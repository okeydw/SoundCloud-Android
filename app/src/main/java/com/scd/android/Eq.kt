package com.scd.android

import kotlin.concurrent.Volatile
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class Biquad(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double) {
    companion object {
        val FLAT = Biquad(1.0, 0.0, 0.0, 0.0, 0.0)
    }
}

enum class EqShape { PEAK, LOW_SHELF, HIGH_SHELF }

object EqCurve {
    const val BANDS = 10
    val FREQS = doubleArrayOf(32.0, 64.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)
    val LABELS = listOf("32", "64", "125", "250", "500", "1K", "2K", "4K", "8K", "16K")
    const val Q = 1.414
    const val Q_BUTTERWORTH = 0.7071067811865476
    const val MAX_GAIN = 12.0
    const val PREAMP_MIN = -24.0
    const val PREAMP_MAX = 6.0
    const val CURVE_RATE = 48_000.0
    private const val GRID_POINTS = 121
    private const val FIT_ROUNDS = 12
    private const val FIT_STEP_DB = 0.5
    private const val FIT_SMOOTHING = 0.02

    val FLAT: List<Double> = List(BANDS) { 0.0 }

    fun design(shape: EqShape, gainDb: Double, frequency: Double, q: Double, sampleRate: Double): Biquad? {
        val f0 = 2.0 * frequency / sampleRate
        if (f0.isNaN() || f0 >= 1.0 || f0 < 0.0) return null
        if (!(q > 0.0)) return null
        val omega = PI * f0
        val s = sin(omega)
        val c = cos(omega)
        val alpha = s / (2.0 * q)
        val a = 10.0.pow(gainDb / 40.0)
        return when (shape) {
            EqShape.PEAK -> norm(
                1.0 + alpha * a, -2.0 * c, 1.0 - alpha * a,
                1.0 + alpha / a, -2.0 * c, 1.0 - alpha / a,
            )
            EqShape.LOW_SHELF -> {
                val sa = 2.0 * alpha * sqrt(a)
                norm(
                    a * ((a + 1.0) - (a - 1.0) * c + sa),
                    2.0 * a * ((a - 1.0) - (a + 1.0) * c),
                    a * ((a + 1.0) - (a - 1.0) * c - sa),
                    (a + 1.0) + (a - 1.0) * c + sa,
                    -2.0 * ((a - 1.0) + (a + 1.0) * c),
                    (a + 1.0) + (a - 1.0) * c - sa,
                )
            }
            EqShape.HIGH_SHELF -> {
                val sa = 2.0 * alpha * sqrt(a)
                norm(
                    a * ((a + 1.0) + (a - 1.0) * c + sa),
                    -2.0 * a * ((a - 1.0) + (a + 1.0) * c),
                    a * ((a + 1.0) + (a - 1.0) * c - sa),
                    (a + 1.0) - (a - 1.0) * c + sa,
                    2.0 * ((a - 1.0) - (a + 1.0) * c),
                    (a + 1.0) - (a - 1.0) * c - sa,
                )
            }
        }
    }

    private fun norm(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double): Biquad {
        val div = 1.0 / a0
        return Biquad(b0 * div, b1 * div, b2 * div, a1 * div, a2 * div)
    }

    fun bandCoefficients(band: Int, gain: Double, sampleRate: Double): Biquad {
        val (shape, q) = when (band) {
            0 -> EqShape.LOW_SHELF to Q_BUTTERWORTH
            BANDS - 1 -> EqShape.HIGH_SHELF to Q_BUTTERWORTH
            else -> EqShape.PEAK to Q
        }
        return design(shape, gain, FREQS[band], q, sampleRate) ?: Biquad.FLAT
    }

