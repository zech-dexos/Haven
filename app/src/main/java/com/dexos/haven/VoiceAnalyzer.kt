package com.dexos.haven

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

data class VoiceAnalysis(
    val pitch: Float,        // estimated Hz
    val speechRate: Float,   // energy bursts per second (proxy for syllable rate)
    val energy: Float,       // RMS energy 0.0-1.0
    val pauseRate: Float,    // fraction of silent frames
    val durationMs: Long
)

object VoiceAnalyzer {

    private const val SAMPLE_RATE = 16000
    private const val FRAME_SIZE = 512
    private const val SILENCE_THRESHOLD = 0.01f

    fun analyze(pcmData: ShortArray, durationMs: Long): VoiceAnalysis {
        val floats = pcmData.map { it / 32768f }.toFloatArray()

        val energy = rmsEnergy(floats)
        val pitch = estimatePitch(floats, SAMPLE_RATE)
        val (speechRate, pauseRate) = estimateSpeechRate(floats)

        return VoiceAnalysis(
            pitch = pitch,
            speechRate = speechRate,
            energy = energy,
            pauseRate = pauseRate,
            durationMs = durationMs
        )
    }

    // RMS energy — overall loudness
    private fun rmsEnergy(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0f
        val sum = samples.fold(0f) { acc, s -> acc + s * s }
        return sqrt(sum / samples.size).coerceIn(0f, 1f)
    }

    // Autocorrelation-based pitch estimation
    private fun estimatePitch(samples: FloatArray, sampleRate: Int): Float {
        val minLag = sampleRate / 400  // 400 Hz max
        val maxLag = sampleRate / 50   // 50 Hz min
        val window = samples.take(minOf(samples.size, 2048)).toFloatArray()

        var bestLag = minLag
        var bestCorr = -1f

        for (lag in minLag..minOf(maxLag, window.size - 1)) {
            var corr = 0f
            for (i in 0 until window.size - lag) {
                corr += window[i] * window[i + lag]
            }
            if (corr > bestCorr) {
                bestCorr = corr
                bestLag = lag
            }
        }

        return if (bestCorr > 0f) sampleRate.toFloat() / bestLag else 0f
    }

    // Energy burst counting as speech rate proxy
    // Returns: bursts per second, silence fraction
    private fun estimateSpeechRate(samples: FloatArray): Pair<Float, Float> {
        if (samples.isEmpty()) return Pair(0f, 0f)

        val frames = samples.toList().chunked(FRAME_SIZE)
        var voicedFrames = 0
        var silentFrames = 0
        var transitions = 0
        var lastWasVoiced = false

        for (frame in frames) {
            val rms = sqrt(frame.fold(0f) { a, s -> a + s * s } / frame.size)
            val isVoiced = rms > SILENCE_THRESHOLD
            if (isVoiced) voicedFrames++ else silentFrames++
            if (isVoiced != lastWasVoiced) transitions++
            lastWasVoiced = isVoiced
        }

        val totalFrames = frames.size.toFloat()
        val durationSec = (samples.size.toFloat() / SAMPLE_RATE)
        val burstRate = if (durationSec > 0) (transitions / 2f) / durationSec else 0f
        val pauseRate = if (totalFrames > 0) silentFrames / totalFrames else 0f

        return Pair(burstRate, pauseRate)
    }

    // Extract MFCC-like feature vector for IoT relay
    // Lightweight 13-coefficient approximation
    fun extractFeatureVector(pcmData: ShortArray): FloatArray {
        val floats = pcmData.map { it / 32768f }.toFloatArray()
        val frameSize = 512
        val numCoeffs = 13
        val result = FloatArray(numCoeffs)

        if (floats.size < frameSize) return result

        // Use middle frame for stability
        val start = (floats.size / 2) - (frameSize / 2)
        val frame = floats.copyOfRange(start, start + frameSize)

        // Apply Hamming window
        for (i in frame.indices) {
            frame[i] *= (0.54f - 0.46f * kotlin.math.cos(2 * Math.PI.toFloat() * i / (frameSize - 1)))
        }

        // Log energy per band (mel-spaced approximation)
        val bandSize = frameSize / numCoeffs
        for (b in 0 until numCoeffs) {
            val bandStart = b * bandSize
            val bandEnd = minOf(bandStart + bandSize, frame.size)
            val energy = frame.slice(bandStart until bandEnd)
                .fold(0f) { acc, s -> acc + s * s } / bandSize
            result[b] = ln(energy.coerceAtLeast(1e-10f))
        }

        return result
    }
}
