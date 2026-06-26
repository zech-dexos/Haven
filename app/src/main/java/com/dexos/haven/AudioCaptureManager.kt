package com.dexos.haven

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat

class AudioCaptureManager(private val context: Context) {

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        // 3 seconds max — enough for a voiceprint, small in RAM (~96KB)
        const val MAX_SAMPLES = SAMPLE_RATE * 3
    }

    private var audioRecord: AudioRecord? = null
    private var isCapturing = false
    private val capturedBuffer = mutableListOf<Short>()
    private var captureStartMs = 0L

    fun startCapture() {
        if (isCapturing) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) return

        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT
        ).coerceAtLeast(4096)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        capturedBuffer.clear()
        captureStartMs = System.currentTimeMillis()
        isCapturing = true
        audioRecord?.startRecording()

        Thread {
            val chunk = ShortArray(bufferSize / 2)
            while (isCapturing) {
                val read = audioRecord?.read(chunk, 0, chunk.size) ?: 0
                if (read > 0) {
                    synchronized(capturedBuffer) {
                        // Cap at MAX_SAMPLES — oldest dropped, ring-buffer style
                        capturedBuffer.addAll(chunk.take(read))
                        if (capturedBuffer.size > MAX_SAMPLES) {
                            val excess = capturedBuffer.size - MAX_SAMPLES
                            repeat(excess) { capturedBuffer.removeAt(0) }
                        }
                    }
                }
            }
        }.start()
    }

    // Returns PCM, extracts features, caller must discard PCM after use
    fun stopAndGetPcm(): ShortArray {
        isCapturing = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        return synchronized(capturedBuffer) {
            capturedBuffer.toShortArray().also { capturedBuffer.clear() }
        }
    }

    fun durationMs(): Long = System.currentTimeMillis() - captureStartMs
}