    fun magnitudeDb(filter: Biquad, frequency: Double): Double {
        val w = 2.0 * PI * frequency / CURVE_RATE
        val sin1 = sin(w)
        val cos1 = cos(w)
        val sin2 = sin(2.0 * w)
        val cos2 = cos(2.0 * w)
        val numRe = filter.b0 + filter.b1 * cos1 + filter.b2 * cos2
        val numIm = filter.b1 * sin1 + filter.b2 * sin2
        val denRe = 1.0 + filter.a1 * cos1 + filter.a2 * cos2
        val denIm = filter.a1 * sin1 + filter.a2 * sin2
        return 10.0 * log10((numRe * numRe + numIm * numIm) / (denRe * denRe + denIm * denIm))
    }

    val grid: DoubleArray by lazy {
        DoubleArray(GRID_POINTS) { i -> 20.0 * 1000.0.pow(i.toDouble() / (GRID_POINTS - 1)) }
    }

    fun bandGains(gains: List<Double>): DoubleArray = DoubleArray(BANDS) { band ->
        val g = gains.getOrNull(band)?.takeIf { it.isFinite() } ?: 0.0
        g.coerceIn(-MAX_GAIN, MAX_GAIN)
    }

    fun responseDb(gains: DoubleArray, frequency: Double): Double {
        var sum = 0.0
        for (band in 0 until BANDS) sum += magnitudeDb(bandCoefficients(band, gains[band], CURVE_RATE), frequency)
        return sum
    }

    fun autoPreamp(gains: DoubleArray): Double {
        var peak = 0.0
        for (f in grid) peak = max(peak, responseDb(gains, f))
        return roundTenth(-peak)
    }

    fun resolvePreamp(gains: DoubleArray, preamp: Double?): Double =
        (preamp?.takeIf { it.isFinite() } ?: autoPreamp(gains)).coerceIn(PREAMP_MIN, PREAMP_MAX)

    fun roundTenth(value: Double): Double {
        val rounded = roundAway(value * 10.0) / 10.0
        return if (rounded == 0.0) 0.0 else rounded
    }

    internal fun roundAway(x: Double): Double = if (x >= 0.0) floor(x + 0.5) else -floor(-x + 0.5)

    class Fit(val gains: DoubleArray, val deviation: Double)

    fun fit(target: DoubleArray): Fit {
        val g = grid
        val gains = DoubleArray(BANDS)
        repeat(FIT_ROUNDS) {
            val curves = bandCurves(g, gains, 0.0)
            val raised = bandCurves(g, gains, FIT_STEP_DB)
            val slopes = Array(BANDS) { band ->
                DoubleArray(g.size) { p -> (raised[band][p] - curves[band][p]) / FIT_STEP_DB }
            }
            val residual = DoubleArray(g.size) { p ->
                var sum = 0.0
                for (band in 0 until BANDS) sum += curves[band][p]
                target[p] - sum
            }
            val matrix = Array(BANDS) { DoubleArray(BANDS) }
            val vector = DoubleArray(BANDS)
            for (row in 0 until BANDS) {
                for (column in 0 until BANDS) matrix[row][column] = dot(slopes[row], slopes[column])
                matrix[row][row] += FIT_SMOOTHING
                vector[row] = dot(slopes[row], residual) - FIT_SMOOTHING * gains[row]
            }
            val step = solve(matrix, vector)
            for (band in 0 until BANDS) gains[band] = (gains[band] + step[band]).coerceIn(-MAX_GAIN, MAX_GAIN)
        }
        val rounded = DoubleArray(BANDS) { roundTenth(gains[it]) }
        var deviation = 0.0
        for (p in g.indices) deviation = max(deviation, abs(target[p] - responseDb(rounded, g[p])))
        return Fit(rounded, roundTenth(deviation))
    }

    private fun bandCurves(g: DoubleArray, gains: DoubleArray, extra: Double): Array<DoubleArray> =
        Array(BANDS) { band ->
            val filter = bandCoefficients(band, gains[band] + extra, CURVE_RATE)
            DoubleArray(g.size) { p -> magnitudeDb(filter, g[p]) }
        }

    private fun dot(a: DoubleArray, b: DoubleArray): Double {
        var sum = 0.0
        for (i in a.indices) sum += a[i] * b[i]
        return sum
    }

