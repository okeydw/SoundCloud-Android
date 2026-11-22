package com.scd.android

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

class SpoolAdoptionTest {
    @Test fun failedCacheReplacementKeepsThePreviousCopy() {
        val directory = Files.createTempDirectory("spool-adoption-").toFile()
        val previous = ScDataSource.cacheDir
        val urn = "soundcloud:tracks:748733749"
        fun spool(variant: String): File {
            val hash = MessageDigest.getInstance("SHA-1").digest("$urn|$variant".toByteArray()).joinToString("") { "%02x".format(it) }
            return File(directory, "$hash.m4a")
        }
        try {
            ScDataSource.cacheDir = directory
            val sq = spool("sq").apply { writeText("previous sq audio") }
            val hq = spool("hq").apply { writeText("previous hq audio") }
            assertFalse(ScDataSource.adoptSpool(urn, File(directory, "missing.m4a"), Quality.HQ))
            assertEquals("previous sq audio", sq.readText())
            assertEquals("previous hq audio", hq.readText())
        } finally {
            ScDataSource.cacheDir = previous
            directory.listFiles()?.forEach { it.delete() }
            directory.delete()
        }
    }
}
