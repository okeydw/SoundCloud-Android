package com.scd.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

class EqTest {
    private val bassDestroyer = doubleArrayOf(12.0, 12.0, 10.0, 7.0, 3.0, 0.0, -2.0, -4.0, -4.0, -5.0)
    private val gains = doubleArrayOf(5.0, 3.0, 0.0, -2.0, -1.5, 0.0, 2.0, 4.0, -3.0, 1.0)

    @Test
    fun bandsSitAtTheirFrequencies() {
        for (band in 1 until EqCurve.BANDS - 1) {
            val filter = EqCurve.bandCoefficients(band, 6.0, EqCurve.CURVE_RATE)
            val centre = EqCurve.magnitudeDb(filter, EqCurve.FREQS[band])
            assertTrue("$band: $centre", abs(centre - 6.0) < 0.01)
            for (octaves in listOf(-1.0, 1.0)) {
                val side = EqCurve.magnitudeDb(filter, EqCurve.FREQS[band] * 2.0.pow(octaves))
                assertTrue("$band $octaves: $side", side < 4.0)
            }
        }
    }

    @Test
    fun shelvesTurnAtTheirFrequencies() {
        val low = EqCurve.bandCoefficients(0, 6.0, EqCurve.CURVE_RATE)
        assertTrue(abs(EqCurve.magnitudeDb(low, EqCurve.FREQS[0]) - 3.0) < 0.05)
        assertTrue(EqCurve.magnitudeDb(low, 20.0) > 4.5)
        assertTrue(EqCurve.magnitudeDb(low, 250.0) < 0.3)
        val high = EqCurve.bandCoefficients(EqCurve.BANDS - 1, 6.0, EqCurve.CURVE_RATE)
        assertTrue(abs(EqCurve.magnitudeDb(high, EqCurve.FREQS[EqCurve.BANDS - 1]) - 3.0) < 0.05)
        assertTrue(EqCurve.magnitudeDb(high, 20_000.0) > 4.0)
        assertTrue(EqCurve.magnitudeDb(high, 4000.0) < 0.5)
    }

    @Test
    fun autoPreampLeavesHeadroomForOverlappingBoosts() {
        val preamp = EqCurve.autoPreamp(bassDestroyer)
        assertTrue("$preamp", preamp < -12.0)
        var peak = Double.NEGATIVE_INFINITY
        for (f in EqCurve.grid) peak = max(peak, EqCurve.responseDb(bassDestroyer, f) + preamp)
        assertTrue("$peak", peak <= 0.06)
    }

    @Test
    fun autoPreampIsZeroForCuts() {
        assertEquals(0.0, EqCurve.autoPreamp(DoubleArray(10) { -3.0 }), 0.0)
        assertEquals(0.0, EqCurve.autoPreamp(DoubleArray(10)), 0.0)
    }

    @Test
    fun manualPreampWinsAndIsClamped() {
        assertEquals(-3.0, EqCurve.resolvePreamp(bassDestroyer, -3.0), 0.0)
        assertEquals(EqCurve.PREAMP_MIN, EqCurve.resolvePreamp(bassDestroyer, -90.0), 0.0)
    }

    @Test
    fun fitRecoversBandGains() {
        val wanted = doubleArrayOf(4.0, -2.0, 3.0, 0.0, -5.0, 2.5, 0.0, 6.0, -1.0, 3.0)
        val target = DoubleArray(EqCurve.grid.size) { EqCurve.responseDb(wanted, EqCurve.grid[it]) }
        val fit = EqCurve.fit(target)
        for (band in 0 until EqCurve.BANDS) assertTrue("$band: ${fit.gains.toList()}", abs(fit.gains[band] - wanted[band]) < 0.3)
        assertTrue("${fit.deviation}", fit.deviation < 0.3)
    }

    @Test
    fun fitReportsWhatItCannotFollow() {
        val notch = EqCurve.design(EqShape.PEAK, -15.0, 3000.0, 8.0, EqCurve.CURVE_RATE)!!
        val target = DoubleArray(EqCurve.grid.size) { EqCurve.magnitudeDb(notch, EqCurve.grid[it]) }
        val fit = EqCurve.fit(target)
        assertTrue("${fit.deviation}", fit.deviation > 5.0)
        assertTrue(fit.gains.all { abs(it) <= EqCurve.MAX_GAIN })
    }