    private fun solve(matrix: Array<DoubleArray>, vector: DoubleArray): DoubleArray {
        val n = BANDS
        for (column in 0 until n) {
            var pivot = column
            for (r in column until n) if (abs(matrix[r][column]) > abs(matrix[pivot][column])) pivot = r
            if (pivot != column) {
                val tmp = matrix[column]; matrix[column] = matrix[pivot]; matrix[pivot] = tmp
                val v = vector[column]; vector[column] = vector[pivot]; vector[pivot] = v
            }
            val lead = matrix[column][column]
            if (abs(lead) < 1e-12) continue
            for (row in column + 1 until n) {
                val factor = matrix[row][column] / lead
                for (k in column until n) matrix[row][k] -= factor * matrix[column][k]
                vector[row] -= factor * vector[column]
            }
        }
        val result = DoubleArray(n)
        for (row in n - 1 downTo 0) {
            var known = 0.0
            for (k in row + 1 until n) known += matrix[row][k] * result[k]
            val lead = matrix[row][row]
            result[row] = if (abs(lead) < 1e-12) 0.0 else (vector[row] - known) / lead
        }
        return result
    }

    fun fixed(value: Double, digits: Int): String {
        var scale = 1L
        repeat(digits) { scale *= 10 }
        val r = roundAway(value * scale)
        val neg = r < 0.0
        val whole = abs(r).toLong()
        val text = (whole / scale).toString() + if (digits > 0) "." + (whole % scale).toString().padStart(digits, '0') else ""
        return if (neg && whole != 0L) "-$text" else text
    }

    fun signed(value: Double): String {
        val r = roundTenth(value)
        return if (r > 0.0) "+" + fixed(r, 1) else fixed(r, 1)
    }

    fun interpolate(points: List<Pair<Double, Double>>, frequency: Double): Double {
        val after = points.count { it.first < frequency }
        if (after == 0) return points[0].second
        if (after == points.size) return points[points.size - 1].second
        val (lowF, lowG) = points[after - 1]
        val (highF, highG) = points[after]
        val position = ln(frequency / lowF) / ln(highF / lowF)
        return lowG + (highG - lowG) * position
    }
}

class EqImport(
    val gains: List<Double>,
    val preamp: Double?,
    val used: Int,
    val skipped: List<String>,
    val deviation: Double,
)

enum class EqFormat { APO, GRAPHIC }

object EqTransfer {
    private const val GRAPHIC_STEP = 1.0563
    private const val SKIPPED_CHARS = 60

    private class Preset {
        val filters = mutableListOf<Biquad>()
        val graphics = mutableListOf<List<Pair<Double, Double>>>()
        var preamp: Double? = null
        val skipped = mutableListOf<String>()
    }

