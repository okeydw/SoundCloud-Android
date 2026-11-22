package com.scd.android

import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AudioFileTransferTest {
    @get:Rule val storage = TemporaryFolder()

    @Test fun copyPreservesOriginalAndCanResumeBeforeIndexSwitch() {
        val source = storage.newFile("track.m4a").apply { writeBytes(ByteArray(300000) { it.toByte() }) }
        val card = storage.newFolder("card")
        File(card, "track.m4a.move.part").writeText("partial")
        val copy = AudioFileTransfer.copyVerified(source, card)
        assertArrayEquals(source.readBytes(), copy.readBytes())
        assertEquals(copy, AudioFileTransfer.copyVerified(source, card))
        assertTrue(source.exists())
        assertFalse(File(card, "track.m4a.move.part").exists())
    }

    @Test fun refusesConflictingCopyAndKeepsBothFiles() {
        val source = storage.newFile("track.mp3").apply { writeText("old") }
        val target = File(storage.newFolder("card"), source.name).apply { writeText("new") }
        assertThrows(IOException::class.java) { AudioFileTransfer.copyVerified(source, target.parentFile!!) }
        assertEquals("old", source.readText())
        assertEquals("new", target.readText())
    }

    @Test fun deletionWaitsForMoveAndBusyTrackCanBeRetried() = runBlocking {
        val access = TrackFileAccess()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val moving = async {
            access.tryMove("t") { started.complete(Unit); release.await(); true }
        }
        started.await()
        val remove = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { access.use("t") { true } }
        assertFalse(remove.isCompleted)
        release.complete(Unit)
        assertTrue(moving.await())
        assertTrue(remove.await())
        access.use("t") { assertFalse(access.tryMove("t") { true }) }
        assertTrue(access.tryMove("t") { true })
    }
}