    @Test
    fun apoExportImportsBack() {
        val text = EqTransfer.export(gains, -5.5, EqFormat.APO)
        assertTrue(text, text.startsWith("Preamp: -5.5 dB\nFilter 1: ON LSC Fc 32 Hz Gain 5.0 dB Q 0.71\n"))
        val imported = EqTransfer.import(text)!!
        for (band in 0 until EqCurve.BANDS) assertTrue("${imported.gains}", abs(imported.gains[band] - gains[band]) <= 0.15)
        assertEquals(-5.5, imported.preamp!!, 0.0)
        assertEquals(EqCurve.BANDS, imported.used)
        assertTrue(imported.skipped.isEmpty())
        assertTrue("${imported.deviation}", imported.deviation <= 0.2)
    }

    @Test
    fun graphicExportImportsBack() {
        val preamp = EqCurve.autoPreamp(gains)
        val text = EqTransfer.export(gains, preamp, EqFormat.GRAPHIC)
        assertTrue(text.startsWith("GraphicEQ: 20 "))
        assertEquals(EqTransfer.graphicFrequencies().size, text.count { it == ';' } + 1)
        val imported = EqTransfer.import(text)!!
        val restored = imported.preamp ?: 0.0
        val back = imported.gains.toDoubleArray()
        for (f in EqCurve.grid) {
            val original = EqCurve.responseDb(gains, f) + preamp
            val again = EqCurve.responseDb(back, f) + restored
            assertTrue("$f: $original $again", abs(original - again) < 0.6)
        }
    }

    @Test
    fun readsPeaceAndApoVariants() {
        val text = """
            # headphone correction
            Device: Speakers
            Preamp: -6.2 dB
            Filter1: ON LSC Fc 105 Hz Gain 5.5 dB Q 0.70
            Filter 2: ON PK Fc 2,2 kHz Gain -3 dB BW Oct 1.0
            Filter 3: OFF PK Fc 500 Hz Gain 9 dB Q 1
            Filter 4: ON HP Fc 20 Hz
            Filter 5: ON HSC Fc 10000 Hz Gain -2.5 dB Q 0.7
            Channel: R
            Filter 6: ON PK Fc 1000 Hz Gain 6 dB Q 1
            Channel: all
            Filter 7: ON PK Fc 6000 Hz Gain 2 dB Q 3
        """.trimIndent()
        val imported = EqTransfer.import(text)!!
        assertEquals(4, imported.used)
        assertEquals(listOf("Filter 4: ON HP Fc 20 Hz", "Filter 6: ON PK Fc 1000 Hz Gain 6 dB Q 1"), imported.skipped)
        assertEquals(-6.2, imported.preamp!!, 0.0)
        assertTrue("${imported.gains}", imported.gains[1] > 2.0)
        assertTrue("${imported.gains}", imported.gains[6] < -2.0)
        assertTrue(imported.deviation > 0.0)
    }

    @Test
    fun readsUnitsGluedToNumbersAndSkipsBadValues() {
        val text = """
            Preamp: -6dB
            Filter 1: ON PK Fc 1kHz Gain -3dB Q 1
            Filter 2: ON PK Fc 100Hz Gain 4,5dB Q 1
            Filter 3: ON PK Fc 300 Hz Gain loud dB Q 1
            Preamp: quiet
        """.trimIndent()
        val imported = EqTransfer.import(text)!!
        assertEquals(2, imported.used)
        assertEquals(-6.0, imported.preamp!!, 0.0)
        assertEquals(listOf("Filter 3: ON PK Fc 300 Hz Gain loud dB Q 1", "Preamp: quiet"), imported.skipped)
        assertTrue("${imported.gains}", imported.gains[1] > 2.0)
        assertTrue("${imported.gains}", imported.gains[5] < -1.5)
    }

