package com.iab.omid.sampleapp.player.tracking

/**
 * Quartile progression definition with companion factory.
 * Parity thresholds:
 *  - UNKNOWN: <=1% of duration
 *  - START: >1% and <25%
 *  - FIRST: >=25% and <50%
 *  - SECOND: >=50% and <75%
 *  - THIRD: >=75%
 */
enum class Quartile {
    UNKNOWN, START, FIRST, SECOND, THIRD;

    companion object {
        private const val EPSILON = 0.000001
        private fun lessThan(a: Double, b: Double): Boolean = b - a > EPSILON
        fun from(positionMs: Long, durationMs: Long): Quartile {
            if (durationMs <= 0L) return UNKNOWN
            val fraction = positionMs.toDouble() / durationMs.toDouble()
            if (lessThan(fraction, 0.01)) return UNKNOWN
            if (lessThan(fraction, 0.25)) return START
            if (lessThan(fraction, 0.5)) return FIRST
            if (lessThan(fraction, 0.75)) return SECOND
            return THIRD
        }
    }
}

