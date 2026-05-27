package com.dexos.haven

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.os.Handler
import android.os.Looper
import java.util.Locale

class HavenListeningService : Service() {

    private var speechRecognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isListening = false
    private var isSpeaking = false

    companion object {
        var instance: HavenListeningService? = null
        var onCommandReceived: ((String) -> Unit)? = null
        var isSpeakingGlobal = false
        const val CHANNEL_ID = "haven_listening"
        const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Haven is listening..."))
        handler.postDelayed({ startListening() }, 1000)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Haven Listening",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Haven is actively listening for your commands"
            setSound(null, null)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Haven")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun startListening() {
        if (isListening || isSpeakingGlobal) return
        handler.post {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches: ArrayList<String>? = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()
                    android.util.Log.d("HavenSvc", "Got result: $text")
                    if (!text.isNullOrBlank() && text.length > 2) {
                        updateNotification("Heard: $text")
                        android.util.Log.d("HavenSvc", "Invoking callback for: $text")
                        onCommandReceived?.invoke(text)
                    } else {
                        if (!isSpeakingGlobal) handler.postDelayed({ startListening() }, 500)
                    }
                }
                override fun onError(error: Int) {
                    isListening = false
                    if (!isSpeakingGlobal) handler.postDelayed({ startListening() }, 1000)
                }
                override fun onReadyForSpeech(params: Bundle?) {
                    updateNotification("Haven is listening...")
                }
                override fun onBeginningOfSpeech() {
                    updateNotification("Hearing you...")
                }
                override fun onEndOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
            }
            try {
                speechRecognizer?.startListening(intent)
                isListening = true
            } catch (e: Exception) {
                handler.postDelayed({ startListening() }, 2000)
            }
        }
    }

    fun resumeListening() {
        isSpeakingGlobal = false
        handler.postDelayed({ startListening() }, 500)
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