    private fun peaceFile(general: String, gains: String, filters: String): String {
        val frequencies = listOf(10, 21, 42, 83, 166, 333, 577, 1000, 2000, 4000, 8000, 16000, 20000)
        val text = StringBuilder("[Speakers]\r\nSpeakerId0=0\r\nSpeakerTargets0=all\r\n")
        text.append("[General]\r\n").append(general)
        for (suffix in listOf("", "1")) {
            text.append("[Frequencies$suffix]\r\n")
            frequencies.forEachIndexed { i, f -> text.append("Frequency${i + 1}=$f\r\n") }
            text.append("[Qualities$suffix]\r\n")
            for (i in frequencies.indices) text.append("Quality${i + 1}=1.41\r\n")
        }
        text.append("[Gains]\r\n").append(gains).append("[Filters]\r\n").append(filters)
        text.append("[Configuration]\r\nHotKey=\r\n")
        return text.toString()
    }

    @Test
    fun readsPeaceFiles() {
        val imported = EqTransfer.import(peaceFile("PreAmp=-10\r\nPreAmp1=-4\r\n", "Gain2=0\r\nGain8=10\r\nGain9=3\r\n", "Filter8=4\r\nFilter9=2\r\n"))!!
        assertEquals(1, imported.used)
        assertEquals(-10.0, imported.preamp!!, 0.0)
        assertEquals(listOf("Filter9=2", "PreAmp1=-4"), imported.skipped)
        assertTrue("${imported.gains}", imported.gains[2] > 6.0)
        assertTrue("${imported.gains}", abs(imported.gains[9]) < 1.0)

        val peaks = EqTransfer.import(peaceFile("", "Gain1=9.5\r\nGain4=3.5\r\nGain12=-3\r\n", ""))!!
        assertEquals(3, peaks.used)
        assertNull(peaks.preamp)
        assertTrue("${peaks.gains}", peaks.gains[1] > 1.0)
        assertTrue("${peaks.gains}", peaks.gains[9] < -1.0)

        val graphic = EqTransfer.import(peaceFile("GraphicEQ=1\r\n", "Gain1=6\r\nGain2=6\r\n", ""))!!
        assertEquals(1, graphic.used)
        assertTrue("${graphic.gains}", graphic.gains[0] > 3.0)
    }

    @Test
    fun autoEqGraphicLineKeepsItsLevelAsPreamp() {
        val imported = EqTransfer.import("GraphicEQ: 20 -1.0; 60 -1.0; 200 -7.0; 20000 -7.0")!!
        assertEquals(-7.0, imported.preamp!!, 0.0)
        assertTrue("${imported.gains}", imported.gains[0] > 3.0)
    }

    @Test
    fun rejectsTextWithoutFilters() {
        assertNull(EqTransfer.import("hello\nPreamp: -3 dB"))
        assertNull(EqTransfer.import(""))
    }

    @Test
    fun decodesUtf16AndBom() {
        val utf16 = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "Preamp: -1 dB".toByteArray(Charsets.UTF_16LE)
        assertEquals("Preamp: -1 dB", EqTransfer.decode(utf16))
        assertEquals("Preamp", EqTransfer.decode(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "Preamp".toByteArray()))
    }

    @Test
    fun numbersNeverUseTheLocaleComma() {
        assertEquals("-3.5", EqCurve.fixed(-3.5, 1))
        assertEquals("0.0", EqCurve.fixed(-0.04, 1))
        assertEquals("0.71", EqCurve.fixed(EqCurve.Q_BUTTERWORTH, 2))
        assertEquals("+4.5", EqCurve.signed(4.5))
        assertEquals("-12.0", EqCurve.signed(-12.0))
    }

    private fun sine(frequency: Double, rate: Int, frames: Int): DoubleArray =
        DoubleArray(frames) { 0.5 * sin(2.0 * PI * frequency * it / rate) }

    private fun runDsp(dsp: EqDsp, input: DoubleArray, channels: Int = 2): Array<DoubleArray> {
        val out = Array(channels) { DoubleArray(input.size) }
        for (i in input.indices) {
            dsp.beginFrame()
            for (c in 0 until channels) out[c][i] = dsp.process(c, input[i])
        }
        return out
    }

    private fun peak(x: DoubleArray, from: Int): Double {
        var p = 0.0
        for (i in from until x.size) p = max(p, abs(x[i]))
        return p
    }

