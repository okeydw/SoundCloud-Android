package com.scd.android

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class IntegrityReport(val checked: Int, val removed: Int, val redownload: Int, val repaired: Int = 0, val remaining: Int = 0)

object Integrity {
    var running by mutableStateOf(false)
        private set
    var done by mutableIntStateOf(0)
        private set
    var total by mutableIntStateOf(0)
        private set
    var last by mutableStateOf<IntegrityReport?>(null)
        private set

    private var automaticStarted = false

    fun startAutomatic(context: Context) {
        if (automaticStarted) return
        automaticStarted = true
        val migration = context.getSharedPreferences("audio_integrity", Context.MODE_PRIVATE)
        if (migration.getInt("repair_revision", 0) >= 1) return
        App.scope.launch(Dispatchers.Main) {
            delay(10_000)
            runCatching { scan(context.applicationContext, automatic = true) }
                .onSuccess { report ->
                    if (report != null && report.remaining == 0) migration.edit().putInt("repair_revision", 1).apply()
                }
                .onFailure { Logs.add("cache", "automatic repair: ${it::class.simpleName}") }
        }
    }

    private fun step() {
        done++
    }

    suspend fun scan(context: Context, automatic: Boolean = false): IntegrityReport? {
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
                var localRepairs = 0
                val broken = mutableListOf<Track>()
                val damaged = mutableListOf<Track>()
                val downloads = Downloads.tracks()
                total = downloads.size + ScDataSource.spoolFiles().size + MediaCache.keyCount()
                for (t in downloads) {
                    step()
                    if (!isScUrn(t.urn)) continue
                    if (t.urn == playing || t.urn == NowPlaying.urn) {
                        unknown++
                        continue
                    }
                    Downloads.fileAccess.use(t.urn) {
                        DurationCache.record(t.urn, t.displayDuration)
                        val file = Downloads.fileFor(t.urn).takeIf { it.exists() } ?: return@use
                        if (AudioNormalize.repair(file, t.displayDuration)) localRepairs++
                        val info = AudioProbe.inspect(file)
                        checked++
                        if (!info.readable && !Downloads.looksLikeAudio(file)) {
                            broken += t
                            return@use
                        }
                        if (!info.playable) {
                            if (damaged.size < 5) Logs.add("cache", "integrity: ${t.title.take(24)} unplayable (${info.problem})")
                            damaged += t
                            return@use
                        }
                        val expected = maxOf(t.displayDuration, DurationCache.get(t.urn))
                        if (AudioProbe.tooSmall(file.length(), expected)) {
                            damaged += t
                            return@use
                        }
                        val ms = info.ms
                        if (ms == null) {
                            unknown++
                            return@use
                        }
                        if (AudioProbe.shortReason(info, file.length(), expected) != null) {
                            if (damaged.size < 5) Logs.add("cache", "integrity: ${t.title.take(24)} is ${ms / 1000}s (header ${info.declaredMs?.div(1000) ?: "?"}s) of ${expected / 1000}s")
                            damaged += t
                        } else if (AudioNormalize.fragmented(file)) {
                            unknown++
                            Logs.add("cache", "integrity: ${t.title.take(24)} container repair still pending")
                        }
                    }
                }
                val (spoolChecked, spoolRemoved) = ScDataSource.scanSpools(playing, ::step)
                val (cacheChecked, cacheRemoved) = MediaCache.scanSnippets(context.cacheDir, playing, ::step)
                Logs.add(
                    "cache",
                    "integrity: downloads $checked checked, ${broken.size + damaged.size} to repair, $localRepairs repacked, $unknown without length; " +
                        "spooled $spoolChecked/$spoolRemoved, cached $cacheChecked/$cacheRemoved",
                )
                val again = if (Prefs.offline) 0 else broken.size + damaged.size
                Triple(
                    IntegrityReport(
                        checked = checked + spoolChecked + cacheChecked,
                        removed = spoolRemoved + cacheRemoved,
                        redownload = again,
                        repaired = localRepairs,
                        remaining = broken.size + damaged.size + unknown,
                    ),
                    broken,
                    damaged,
                )
            }
            var result = report
            if (!Prefs.offline && (!automatic || !Prefs.prefetchWifiOnly || !NetMonitor.isMetered(context))) {
                for (track in (broken + damaged).distinctBy { it.urn }) {
                    if (Prefs.offline) break
                    if (track.urn == NowPlaying.urn) continue
                    if (Downloads.repair(track)) result = result.copy(repaired = result.repaired + 1, remaining = result.remaining - 1)
                }
            }
            last = result
            return result
        } finally {
            running = false
        }
    }
}
