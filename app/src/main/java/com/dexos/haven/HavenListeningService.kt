package com.dexos.haven

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class HavenListeningService : Service() {

    private var speechRecognizer: SpeechRecognizer? = null
    private val client = OkHttpClient()
    private val RAILWAY_URL = "https://dex-backend-production-2bbe.up.railway.app/haven_api"
    private val TTS_URL = "https://dex-backend-production-2bbe.up.railway.app/haven_tts_free"
    private var isSpeaking = false
    private var speechQueue: MutableList<String> = mutableListOf()
    private var mediaPlayer: MediaPlayer? = null
    private val conversationHistory = mutableListOf<JSONObject>()
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var userId: String

    companion object {
        var instance: HavenListeningService? = null
        const val CHANNEL_ID = "haven_listening"
        const val NOTIFICATION_ID = 1

        // Called by FloatingHavenService (or anywhere) to kick off a listening turn
        fun triggerListen() {
            instance?.startListeningSession()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = getSharedPreferences("haven_prefs", MODE_PRIVATE)
        userId = prefs.getString("user_id", null) ?: run {
            val newId = java.util.UUID.randomUUID().toString()
            prefs.edit().putString("user_id", newId).apply()
            newId
        }
        restoreConversationHistory()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        setupSpeechRecognizer()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Haven is with you"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun restoreConversationHistory() {
        val saved = prefs.getString("conversation_history", null) ?: return
        try {
            val arr = JSONArray(saved)
            for (i in 0 until arr.length()) conversationHistory.add(arr.getJSONObject(i))
        } catch (e: Exception) {
            android.util.Log.e("Haven", "Service failed to restore history: ${e.message}")
        }
    }

    private fun saveConversationHistory() {
        val arr = JSONArray()
        for (msg in conversationHistory) arr.put(msg)
        prefs.edit().putString("conversation_history", arr.toString()).apply()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Haven", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Haven companion is active"
            setSound(null, null)
            enableVibration(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Haven")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    fun startListeningSession() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            updateNotification("Microphone permission needed -- open Haven to grant it")
            return
        }
        if (HavenState.isBusy()) {
            // Haven's own app is already listening/speaking in foreground -- don't collide
            return
        }
        HavenState.isListening = true
        if (isSpeaking) {
            speechQueue.clear()
            try { mediaPlayer?.stop() } catch (e: Exception) {}
            mediaPlayer?.release()
            mediaPlayer = null
            isSpeaking = false
        }
        updateNotification("Listening...")
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
        }
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            updateNotification("Haven is with you")
        }
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                HavenState.isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()
                if (text == null || text.length < 3) {
                    updateNotification("Haven is with you")
                    return
                }
                updateNotification("Thinking...")
                sendToBackend(text)
            }
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                HavenState.isListening = false
                updateNotification("Haven is with you")
            }
            override fun onReadyForSpeech(params: Bundle?) { updateNotification("Listening...") }
            override fun onBeginningOfSpeech() { updateNotification("Hearing you...") }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun sendToBackend(userMessage: String) {
        conversationHistory.add(JSONObject().put("role", "user").put("content", userMessage))
        val voiceContext = JSONObject()
            .put("message_length", userMessage.length)
            .put("word_count", userMessage.trim().split("\\s+".toRegex()).size)
            .put("is_short", userMessage.length < 20)
            .put("is_question", userMessage.trimEnd().endsWith("?"))
        val screenContext = JSONObject()
            .put("current_app", HavenAccessibilityService.currentScreenPackage)
            .put("screen_text", HavenAccessibilityService.currentScreenText)
        val body = JSONObject()
            .put("messages", JSONArray(conversationHistory.toString()))
            .put("user_id", userId)
            .put("voice_context", voiceContext)
            .put("screen_context", screenContext)
            .toString()
        val request = Request.Builder()
            .url(RAILWAY_URL)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = client.newCall(request).execute()
                val rawJson = JSONObject(response.body?.string() ?: "{}")
                val responseText = when {
                    rawJson.has("voice_response") -> rawJson.optString("voice_response")
                    rawJson.has("response") -> rawJson.optString("response")
                    else -> "I am right here with you."
                }
                if (rawJson.has("device_action") && !rawJson.isNull("device_action")) {
                    executeDeviceAction(rawJson.getJSONObject("device_action"))
                }
                conversationHistory.add(JSONObject().put("role", "assistant").put("content", responseText))
                withContext(Dispatchers.Main) {
                    saveConversationHistory()
                    updateNotification("Haven is speaking...")
                    speak(responseText)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { updateNotification("Connection error -- tap to try again") }
            }
        }
    }

    private fun executeDeviceAction(da: JSONObject) {
        val actionType = da.optString("type", "")
        val pkg = da.optString("package", "")
        val query = da.optString("query", "")
        try {
            when (actionType) {
                "OPEN_APP" -> {
                    if (pkg.isNotEmpty()) {
                        val launch = packageManager.getLaunchIntentForPackage(pkg)
                        if (launch != null) {
                            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(launch)
                        }
                    }
                }
                "CALL" -> {
                    val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                        if (query.isNotEmpty()) data = android.net.Uri.parse("tel:${query.replace(" ", "")}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(dialIntent)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("Haven", "Device action failed: ${e.message}")
        }
    }

    private fun splitIntoChunks(text: String): MutableList<String> {
        val sentences = text.split(Regex("(?<=[.!?])\\s+")).map { it.trim() }.filter { it.isNotBlank() }
        return if (sentences.isEmpty()) mutableListOf(text) else sentences.toMutableList()
    }

    private fun speak(text: String) {
        isSpeaking = true
        HavenState.isSpeaking = true
        speechQueue = splitIntoChunks(text)
        playNextChunk()
    }

    private fun playNextChunk() {
        if (!isSpeaking || speechQueue.isEmpty()) {
            if (isSpeaking) {
                isSpeaking = false
                HavenState.isSpeaking = false
                updateNotification("Haven is with you")
            }
            return
        }
        val chunk = speechQueue.removeAt(0)
        val body = JSONObject().put("text", chunk).toString()
        val request = Request.Builder()
            .url(TTS_URL)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = client.newCall(request).execute()
                val audioBytes = response.body?.bytes()
                if (audioBytes == null || audioBytes.size < 100) {
                    withContext(Dispatchers.Main) { playNextChunk() }
                    return@launch
                }
                val tempFile = File(cacheDir, "haven_tts_${System.currentTimeMillis()}.mp3")
                FileOutputStream(tempFile).use { it.write(audioBytes) }
                withContext(Dispatchers.Main) {
                    if (!isSpeaking) { tempFile.delete(); return@withContext }
                    try {
                        mediaPlayer?.release()
                        mediaPlayer = MediaPlayer().apply {
                            setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .build()
                            )
                            setDataSource(tempFile.absolutePath)
                            setOnPreparedListener { start() }
                            setOnCompletionListener {
                                release(); mediaPlayer = null; tempFile.delete(); playNextChunk()
                            }
                            setOnErrorListener { _, _, _ -> release(); mediaPlayer = null; playNextChunk(); true }
                            prepareAsync()
                        }
                    } catch (e: Exception) { playNextChunk() }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { playNextChunk() }
            }
        }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        mediaPlayer?.release()
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
