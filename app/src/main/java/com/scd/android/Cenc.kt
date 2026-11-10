package com.scd.android

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object Cenc {
    class MediaException(message: String) : Exception(message)

    private fun u32(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xFF) shl 24) or
            ((b[o + 1].toInt() and 0xFF) shl 16) or
            ((b[o + 2].toInt() and 0xFF) shl 8) or
            (b[o + 3].toInt() and 0xFF)

    fun decryptSegment(seg: ByteArray, key: ByteArray): ByteArray {
        if (key.size != 16) throw MediaException("key length ${key.size}")
        val traf = findBox(seg, listOf("moof", "traf")) ?: throw MediaException("no traf")
        val ts = traf.first
        val te = traf.second

        var trun: Int? = null
        iterBoxes(seg, ts + 8, te) { typ, bo, _ -> if (typ == "trun") trun = bo }
        val rs = trun ?: throw MediaException("no trun")
        val tflags = (u32(seg, rs + 8) and 0xFFFFFF)
        val scount = u32(seg, rs + 12)
        if (scount < 0) throw MediaException("bad sample count")
        var p = rs + 16
        if (tflags and 0x1 != 0) p += 4
        if (tflags and 0x4 != 0) p += 4
        val per = (if (tflags and 0x100 != 0) 4 else 0) +
            (if (tflags and 0x200 != 0) 4 else 0) +
            (if (tflags and 0x400 != 0) 4 else 0) +
            (if (tflags and 0x800 != 0) 4 else 0)
        val sizeOff = if (tflags and 0x100 != 0) 4 else 0
        val sizes = IntArray(scount)
        if (tflags and 0x200 != 0) {
            for (i in 0 until scount) sizes[i] = u32(seg, p + i * per + sizeOff)
        } else {
            val size = defaultSampleSize(seg, ts, te) ?: throw MediaException("no sample size in trun or tfhd")
            for (i in 0 until scount) sizes[i] = size
        }

        var sencBox: Int? = null
        iterBoxes(seg, ts + 8, te) { typ, bo, _ -> if (typ == "senc") sencBox = bo }
        val cs = sencBox ?: throw MediaException("no senc")
        val sflags = (u32(seg, cs + 8) and 0xFFFFFF)
        if (sflags and 2 != 0) throw MediaException("subsample senc не ожидался для этого аудио")
        val count = u32(seg, cs + 12)
        if (count != scount) throw MediaException("senc/trun sample count mismatch")
        val ivs = Array(count) { ByteArray(16) }
        var q = cs + 16
        for (i in 0 until count) {
            System.arraycopy(seg, q, ivs[i], 0, 16)
            q += 16
        }

        var mdat: Pair<Int, Int>? = null
        iterBoxes(seg, 0, seg.size) { typ, bo, be -> if (typ == "mdat") mdat = bo to be }
        val m = mdat ?: throw MediaException("no mdat")
        val dataStart = m.first + 8
        val dataLen = m.second - dataStart
        val out = seg.copyOf()

        var off = 0
        for (i in 0 until count) {
            val n = sizes[i]
            if (off + n > dataLen) throw MediaException("sample out of mdat")
            ctrInPlace(out, dataStart + off, n, key, ivs[i])
            off += n
        }
        iterBoxes(out, ts + 8, te) { type, offset, _ ->
            if (type == "senc" || type == "saiz" || type == "saio") {
                "free".toByteArray(Charsets.US_ASCII).copyInto(out, offset + 4)
            }
        }
        return out
    }

    private fun ctrInPlace(buf: ByteArray, start: Int, len: Int, key: ByteArray, iv: ByteArray) {
        if (len == 0) return
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        val plain = cipher.doFinal(buf, start, len)
        if (plain.size != len) throw MediaException("ctr length ${plain.size} != $len")
        System.arraycopy(plain, 0, buf, start, len)
    }

    private fun defaultSampleSize(seg: ByteArray, ts: Int, te: Int): Int? {
        var tfhd: Pair<Int, Int>? = null
        iterBoxes(seg, ts + 8, te) { typ, bo, be -> if (typ == "tfhd") tfhd = bo to be }
        val box = tfhd ?: return null
        val hs = box.first
        val he = box.second
        val flags = (u32(seg, hs + 8) and 0xFFFFFF)
        if (flags and 0x10 == 0) return null
        var p = hs + 16
        if (flags and 0x1 != 0) p += 8
        if (flags and 0x2 != 0) p += 4
        if (flags and 0x8 != 0) p += 4
        if (p + 4 > he || p + 4 > seg.size) return null
        return u32(seg, p)
    }

    fun iterBoxes(b: ByteArray, start: Int, end: Int, f: (String, Int, Int) -> Unit) {
        if (start < 0 || end > b.size || start > end) return
        var o = start
        while (o <= end - 8) {
            val sz = u32(b, o)
            val typ = runCatching { String(b, o + 4, 4, Charsets.US_ASCII) }.getOrDefault("")
            if (sz != 0 && (sz < 8 || sz > end - o)) break
            val boxEnd = if (sz == 0) end else o + sz
            f(typ, o, boxEnd)
            o = boxEnd
        }
    }

    fun findBox(b: ByteArray, path: List<String>): Pair<Int, Int>? {
        var regionStart = 0
        var regionEnd = b.size
        for ((i, want) in path.withIndex()) {
            var hit: Pair<Int, Int>? = null
            iterBoxes(b, regionStart, regionEnd) { typ, bo, be -> if (hit == null && typ == want) hit = bo to be }
            val (bs, be) = hit ?: return null
            if (i == path.size - 1) return bs to be
            regionStart = bs + containerHeader(b, bs, want)
            regionEnd = be
        }
        return null
    }

    private fun containerHeader(b: ByteArray, bs: Int, typ: String): Int = when (typ) {
        "stsd" -> 16
        "enca", "mp4a" -> sampleEntryHdr(b, bs)
        else -> 8
    }

    fun sampleEntryHdr(b: ByteArray, bs: Int): Int {
        val ver = ((b[bs + 16].toInt() and 0xFF) shl 8) or (b[bs + 17].toInt() and 0xFF)
        return 8 + 8 + when (ver) {
            1 -> 36
            2 -> 56
            else -> 20
        }
    }
}
