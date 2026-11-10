package com.scd.android

import java.io.File
import java.io.RandomAccessFile

internal object ClearMp4 {
    private const val MAX_METADATA = 2 * 1024 * 1024

    fun needsRepair(prefix: ByteArray): Boolean {
        val moov = Cenc.findBox(prefix, listOf("moov")) ?: return false
        val box = prefix.copyOfRange(moov.first, moov.second)
        val moof = Cenc.findBox(prefix, listOf("moof")) ?: return false
        return encryptionOffsets(prefix.copyOfRange(moof.first, moof.second), clearTracks(box)).isNotEmpty()
    }

    private fun encryptionOffsets(box: ByteArray, tracks: Set<Int>): List<Int> = buildList {
        if (tracks.isEmpty()) return@buildList
        Cenc.iterBoxes(box, 8, box.size) { child, start, end ->
            if (child == "traf") {
                var trackId: Int? = null
                Cenc.iterBoxes(box, start + 8, end) { field, at, _ ->
                    if (field == "tfhd") trackId = id(box, at + 12)
                }
                if (trackId in tracks) Cenc.iterBoxes(box, start + 8, end) { field, at, _ ->
                    if (field == "senc" || field == "saiz" || field == "saio") add(at + 4)
                }
            }
        }
    }

    private fun id(bytes: ByteArray, offset: Int): Int? {
        if (offset < 0 || offset > bytes.size - 4) return null
        return (0..3).fold(0) { value, i -> (value shl 8) or (bytes[offset + i].toInt() and 255) }
    }

    private fun clearTracks(moov: ByteArray): Set<Int> = buildSet {
        Cenc.iterBoxes(moov, 8, moov.size) { type, start, end ->
            if (type == "trak") {
                val track = moov.copyOfRange(start, end)
                val header = Cenc.findBox(track, listOf("trak", "tkhd"))
                val entries = Cenc.findBox(track, listOf("trak", "mdia", "minf", "stbl", "stsd"))
                if (header != null && entries != null && header.first + 9 <= header.second) {
                    var clear = false
                    var encrypted = false
                    Cenc.iterBoxes(track, entries.first + 16, entries.second) { entry, _, _ ->
                        if (entry == "mp4a") clear = true
                        if (entry == "enca" || entry == "encv") encrypted = true
                    }
                    val offset = header.first + if (track[header.first + 8].toInt() == 1) 28 else 20
                    if (clear && !encrypted) id(track, offset)?.let { add(it) }
                }
            }
        }
    }

    fun repair(file: File) {
        RandomAccessFile(file, "rw").use { data ->
            if (data.length() < 8) return
            data.seek(4)
            if (data.readInt() != 0x66747970) return
            var tracks = emptySet<Int>()
            var offset = 0L
            while (offset <= data.length() - 8) {
                data.seek(offset)
                var size = data.readInt().toLong() and 0xffffffffL
                val type = data.readInt()
                var headerSize = 8
                if (size == 1L) {
                    if (offset > data.length() - 16) return
                    size = data.readLong()
                    headerSize = 16
                } else if (size == 0L) size = data.length() - offset
                if (size < headerSize || size > data.length() - offset) return
                if (headerSize == 8 && size <= MAX_METADATA && (type == 0x6d6f6f76 || type == 0x6d6f6f66)) {
                    val box = ByteArray(size.toInt())
                    data.seek(offset)
                    data.readFully(box)
                    if (type == 0x6d6f6f76) tracks = clearTracks(box)
                    else for (at in encryptionOffsets(box, tracks)) {
                        data.seek(offset + at)
                        data.writeInt(0x66726565)
                    }
                }
                offset += size
            }
        }
    }
}