    private sealed interface Line {
        class Filter(val coefficients: Biquad) : Line
        data object Off : Line
        data object Unsupported : Line
    }

    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            val count = (bytes.size - 2) / 2
            val chars = CharArray(count) { i ->
                val lo = bytes[2 + i * 2].toInt() and 0xFF
                val hi = bytes[3 + i * 2].toInt() and 0xFF
                ((hi shl 8) or lo).toChar()
            }
            return chars.concatToString()
        }
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return bytes.copyOfRange(3, bytes.size).decodeToString()
        }
        return bytes.decodeToString()
    }

    fun import(text: String): EqImport? {
        val isPeace = text.lines().any { it.trim().equals("[frequencies]", ignoreCase = true) }
        val preset = if (isPeace) readPeace(text) else readApo(text)
        preset.graphics.removeAll { it.isEmpty() }
        if (preset.filters.isEmpty() && preset.graphics.isEmpty()) return null
        val target = DoubleArray(EqCurve.grid.size) { p ->
            val f = EqCurve.grid[p]
            var sum = 0.0
            for (filter in preset.filters) sum += EqCurve.magnitudeDb(filter, f)
            for (points in preset.graphics) sum += EqCurve.interpolate(points, f)
            sum
        }
        val level = if (preset.graphics.isEmpty()) 0.0 else EqCurve.roundTenth(median(target))
        val shape = DoubleArray(target.size) { target[it] - level }
        val fit = EqCurve.fit(shape)
        val rawPreamp = preset.preamp?.let { it + level } ?: level.takeIf { it < 0.0 }
        val preamp = rawPreamp?.let { EqCurve.roundTenth(it.coerceIn(EqCurve.PREAMP_MIN, EqCurve.PREAMP_MAX)) }
        return EqImport(
            gains = fit.gains.toList(),
            preamp = preamp,
            used = preset.filters.size + preset.graphics.size,
            skipped = preset.skipped.toList(),
            deviation = fit.deviation,
        )
    }

    private fun skip(line: String) = line.take(SKIPPED_CHARS)

    private fun readApo(text: String): Preset {
        val preset = Preset()
        var firstChannel = true
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.startsWith('#')) continue
            val colon = line.indexOf(':')
            if (colon < 0) continue
            val key = line.substring(0, colon).trim().lowercase()
            val value = line.substring(colon + 1)
            val isEq = key == "preamp" || key == "graphiceq" || key.startsWith("filter")
            if (key == "channel") {
                firstChannel = value.words().any { it.lowercase() in setOf("all", "l", "1") }
                continue
            }
            if (!isEq) continue
            if (!firstChannel) {
                if (key != "preamp") preset.skipped += skip(line)
                continue
            }
            when {
                key == "preamp" -> {
                    val gain = value.words().firstOrNull()?.let(::number)
                    if (gain != null) preset.preamp = (preset.preamp ?: 0.0) + gain else preset.skipped += skip(line)
                }
                key == "graphiceq" -> preset.graphics += graphicPoints(value)
                else -> when (val parsed = filter(value)) {
                    is Line.Filter -> preset.filters += parsed.coefficients
                    Line.Off -> Unit
                    Line.Unsupported -> preset.skipped += skip(line)
                }
            }
        }
        return preset
    }

    private class Entry(val section: String, val key: String, val value: String, val line: String)

    private fun readPeace(text: String): Preset {
        val entries = mutableListOf<Entry>()
        var section = ""
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.startsWith('[') && line.endsWith(']') && line.length >= 2) {
                section = line.substring(1, line.length - 1).trim().lowercase()
                continue
            }
            val eq = line.indexOf('=')
            if (eq >= 0) entries += Entry(section, line.substring(0, eq).trim().lowercase(), line.substring(eq + 1).trim(), line)
        }
        fun get(section: String, key: String): Entry? = entries.firstOrNull { it.section == section && it.key == key }

        val preset = Preset()
        get("general", "preamp")?.let { e ->
            val gain = number(e.value)
            if (gain != null) preset.preamp = gain else preset.skipped += e.line
        }
        val graphic = get("general", "graphiceq")?.value == "1"
        val points = mutableListOf<Pair<Double, Double>>()
        var band = 1
        while (true) {
            val frequency = get("frequencies", "frequency$band")?.let { number(it.value) }?.takeIf { it > 0.0 } ?: break
            val gainEntry = get("gains", "gain$band")
            val gain = if (gainEntry == null) {
                0.0
            } else {
                number(gainEntry.value) ?: run {
                    preset.skipped += gainEntry.line
                    band++
                    null
                }
            }
            if (gain == null) continue
            if (graphic) {
                points += frequency to gain
                band++
                continue
            }
            val kind = get("filters", "filter$band")
            val shape = when (kind?.value ?: "0") {
                "0" -> EqShape.PEAK
                "4" -> EqShape.LOW_SHELF
                "5" -> EqShape.HIGH_SHELF
                else -> null
            }
            if (shape == null) {
                kind?.let { preset.skipped += it.line }
                band++
                continue
            }
            if (gain == 0.0) {
                band++
                continue
            }
            val q = get("qualities", "quality$band")?.let { number(it.value) }?.takeIf { it > 0.0 } ?: defaultQ(shape)
            EqCurve.design(shape, gain, frequency, q, EqCurve.CURVE_RATE)?.let { preset.filters += it }
            band++
        }
        if (graphic) preset.graphics += points.toList()
        for (e in entries) {
            val perSpeaker = (e.section == "general" && e.key.startsWith("preamp") && e.key != "preamp") ||
                (e.section.startsWith("gains") && e.section != "gains") ||
                (e.section.startsWith("filters") && e.section != "filters")
            if (perSpeaker && number(e.value) != 0.0) preset.skipped += e.line
        }
        return preset
    }

    fun export(gains: DoubleArray, preamp: Double, format: EqFormat): String = when (format) {
        EqFormat.APO -> buildString {
            append("Preamp: ").append(EqCurve.fixed(preamp, 1)).append(" dB\n")
            for (band in 0 until EqCurve.BANDS) {
                val (kind, q) = when (band) {
                    0 -> "LSC" to EqCurve.Q_BUTTERWORTH
                    EqCurve.BANDS - 1 -> "HSC" to EqCurve.Q_BUTTERWORTH
                    else -> "PK" to EqCurve.Q
                }
                append("Filter ").append(band + 1).append(": ON ").append(kind)
                    .append(" Fc ").append(EqCurve.FREQS[band].toLong())
                    .append(" Hz Gain ").append(EqCurve.fixed(gains[band], 1))
                    .append(" dB Q ").append(EqCurve.fixed(q, 2)).append('\n')
            }
        }
        EqFormat.GRAPHIC -> {
            val points = graphicFrequencies().joinToString("; ") { f ->
                f.toLong().toString() + " " + EqCurve.fixed(EqCurve.roundTenth(EqCurve.responseDb(gains, f) + preamp), 1)
            }
            "GraphicEQ: $points\n"
        }
    }

    internal fun graphicFrequencies(): List<Double> {
        val out = mutableListOf<Double>()
        var step = 0
        while (true) {
            val f = EqCurve.roundAway(20.0 * GRAPHIC_STEP.pow(step))
            if (f > 20_000.0) break
            if (out.lastOrNull() != f) out += f
            step++
        }
        return out
    }

    private fun String.words(): List<String> = trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

    internal fun number(token: String): Double? {
        val t = token.replace(',', '.').lowercase()
        val (digits, scale) = when {
            t.endsWith("khz") -> t.dropLast(3) to 1000.0
            t.endsWith("hz") -> t.dropLast(2) to 1.0
            t.endsWith("db") -> t.dropLast(2) to 1.0
            else -> t to 1.0
        }
        if (digits.isEmpty() || digits.any { it.isLetter() && it != 'e' }) return null
        return digits.toDoubleOrNull()?.takeIf { it.isFinite() }?.let { it * scale }
    }

    private fun filter(value: String): Line {
        val tokens = value.words()
        if (tokens.firstOrNull()?.equals("on", ignoreCase = true) != true) return Line.Off
        val kind = tokens.getOrNull(1)?.uppercase() ?: return Line.Off
        var frequency: Double? = null
        var gain: Double? = null
        var q: Double? = null
        var index = 2
        while (index < tokens.size) {
            val key = tokens[index].lowercase()
            val next = tokens.getOrNull(index + 1)?.let(::number)
            when (key) {
                "fc" -> {
                    val scale = if (tokens.getOrNull(index + 2)?.equals("khz", ignoreCase = true) == true) 1000.0 else 1.0
                    frequency = next?.let { it * scale }
                }
                "gain" -> gain = next
                "q" -> q = next
                "bw" -> {
                    val octaves = tokens.getOrNull(index + 2)?.let(::number)
                    q = octaves?.let {
                        val width = 2.0.pow(it)
                        sqrt(width) / (width - 1.0)
                    }
                }
            }
            index++
        }
        val f = frequency?.takeIf { it > 0.0 } ?: return Line.Unsupported
        val g = gain ?: return Line.Unsupported
        val shape = when (kind) {
            "PK", "PEQ" -> EqShape.PEAK
            "LS", "LSC", "LSQ" -> EqShape.LOW_SHELF
            "HS", "HSC", "HSQ" -> EqShape.HIGH_SHELF
            else -> return Line.Unsupported
        }
        val quality = q?.takeIf { it > 0.0 } ?: defaultQ(shape)
        return EqCurve.design(shape, g, f, quality, EqCurve.CURVE_RATE)?.let { Line.Filter(it) } ?: Line.Unsupported
    }

    private fun defaultQ(shape: EqShape): Double = if (shape == EqShape.PEAK) EqCurve.Q else EqCurve.Q_BUTTERWORTH

    private fun graphicPoints(value: String): List<Pair<Double, Double>> =
        value.split(';').mapNotNull { pair ->
            val parts = pair.words()
            val f = parts.getOrNull(0)?.let(::number) ?: return@mapNotNull null
            val g = parts.getOrNull(1)?.let(::number) ?: return@mapNotNull null
            if (f > 0.0) f to g else null
        }.sortedBy { it.first }

    private fun median(values: DoubleArray): Double {
        val sorted = values.sortedArray()
        return sorted[sorted.size / 2]
    }
}

