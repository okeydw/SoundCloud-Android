package com.scd.android

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest

internal object AudioFileTransfer {
    private fun digest(file: File): ByteArray {
        val hash = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(128 * 1024)
        file.inputStream().use { input ->
            while (true) {
                val size = input.read(buffer)
                if (size < 0) break
                hash.update(buffer, 0, size)
            }
        }
        return hash.digest()
    }

    fun identical(a: File, b: File): Boolean =
        a.isFile && b.isFile && a.length() > 0L && a.length() == b.length() && digest(a).contentEquals(digest(b))

    fun copyVerified(source: File, directory: File): File {
        val target = File(directory, source.name)
        if (!source.isFile || source.length() == 0L) throw IOException("Missing source")
        if (target.exists()) {
            if (identical(source, target)) return target
            throw IOException("Destination already contains different audio")
        }
        val staged = File(directory, source.name + ".move.part")
        if (staged.exists() && !staged.delete()) throw IOException("Cannot remove partial transfer")
        if (directory.usableSpace < source.length()) throw IOException("Insufficient storage")
        try {
            staged.outputStream().use { output ->
                source.inputStream().use { it.copyTo(output, 128 * 1024) }
                output.fd.sync()
            }
            if (!identical(source, staged)) throw IOException("Copy verification failed")
            Files.move(staged.toPath(), target.toPath())
            return target
        } finally { staged.delete() }
    }
}
