package com.scd.android

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class IntegrityReport(val checked: Int, val removed: Int, val redownload: Int)

object Integrity {
    var running by mutableStateOf(false)
        private set
    var done by mutableIntStateOf(0)
        private set
    var total by mutableIntStateOf(0)
        private set
    var last by mutableStateOf<IntegrityReport?>(null)
        private set

    private fun step() {
        done++
    }

    suspend fun scan(context: Context): IntegrityReport? {
        if (running) return null
        running = true
        last = null
        done = 0
        total = 0
        try {
            val playing = NowPlaying.urn
            val (report, broken, damaged) = withContext(Dispatchers.IO) {
                var checked = 0
                var unknown = 0
                val broken = mutableListOf<Track>()
                val damaged = mutableListOf<Track>()
                val downloads = Downloads.tracks()
                total = downloads.size + ScDataSource.spoolFiles().size + MediaCache.keyCount()
                for (t in downloads) {
                    step()
                    if (t.urn == playing) continue
                    val file = Downloads.fileFor(t.urn).takeIf { it.exists() } ?: continue
                    val info = AudioProbe.inspect(file)
                    checked++
                    if (!info.readable && !Downloads.looksLikeAudio(file)) {
                        broken += t
                        continue
                    }
                    if (!info.playable) {
                        if (damaged.size < 5) Logs.add("cache", "integrity: ${t.title.take(24)} unplayable (${info.problem})")
                        damaged += t
                        continue
                    }
                    val expected = maxOf(t.displayDuration, DurationCache.get(t.urn))
                    if (AudioProbe.tooSmall(file.length(), expected)) {
                        damaged += t
                        continue
                    }
                    val ms = info.ms
                    if (ms == null) {
                        unknown++
                        continue
                    }
                    fun cut(v: Long) = expected > 45_000L && v < expected - 5_000L && (ScDataSource.isSnippetLength(v) || v < expected / 2)
                    if (cut(ms) || info.declaredMs?.let(::cut) == true) {
                        if (damaged.size < 5) Logs.add("cache", "integrity: ${t.title.take(24)} is ${ms / 1000}s (header ${info.declaredMs?.div(1000) ?: "?"}s) of ${expected / 1000}s")
                        damaged += t
                    }
                }
                if (broken.isNotEmpty()) Downloads.removeAll(broken.map { it.urn })
                val (spoolChecked, spoolRemoved) = ScDataSource.scanSpools(playing, ::step)
                val (cacheChecked, cacheRemoved) = MediaCache.scanSnippets(context.cacheDir, playing, ::step)
                Logs.add(
                    "cache",
                    "integrity: downloads $checked checked, ${broken.size} removed, ${damaged.size} to repair, $unknown without length; " +
                        "spooled $spoolChecked/$spoolRemoved, cached $cacheChecked/$cacheRemoved",
                )
                val again = if (Prefs.offline) 0 else broken.size + damaged.size
                Triple(
                    IntegrityReport(
                        checked = checked + spoolChecked + cacheChecked,
                        removed = broken.size + (if (Prefs.offline) 0 else damaged.size) + spoolRemoved + cacheRemoved,
                        redownload = again,
                    ),
                    broken,
                    damaged,
                )
            }
            if (!Prefs.offline) {
                if (broken.isNotEmpty()) Downloads.enqueue(context.applicationContext, broken)
                if (damaged.isNotEmpty()) Downloads.redownload(context, damaged)
            }
            last = report
            return report
        } finally {
            running = false
        }
    }
}
