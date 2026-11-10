package com.scd.android

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ClearMp4Test {
    private fun u32(value: Int) = byteArrayOf((value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte())
    private fun box(type: String, data: ByteArray = byteArrayOf()) = u32(data.size + 8) + type.toByteArray() + data
    private fun init(encrypted: Boolean): ByteArray {
        val tkhd = box("tkhd", u32(0) + u32(0) + u32(0) + u32(1))
        val stsd = box("stsd", u32(0) + u32(1) + box(if (encrypted) "enca" else "mp4a", ByteArray(28)))
        val trak = box("trak", tkhd + box("mdia", box("minf", box("stbl", stsd))))
        return box("ftyp", "isom".toByteArray()) + box("moov", trak + box("mvex"))
    }
    private fun fragment() = box("moof", box("traf", box("tfhd", u32(0) + u32(1)) +
        box("saiz", ByteArray(12)) + box("saio", ByteArray(16)) + box("senc", ByteArray(24)))) +
        box("mdat", "audio-senc-saiz-saio-must-stay".toByteArray())

    @Test fun clearFragmentsLoseOnlyTheirEncryptionBoxTypes() {
        val input = init(false) + fragment() + fragment()
        val file = File.createTempFile("clear-mp4", ".m4a")
        try {
            file.writeBytes(input)
            ClearMp4.repair(file)
            val actual = file.readBytes()
            assertEquals(input.size, actual.size)
            assertTrue(ClearMp4.needsRepair(input))
            var changedBoxes = 0
            Cenc.iterBoxes(input, 0, input.size) { type, start, end ->
                if (type == "moof") Cenc.iterBoxes(input, start + 8, end) { _, traf, trafEnd ->
                    Cenc.iterBoxes(input, traf + 8, trafEnd) { child, at, _ ->
                        if (child in setOf("saiz", "saio", "senc")) {
                            assertEquals("free", actual.decodeToString(at + 4, at + 8))
                            "free".toByteArray().copyInto(input, at + 4)
                            changedBoxes++
                        }
                    }
                }
            }
            assertEquals(6, changedBoxes)
            assertArrayEquals(input, actual)
            assertFalse(ClearMp4.needsRepair(actual))
            ClearMp4.repair(file)
            assertArrayEquals(actual, file.readBytes())
        } finally { file.delete() }
    }

    @Test fun encryptedOrNonMp4AudioIsNeverRewritten() {
        for (bytes in listOf(init(true) + fragment(), "ID3-senc-saiz".toByteArray())) {
            val file = File.createTempFile("encrypted-mp4", ".m4a")
            try {
                file.writeBytes(bytes)
                assertFalse(ClearMp4.needsRepair(bytes))
                ClearMp4.repair(file)
                assertArrayEquals(bytes, file.readBytes())
            } finally { file.delete() }
        }
    }
}
