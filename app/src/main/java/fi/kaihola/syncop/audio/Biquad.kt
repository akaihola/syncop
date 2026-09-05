package fi.kaihola.syncop.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Minimal second-order IIR filter (RBJ cookbook). */
class Biquad(private val b0: Double, private val b1: Double, private val b2: Double,
             private val a1: Double, private val a2: Double) {
    private var x1 = 0.0; private var x2 = 0.0; private var y1 = 0.0; private var y2 = 0.0

    fun process(x: Double): Double {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = x; y2 = y1; y1 = y
        return y
    }

    fun reset() { x1 = 0.0; x2 = 0.0; y1 = 0.0; y2 = 0.0 }

    companion object {
        private const val Q = 0.7071

        fun lowPass(fc: Double, fs: Int): Biquad {
            val w0 = 2 * PI * fc / fs
            val alpha = sin(w0) / (2 * Q)
            val a0 = 1 + alpha
            return Biquad((1 - cos(w0)) / 2 / a0, (1 - cos(w0)) / a0, (1 - cos(w0)) / 2 / a0,
                -2 * cos(w0) / a0, (1 - alpha) / a0)
        }

        fun highPass(fc: Double, fs: Int): Biquad {
            val w0 = 2 * PI * fc / fs
            val alpha = sin(w0) / (2 * Q)
            val a0 = 1 + alpha
            return Biquad((1 + cos(w0)) / 2 / a0, -(1 + cos(w0)) / a0, (1 + cos(w0)) / 2 / a0,
                -2 * cos(w0) / a0, (1 - alpha) / a0)
        }

        fun bandPass(fc: Double, fs: Int, q: Double = 4.0): Biquad {
            val w0 = 2 * PI * fc / fs
            val alpha = sin(w0) / (2 * q)
            val a0 = 1 + alpha
            return Biquad(alpha / a0, 0.0, -alpha / a0, -2 * cos(w0) / a0, (1 - alpha) / a0)
        }
    }
}
