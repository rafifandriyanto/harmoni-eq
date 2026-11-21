package com.example.data.autoeq

import com.example.model.ISO_20_FREQUENCIES_HZ
import kotlin.math.ln
import kotlin.math.round

/**
 * Interpolates dense AutoEQ frequency-response measurement points (frequencyHz -> gainDb)
 * onto Harmoni EQ's 20 ISO band center frequencies in logarithmic frequency space.
 *
 * Uses Fritsch-Carlson Monotone Cubic Hermite Spline (PCHIP) by default for smooth,
 * artifact-free audio curves without overshoot around sharp headphone resonances,
 * with linear interpolation also available.
 */
object AutoEqInterpolator {

    const val MIN_GAIN_DB = -15f
    const val MAX_GAIN_DB = 15f

    /**
     * Interpolates [points] onto [targetFrequenciesHz] (default: Harmoni's 20 ISO bands)
     * using monotone cubic Hermite spline interpolation in log-frequency space.
     */
    fun interpolateToBands(
        points: List<Pair<Float, Float>>,
        targetFrequenciesHz: List<Int> = ISO_20_FREQUENCIES_HZ,
        useCubicSpline: Boolean = true
    ): List<Float> {
        val clean = sanitizePoints(points)
        if (clean.isEmpty()) return List(targetFrequenciesHz.size) { 0f }
        if (clean.size == 1) {
            val v = clampAndRound(clean[0].second)
            return List(targetFrequenciesHz.size) { v }
        }

        val xs = DoubleArray(clean.size) { ln(clean[it].first.toDouble()) }
        val ys = DoubleArray(clean.size) { clean[it].second.toDouble() }

        val slopes = if (useCubicSpline && clean.size >= 3) {
            computePchipSlopes(xs, ys)
        } else {
            null
        }

        return targetFrequenciesHz.map { targetHz ->
            val xTarget = ln(targetHz.coerceAtLeast(1).toDouble())
            val rawGain = evaluateAt(xTarget, xs, ys, slopes)
            clampAndRound(rawGain.toFloat())
        }
    }

    /** Convenience wrapper for pure log-frequency linear interpolation. */
    fun interpolateLinear(
        points: List<Pair<Float, Float>>,
        targetFrequenciesHz: List<Int> = ISO_20_FREQUENCIES_HZ
    ): List<Float> = interpolateToBands(points, targetFrequenciesHz, useCubicSpline = false)

    /** Convenience wrapper for smooth log-frequency monotone cubic spline interpolation. */
    fun interpolateCubicSpline(
        points: List<Pair<Float, Float>>,
        targetFrequenciesHz: List<Int> = ISO_20_FREQUENCIES_HZ
    ): List<Float> = interpolateToBands(points, targetFrequenciesHz, useCubicSpline = true)

    private fun sanitizePoints(points: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
        val grouped = LinkedHashMap<Float, Pair<Double, Int>>()
        points.forEach { (freq, gain) ->
            if (freq.isFinite() && gain.isFinite() && freq > 0f) {
                val prev = grouped[freq]
                grouped[freq] = if (prev == null) gain.toDouble() to 1 else (prev.first + gain) to (prev.second + 1)
            }
        }
        return grouped.entries
            .sortedBy { it.key }
            .map { (freq, sumCount) -> freq to (sumCount.first / sumCount.second).toFloat() }
    }

    /**
     * Computes Fritsch-Carlson monotone piecewise cubic Hermite tangents so the
     * interpolated curve stays smooth (C1) and never overshoots local extrema.
     */
    private fun computePchipSlopes(xs: DoubleArray, ys: DoubleArray): DoubleArray {
        val n = xs.size
        val h = DoubleArray(n - 1) { i -> xs[i + 1] - xs[i] }
        val delta = DoubleArray(n - 1) { i -> (ys[i + 1] - ys[i]) / h[i] }
        val m = DoubleArray(n)

        // Interior tangents: weighted harmonic mean when adjacent slopes share sign
        for (i in 1 until n - 1) {
            if (delta[i - 1] * delta[i] > 0.0) {
                val w1 = 2.0 * h[i] + h[i - 1]
                val w2 = h[i] + 2.0 * h[i - 1]
                m[i] = (w1 + w2) / (w1 / delta[i - 1] + w2 / delta[i])
            } else {
                m[i] = 0.0
            }
        }

        // Endpoint tangents using non-centered 3-point formula clamped for monotonicity
        m[0] = endpointSlope(h[0], h[1], delta[0], delta[1])
        m[n - 1] = endpointSlope(h[n - 2], h[n - 3], delta[n - 2], delta[n - 3])
        return m
    }

    private fun endpointSlope(h0: Double, h1: Double, d0: Double, d1: Double): Double {
        var slope = ((2.0 * h0 + h1) * d0 - h0 * d1) / (h0 + h1)
        if (slope * d0 <= 0.0) {
            slope = 0.0
        } else if (d0 * d1 <= 0.0 && kotlin.math.abs(slope) > kotlin.math.abs(3.0 * d0)) {
            slope = 3.0 * d0
        }
        return slope
    }

    private fun evaluateAt(
        x: Double,
        xs: DoubleArray,
        ys: DoubleArray,
        slopes: DoubleArray?
    ): Double {
        val last = xs.size - 1
        if (x <= xs[0]) return ys[0]
        if (x >= xs[last]) return ys[last]

        // Binary search for segment [i, i + 1] containing x
        var lo = 0
        var hi = last - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (x < xs[mid]) {
                hi = mid - 1
            } else if (x > xs[mid + 1]) {
                lo = mid + 1
            } else {
                lo = mid
                break
            }
        }
        val i = lo.coerceIn(0, last - 1)
        val dx = xs[i + 1] - xs[i]
        if (dx <= 0.0) return ys[i]

        val t = (x - xs[i]) / dx
        if (slopes == null) {
            return ys[i] + t * (ys[i + 1] - ys[i])
        }

        val t2 = t * t
        val t3 = t2 * t
        val h00 = 2.0 * t3 - 3.0 * t2 + 1.0
        val h10 = t3 - 2.0 * t2 + t
        val h01 = -2.0 * t3 + 3.0 * t2
        val h11 = t3 - t2
        return h00 * ys[i] + h10 * dx * slopes[i] + h01 * ys[i + 1] + h11 * dx * slopes[i + 1]
    }

    private fun clampAndRound(gainDb: Float): Float {
        if (!gainDb.isFinite()) return 0f
        val clamped = gainDb.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
        return round(clamped * 10f) / 10f
    }
}
