package com.scd.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val STATS_PERIODS = listOf("week", "month", "year", "all")

@Composable
fun statsPeriodLabel(p: String): String = stringResource(
    when (p) {
        "week" -> R.string.stats_week
        "month" -> R.string.stats_month
        "year" -> R.string.stats_year
        else -> R.string.stats_all
    },
)

@Composable
fun listenTime(ms: Long): String {
    val (h, m) = CatalogRepo.formatListen(ms)
    return if (h > 0) stringResource(R.string.stats_hours, h, m) else stringResource(R.string.stats_minutes, m)
}

fun statsDelta(now: Long, before: Long?): Int? {
    if (before == null || before <= 0) return null
    return (((now - before) * 100) / before).toInt()
}

private fun epochDay(s: String): Long? {
    val d = s.take(10)
    if (d.length < 10 || d[4] != '-' || d[7] != '-') return null
    val y = d.substring(0, 4).toIntOrNull() ?: return null
    val m = d.substring(5, 7).toIntOrNull() ?: return null
    val day = d.substring(8, 10).toIntOrNull() ?: return null
    val yy = if (m <= 2) y - 1 else y
    val era = (if (yy >= 0) yy else yy - 399) / 400
    val yoe = yy - era * 400
    val doy = (153 * ((m + 9) % 12) + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097L + doe - 719468
}

private fun monthIndex(s: String): Int? {
    val y = s.take(4).toIntOrNull() ?: return null
    val m = s.drop(5).take(2).toIntOrNull() ?: return null
    return y * 12 + m - 1
}

private fun List<TimelinePoint>.merged(): TimelinePoint =
    TimelinePoint(first().date, sumOf { it.plays }, sumOf { it.listenedMs })

fun ListeningStats.filledTimeline(): List<TimelinePoint> {
    if (timeline.isEmpty()) return timeline
    return runCatching {
        if (unit == "month") {
            val known = timeline.groupBy { monthIndex(it.date) }
            val keys = known.keys.filterNotNull()
            if (keys.isEmpty()) return@runCatching timeline
            val start = minOf(monthIndex(from) ?: keys.min(), keys.min())
            val end = maxOf(monthIndex(to) ?: keys.max(), keys.max())
            if (end - start > 240) return@runCatching timeline
            (start..end).map { i -> known[i]?.merged() ?: TimelinePoint("${i / 12}-${(i % 12 + 1).toString().padStart(2, '0')}") }
        } else {
            val step = when (unit) {
                "day" -> 1L
                "week" -> 7L
                else -> return@runCatching timeline
            }
            val known = timeline.groupBy { epochDay(it.date) }
            val keys = known.keys.filterNotNull()
            if (keys.isEmpty()) return@runCatching timeline
            val fromDay = epochDay(from)
            val toDay = epochDay(to)
            var start = keys.min()
            var end = keys.max()
            if (fromDay != null) while (start - step >= fromDay) start -= step
            if (toDay != null) while (end + step <= toDay) end += step
            if ((end - start) / step > 400) return@runCatching timeline
            generateSequence(start) { it + step }.takeWhile { it <= end }.map { d -> known[d]?.merged() ?: TimelinePoint() }.toList()
        }
    }.getOrDefault(timeline)
}

@Composable
fun TimelineChart(points: List<TimelinePoint>, color: Color, track: Color, modifier: Modifier = Modifier) {
    val max = (points.maxOfOrNull { it.listenedMs } ?: 0L).coerceAtLeast(1L)
    Canvas(modifier.fillMaxWidth().height(110.dp)) {
        if (points.isEmpty()) return@Canvas
        val gap = 2.dp.toPx()
        val w = ((size.width - gap * (points.size - 1)) / points.size).coerceAtLeast(1f)
        points.forEachIndexed { i, pt ->
            val x = i * (w + gap)
            drawRoundRect(track, Offset(x, 0f), Size(w, size.height), CornerRadius(w / 2, w / 2))
            val h = (pt.listenedMs.toFloat() / max * size.height).coerceAtLeast(if (pt.listenedMs > 0) 3.dp.toPx() else 0f)
            if (h > 0f) drawRoundRect(color, Offset(x, size.height - h), Size(w, h), CornerRadius(w / 2, w / 2))
        }
    }
}

@Composable
fun RhythmHeatmap(cells: List<RhythmCell>, color: Color, empty: Color, labelColor: Color, modifier: Modifier = Modifier) {
    val grid = Array(7) { LongArray(24) }
    cells.forEach { c -> if (c.weekday in 1..7 && c.hour in 0..23) grid[c.weekday - 1][c.hour] += c.plays else if (c.weekday == 0 && c.hour in 0..23) grid[6][c.hour] += c.plays }
    val max = grid.maxOf { row -> row.maxOrNull() ?: 0L }.coerceAtLeast(1L)
    val days = listOf(R.string.day_mon, R.string.day_tue, R.string.day_wed, R.string.day_thu, R.string.day_fri, R.string.day_sat, R.string.day_sun).map { stringResource(it) }
    Column(modifier) {
        Row {
            Column(Modifier.width(26.dp)) {
                days.forEach { d ->
                    Text(d, style = TextStyle(fontSize = 9.sp, color = labelColor), modifier = Modifier.height(14.dp))
                }
            }
            Canvas(Modifier.weight(1f).height(98.dp)) {
                val gap = 2.dp.toPx()
                val cw = (size.width - gap * 23) / 24
                val ch = (size.height - gap * 6) / 7
                for (d in 0 until 7) {
                    for (h in 0 until 24) {
                        val v = grid[d][h]
                        val c = if (v == 0L) empty else color.copy(alpha = 0.18f + 0.82f * (v.toFloat() / max))
                        drawRoundRect(c, Offset(h * (cw + gap), d * (ch + gap)), Size(cw, ch), CornerRadius(3.dp.toPx()))
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(26.dp))
            listOf("0", "6", "12", "18", "23").forEachIndexed { i, label ->
                Text(label, style = TextStyle(fontSize = 9.sp, color = labelColor))
                if (i < 4) Spacer(Modifier.weight(1f))
            }
        }
    }
}