    @Test
    fun disabledEqualizerIsBitExactPassThrough() {
        EqLive.publish(false, bassDestroyer.toList(), null)
        val dsp = EqDsp().apply { configure(48_000, 2) }
        val input = sine(1000.0, 48_000, 4800)
        val out = runDsp(dsp, input)
        assertTrue(dsp.bypass)
        for (i in input.indices) assertEquals(input[i], out[0][i], 0.0)
    }

    @Test
    fun aBoostedBandRaisesItsToneByItsGain() {
        val g = DoubleArray(10).also { it[5] = 6.0 }
        EqLive.publish(true, g.toList(), 0.0)
        val dsp = EqDsp().apply { configure(48_000, 2) }
        val out = runDsp(dsp, sine(1000.0, 48_000, 48_000))
        val db = 20.0 * kotlin.math.log10(peak(out[0], 24_000) / 0.5)
        assertTrue("$db", abs(db - 6.0) < 0.1)
        assertEquals(peak(out[0], 24_000), peak(out[1], 24_000), 1e-12)
    }

    @Test
    fun theOutputMatchesThePlainBiquadCascade() {
        EqLive.publish(true, gains.toList(), -4.0)
        val rate = 44_100
        val dsp = EqDsp().apply { configure(rate, 1) }
        val input = sine(440.0, rate, 2000)
        val out = runDsp(dsp, input, channels = 1)[0]
        val coeffs = (0 until 10).map { EqCurve.bandCoefficients(it, gains[it], rate.toDouble()) }
        val st = Array(10) { DoubleArray(4) }
        val pre = 10.0.pow(-4.0 / 20.0)
        for (i in input.indices) {
            var x = input[i] * pre
            for (b in 0 until 10) {
                val c = coeffs[b]; val s = st[b]
                val y = c.b0 * x + c.b1 * s[0] + c.b2 * s[1] - c.a1 * s[2] - c.a2 * s[3]
                s[1] = s[0]; s[0] = x; s[3] = s[2]; s[2] = y
                x = y
            }
            assertEquals("frame $i", x.coerceIn(-1.0, 1.0), out[i], 1e-12)
        }
    }

    @Test
    fun switchingOnFadesInWithoutAClick() {
        EqLive.publish(false, bassDestroyer.toList(), null)
        val rate = 48_000
        val dsp = EqDsp().apply { configure(rate, 1) }
        val input = sine(60.0, rate, 9600)
        val out = DoubleArray(input.size)
        for (i in input.indices) {
            if (i == 2400) EqLive.publish(true, bassDestroyer.toList(), null)
            dsp.beginFrame()
            out[i] = dsp.process(0, input[i])
        }
        var jump = 0.0
        for (i in 2401 until input.size) jump = max(jump, abs(out[i] - out[i - 1]))
        val natural = 2.0 * PI * 60.0 / rate * 0.5 * 4.0
        assertTrue("jump $jump", jump < natural)
        assertTrue(!dsp.bypass)
    }

    @Test
    fun aNewCurveRampsInsteadOfJumping() {
        EqLive.publish(true, DoubleArray(10).toList(), 0.0)
        val rate = 48_000
        val dsp = EqDsp().apply { configure(rate, 1) }
        val input = sine(1000.0, rate, 9600)
        val out = DoubleArray(input.size)
        for (i in input.indices) {
            if (i == 4800) EqLive.publish(true, DoubleArray(10).also { it[5] = 6.0 }.toList(), 0.0)
            dsp.beginFrame()
            out[i] = dsp.process(0, input[i])
        }
        var jump = 0.0
        for (i in 4801 until 4801 + 1440) jump = max(jump, abs(out[i] - out[i - 1]))
        assertTrue("jump $jump", jump < 0.6)
        val settled = 20.0 * kotlin.math.log10(peak(out, 7200) / 0.5)
        assertTrue("$settled", abs(settled - 6.0) < 0.2)
    }

    @Test
    fun aBandAboveNyquistStaysFlatAtLowRates() {
        val c = EqCurve.bandCoefficients(9, 12.0, 22_050.0)
        assertEquals(1.0, c.b0, 0.0)
        assertEquals(0.0, c.a1, 0.0)
    }
}
