package com.scd.android

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class CencTest {
    private val key = "0123456789abcdef".toByteArray(Charsets.US_ASCII)

    private fun u32(v: Int): ByteArray =
        byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())

    private fun boxed(typ: String, body: ByteArray): ByteArray =
        u32(body.size + 8) + typ.toByteArray(Charsets.US_ASCII) + body

    private fun full(typ: String, flags: Int, body: ByteArray): ByteArray =
        boxed(typ, u32(flags) + body)

    private fun segment(
        tfhdFlags: Int,
        tfhdFields: List<Int>,
        trunFlags: Int,
        perSample: List<List<Int>>,
        ivs: List<ByteArray>,
        samples: List<ByteArray>,
    ): ByteArray {
        var tfhd = u32(1)
        for (f in tfhdFields) tfhd += u32(f)
        var trun = u32(perSample.size) + u32(0)
        for (fields in perSample) for (f in fields) trun += u32(f)
        var senc = u32(ivs.size)
        for (iv in ivs) senc += iv
        val traf = full("tfhd", tfhdFlags, tfhd) + full("trun", trunFlags, trun) + full("senc", 0, senc) + full("saiz", 0, u32(0)) + full("saio", 0, u32(0))
        val moof = boxed("moof", boxed("traf", traf))
        return moof + boxed("mdat", samples.reduce { a, b -> a + b })
    }

    private fun iv(b: Int): ByteArray = ByteArray(16) { b.toByte() }

    private fun encrypted(sample: ByteArray, iv: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/CTR/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return c.doFinal(sample)
    }

    private fun mdatPayload(seg: ByteArray): ByteArray {
        var range: Pair<Int, Int>? = null
        Cenc.iterBoxes(seg, 0, seg.size) { typ, bo, be -> if (typ == "mdat") range = bo to be }
        val (ms, me) = range!!
        return seg.copyOfRange(ms + 8, me)
    }

    @Test
    fun perSampleSizesInTrunAreUsedWithTheirOwnIvs() {
        val ivs = listOf(iv(1), iv(2))
        val a = "first sample".toByteArray()
        val b = "second".toByteArray()
        val seg = segment(
            0x38,
            listOf(1024, 99, 0),
            0x201,
            listOf(listOf(a.size), listOf(b.size)),
            ivs,
            listOf(encrypted(a, ivs[0]), encrypted(b, ivs[1])),
        )
        val decrypted = Cenc.decryptSegment(seg, key)
        assertArrayEquals(a + b, mdatPayload(decrypted))
        val traf = Cenc.findBox(decrypted, listOf("moof", "traf"))!!
        val types = mutableListOf<String>()
        Cenc.iterBoxes(decrypted, traf.first + 8, traf.second) { type, _, _ -> types += type }
        org.junit.Assert.assertEquals(listOf("tfhd", "trun", "free", "free", "free"), types)
    }

    @Test
    fun aTailWithoutSizesInTrunTakesTheTfhdDefault() {
        val ivs = listOf(iv(3), iv(4))
        val a = "tail01".toByteArray()
        val b = "tail02".toByteArray()
        val seg = segment(
            0x2_0038,
            listOf(1024, 6, 0x0200_0000),
            0x101,
            listOf(listOf(1024), listOf(1024)),
            ivs,
            listOf(encrypted(a, ivs[0]), encrypted(b, ivs[1])),
        )
        assertArrayEquals(a + b, mdatPayload(Cenc.decryptSegment(seg, key)))
    }

    @Test
    fun aDefaultSizeAfterTheOptionalTfhdFieldsIsFound() {
        val ivs = listOf(iv(5))
        val a = "abcd".toByteArray()
        val seg = segment(
            0x1 or 0x2 or 0x8 or 0x10,
            listOf(0, 0, 7, 1024, 4),
            0x1,
            listOf(emptyList()),
            ivs,
            listOf(encrypted(a, ivs[0])),
        )
        assertArrayEquals(a, mdatPayload(Cenc.decryptSegment(seg, key)))
    }

    @Test
    fun aSegmentWithNoSizeAnywhereIsRefused() {
        val ivs = listOf(iv(6))
        val seg = segment(0x8, listOf(1024), 0x101, listOf(listOf(1024)), ivs, listOf("abcd".toByteArray()))
        val error = runCatching { Cenc.decryptSegment(seg, key) }.exceptionOrNull()
        assertTrue("$error", error is Cenc.MediaException && error.message!!.contains("no sample size"))
    }
}
