package com.scd.android

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.InterruptedIOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class SegmentDownloadTest {
    @Test fun sixSegmentsStartTogetherButOutputKeepsPlaylistOrder() {
        val started = CountDownLatch(6)
        val release = CountDownLatch(1)
        val calls = CopyOnWriteArrayList<Call>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            calls += chain.call()
            started.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            val index = chain.request().url.encodedPath.substringAfterLast('/').toInt()
            Thread.sleep((6 - index.coerceAtMost(6)) * 10L)
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(byteArrayOf(index.toByte()).toResponseBody()).build()
        }.build()
        val output = ByteArrayOutputStream()
        val error = AtomicReference<Throwable?>()
        val worker = thread {
            try { SegmentDownload.write(client, (0..8).map { "https://segments.test/$it" }, output) }
            catch (e: Throwable) { error.set(e) }
        }
        try {
            assertTrue("Only three requests used to start before waiting", started.await(5, TimeUnit.SECONDS))
            assertEquals(6, calls.size)
            assertEquals(0, output.size())
        } finally { release.countDown(); worker.join(5_000) }
        assertFalse(worker.isAlive)
        assertNull(error.get())
        assertArrayEquals((0..8).map { it.toByte() }.toByteArray(), output.toByteArray())
    }

    @Test fun cancellationStopsTheAwaitedCallAndAllPrefetchedCalls() {
        val started = CountDownLatch(6)
        val release = CountDownLatch(1)
        val calls = CopyOnWriteArrayList<Call>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            calls += chain.call()
            started.countDown()
            release.await(5, TimeUnit.SECONDS)
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(byteArrayOf(1).toResponseBody()).build()
        }.build()
        val error = AtomicReference<Throwable?>()
        val worker = thread {
            try { SegmentDownload.write(client, (0..8).map { "https://segments.test/$it" }, ByteArrayOutputStream()) }
            catch (e: Throwable) { error.set(e) }
        }
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            worker.interrupt()
            worker.join(3_000)
            assertFalse(worker.isAlive)
            assertTrue(error.get() is InterruptedIOException)
            assertEquals(6, calls.size)
            assertTrue(calls.all { it.isCanceled() })
        } finally { release.countDown(); worker.interrupt(); worker.join(3_000) }
    }
}