class EqParams(val enabled: Boolean, val gains: DoubleArray, val preamp: Double) {
    fun sameCurve(other: EqParams): Boolean = preamp == other.preamp && gains.contentEquals(other.gains)
}

object EqLive {
    @Volatile
    var params: EqParams = EqParams(false, DoubleArray(EqCurve.BANDS), 0.0)
        private set

    fun publish(enabled: Boolean, gains: List<Double>, preamp: Double?) {
        val g = EqCurve.bandGains(gains)
        params = EqParams(enabled, g, EqCurve.resolvePreamp(g, preamp))
    }
}

class EqDsp {
    private var rate = 44_100.0
    private var channels = 2
    private val b0 = DoubleArray(EqCurve.BANDS)
    private val b1 = DoubleArray(EqCurve.BANDS)
    private val b2 = DoubleArray(EqCurve.BANDS)
    private val a1 = DoubleArray(EqCurve.BANDS)
    private val a2 = DoubleArray(EqCurve.BANDS)
    private var state = DoubleArray(channels * EqCurve.BANDS * 4)

    private var seen: EqParams? = null
    private var enabled = false
    private var wet = 0.0
    private val fromGains = DoubleArray(EqCurve.BANDS)
    private var fromPreamp = 0.0
    private val currentGains = DoubleArray(EqCurve.BANDS)
    private var currentPreamp = 0.0
    private val targetGains = DoubleArray(EqCurve.BANDS)
    private var targetPreamp = 0.0
    private val scratch = DoubleArray(EqCurve.BANDS)
    private var rampFrame = 0
    private var rampFrames = 1
    private var preampGain = 1.0
    private var preampTarget = 1.0
    private var smoothing = 1.0

