package com.scd.android

import kotlinx.coroutines.CompletableDeferred

internal class TrackFileAccess {
    private val readers = mutableMapOf<String, Int>()
    private val moves = mutableMapOf<String, CompletableDeferred<Unit>>()

    suspend fun <T> use(urn: String, action: suspend () -> T): T {
        while (true) {
            val pending = synchronized(this) {
                moves[urn].also { if (it == null) readers[urn] = (readers[urn] ?: 0) + 1 }
            } ?: break
            pending.await()
        }
        try { return action() }
        finally {
            synchronized(this) {
                val left = readers.getValue(urn) - 1
                if (left == 0) readers.remove(urn) else readers[urn] = left
            }
        }
    }

    suspend fun tryMove(urn: String, action: suspend () -> Boolean): Boolean {
        val done = synchronized(this) {
            if (urn in readers || urn in moves) return false
            CompletableDeferred<Unit>().also { moves[urn] = it }
        }
        try { return action() }
        finally {
            synchronized(this) { moves.remove(urn) }
            done.complete(Unit)
        }
    }
}
