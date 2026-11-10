package com.scd.android

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import java.util.concurrent.atomic.AtomicInteger

object TrackTurns {
    enum class Priority { PLAYBACK, BACKGROUND, BULK }

    private val hq = Semaphore(1)
    private val waiting = AtomicInteger(0)

    suspend fun <T> withTurn(priority: Priority, block: suspend () -> T): T {
        if (priority == Priority.PLAYBACK || Prefs.star) return block()
        if (priority == Priority.BACKGROUND) {
            waiting.incrementAndGet()
            try {
                hq.acquire()
            } finally {
                waiting.decrementAndGet()
            }
        } else {
            while (true) {
                while (waiting.get() > 0) delay(250L)
                hq.acquire()
                if (waiting.get() == 0) break
                hq.release()
                delay(50L)
            }
        }
        try {
            return block()
        } finally {
            hq.release()
        }
    }
}