    val bypass: Boolean get() = wet == 0.0

    fun configure(sampleRate: Int, channelCount: Int) {
        rate = sampleRate.coerceAtLeast(1).toDouble()
        channels = channelCount.coerceIn(1, 8)
        state = DoubleArray(channels * EqCurve.BANDS * 4)
        rampFrames = framesIn(RAMP_SECONDS).coerceAtLeast(1)
        smoothing = 1.0 - exp(-1.0 / framesIn(SMOOTHING_SECONDS).coerceAtLeast(1).toDouble())
        val p = EqLive.params
        seen = p
        enabled = p.enabled
        wet = if (enabled) 1.0 else 0.0
        p.gains.copyInto(currentGains)
        p.gains.copyInto(targetGains)
        p.gains.copyInto(fromGains)
        currentPreamp = p.preamp
        targetPreamp = p.preamp
        fromPreamp = p.preamp
        for (band in 0 until EqCurve.BANDS) setBand(band, currentGains[band])
        preampGain = dbToGain(currentPreamp)
        preampTarget = preampGain
        rampFrame = 0
    }

    fun reset() = state.fill(0.0)

    private fun framesIn(seconds: Double): Int = (seconds * rate).toInt()

    private fun setBand(band: Int, gain: Double) {
        val c = EqCurve.bandCoefficients(band, gain, rate)
        b0[band] = c.b0; b1[band] = c.b1; b2[band] = c.b2; a1[band] = c.a1; a2[band] = c.a2
    }

    private fun followParams() {
        val p = EqLive.params
        if (p === seen) return
        seen = p
        enabled = p.enabled
        val changed = p.preamp != targetPreamp || !p.gains.contentEquals(targetGains)
        if (changed) {
            p.gains.copyInto(targetGains)
            targetPreamp = p.preamp
            currentGains.copyInto(fromGains)
            fromPreamp = currentPreamp
            rampFrame = 0
        }
    }

    private fun curveDiffers(): Boolean = currentPreamp != targetPreamp || !currentGains.contentEquals(targetGains)

    private fun apply(gains: DoubleArray, preamp: Double) {
        for (band in 0 until EqCurve.BANDS) {
            if (gains[band] == currentGains[band]) continue
            setBand(band, gains[band])
            currentGains[band] = gains[band]
        }
        currentPreamp = preamp
        preampTarget = dbToGain(preamp)
    }

    fun beginFrame() {
        followParams()
        val wetTarget = if (enabled) 1.0 else 0.0
        wet += (wetTarget - wet) * smoothing
        if (!enabled && wet < BYPASS_BELOW) {
            if (wet > 0.0) reset()
            wet = 0.0
        }
        if (curveDiffers()) {
            rampFrame++
            if (wet == 0.0 || rampFrame >= rampFrames) {
                apply(targetGains, targetPreamp)
            } else if (rampFrame % RAMP_STEP_FRAMES == 0) {
                val position = rampFrame.toDouble() / rampFrames
                for (band in 0 until EqCurve.BANDS) scratch[band] = fromGains[band] + (targetGains[band] - fromGains[band]) * position
                apply(scratch, fromPreamp + (targetPreamp - fromPreamp) * position)
            }
        }
        if (wet == 0.0) preampGain = preampTarget else preampGain += (preampTarget - preampGain) * smoothing
    }

    fun process(channel: Int, sample: Double): Double {
        val ch = channel.coerceIn(0, channels - 1)
        if (!sample.isFinite()) {
            clearChannel(ch)
            return 0.0
        }
        if (wet == 0.0) return sample
        var x = sample * preampGain
        var at = ch * EqCurve.BANDS * 4
        val s = state
        for (band in 0 until EqCurve.BANDS) {
            val x1 = s[at]; val x2 = s[at + 1]; val y1 = s[at + 2]; val y2 = s[at + 3]
            val y = b0[band] * x + b1[band] * x1 + b2[band] * x2 - a1[band] * y1 - a2[band] * y2
            if (!y.isFinite()) {
                clearChannel(ch)
                return sample.coerceIn(-1.0, 1.0)
            }
            s[at + 1] = x1; s[at] = x; s[at + 3] = y1; s[at + 2] = y
            x = y
            at += 4
        }
        val out = sample + (x - sample) * wet
        return if (out > 1.0) 1.0 else if (out < -1.0) -1.0 else out
    }

    private fun clearChannel(channel: Int) {
        val start = channel * EqCurve.BANDS * 4
        state.fill(0.0, start, start + EqCurve.BANDS * 4)
    }

    private fun dbToGain(db: Double): Double = 10.0.pow(db / 20.0)

    private companion object {
        const val RAMP_SECONDS = 0.030
        const val RAMP_STEP_FRAMES = 32
        const val SMOOTHING_SECONDS = 0.003
        const val BYPASS_BELOW = 1e-4
    }
}
