package com.dexos.haven

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.content.Intent
import android.content.SharedPreferences
import android.widget.TextView
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.view.Gravity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import android.provider.Settings
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var statusText: TextView
    private lateinit var memoryBar: TextView
    private lateinit var micButton: Button
    private lateinit var sendButton: Button
    private lateinit var textInput: EditText
    private lateinit var chatContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var prefs: SharedPreferences
    private val client = OkHttpClient()
    private val RAILWAY_URL = "https://dex-backend-production-2bbe.up.railway.app/haven_api"
    private val TTS_URL = "https://dex-backend-production-2bbe.up.railway.app/haven_tts_free"
    private val conversationHistory = mutableListOf<JSONObject>()
    private var isSpeaking = false
    private var isWaitingForResponse = false
    private lateinit var userId: String
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusText = findViewById(R.id.statusText)
        memoryBar = findViewById(R.id.memoryBar)
        micButton = findViewById(R.id.micButton)
        sendButton = findViewById(R.id.sendButton)
        textInput = findViewById(R.id.textInput)
        chatContainer = findViewById(R.id.chatContainer)
        scrollView = findViewById(R.id.scrollView)
        prefs = getSharedPreferences("haven_prefs", MODE_PRIVATE)
        userId = prefs.getString("user_id", null) ?: run {
            val newId = UUID.randomUUID().toString()
            prefs.edit().putString("user_id", newId).apply()
            newId
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        setupSpeechRecognizer()
        micButton.setOnClickListener { startListening() }
        sendButton.setOnClickListener {
            val text = textInput.text.toString().trim()
            if (text.isNotEmpty()) {
                textInput.setText("")
                addBubble(text, isUser = true)
                val handled = handleLocalCommand(text)
                if (!handled) sendToHaven(text)
            }
        }
        memoryBar.text = "Memory active"
        addBubble("Haven is ready. Tap the button and speak.", isUser = false)
        statusText.text = "DexOS · ReasonFlow active · memory synced"
    }

    private fun addBubble(text: String, isUser: Boolean) {
        val bubble = TextView(this)
        bubble.text = text
        bubble.textSize = 13f
        bubble.setTextColor(if (isUser) Color.parseColor("#9FB8CC") else Color.parseColor("#9FE1CB"))
        bubble.setPadding(28, 20, 28, 20)
        val bg = GradientDrawable()
        bg.cornerRadius = 28f
        bg.setColor(if (isUser) Color.parseColor("#0f1a28") else Color.parseColor("#0a1a14"))
        bg.setStroke(1, if (isUser) Color.parseColor("#1a3040") else Color.parseColor("#0f3d2a"))
        bubble.background = bg
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.topMargin = 8
        params.bottomMargin = 8
        if (isUser) {
            params.gravity = Gravity.END
            params.marginEnd = 8
        } else {
            params.gravity = Gravity.START
            params.marginStart = 8
        }
        bubble.layoutParams = params
        chatContainer.addView(bubble)
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches: ArrayList<String>? = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: run {
                    micButton.text = "Speak to Haven"
                    micButton.isEnabled = true
                    return
                }
                if (text.length < 3) {
                    micButton.text = "Speak to Haven"
                    micButton.isEnabled = true
                    return
                }
                addBubble(text, isUser = true)
                micButton.text = "Thinking..."
                micButton.isEnabled = false
                val handled = handleLocalCommand(text)
                if (!handled) sendToHaven(text)
            }
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                micButton.text = "Speak to Haven"
                micButton.isEnabled = true
                statusText.text = "DexOS · ReasonFlow active · memory synced"
            }
            override fun onReadyForSpeech(params: Bundle?) { statusText.text = "Listening..." }
            override fun onBeginningOfSpeech() { statusText.text = "Hearing you..." }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun startListening() {
        if (isSpeaking || isWaitingForResponse) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        micButton.text = "Listening..."
        speechRecognizer.startListening(intent)
    }

    private fun openAppByLabel(appName: String): Boolean {
        val lower = appName.lowercase()

        // Layer 1: System intents — works on every Android guaranteed
        val systemIntent: Intent? = when {
            lower.contains("setting") -> Intent(Settings.ACTION_SETTINGS)
            lower.contains("wifi") -> Intent(Settings.ACTION_WIFI_SETTINGS)
            lower.contains("bluetooth") -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            lower.contains("contact") -> Intent(Intent.ACTION_VIEW).apply {
                type = "vnd.android.cursor.dir/contact"
            }
            lower.contains("phone") || lower.contains("dialer") || lower.contains("call") ->
                Intent(Intent.ACTION_DIAL)
            lower.contains("camera") -> Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
            lower.contains("browser") || lower.contains("chrome") ->
                Intent(Intent.ACTION_VIEW, Uri.parse("http://"))
            else -> null
        }
        if (systemIntent != null) {
            systemIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try { startActivity(systemIntent); true } catch (e: Exception) { false }
        }

        // Layer 2: Scan launcher-visible apps by label
        val pm = packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val apps = pm.queryIntentActivities(launcherIntent, 0)
        val match = apps.firstOrNull {
            it.loadLabel(pm).toString().lowercase().contains(lower)
        }
        return if (match != null) {
            val launchIntent = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                true
            } else false
        } else false
    }

    private fun handleLocalCommand(text: String): Boolean {
        val lower = text.lowercase()
        if (lower.contains("open") || lower.contains("launch") || lower.contains("go to")) {
            val triggerWords = listOf("go to", "open", "launch")
            var appQuery = lower
            for (trigger in triggerWords) {
                if (lower.contains(trigger)) {
                    appQuery = lower.substringAfter(trigger).trim()
                    break
                }
            }
            if (appQuery.isNotEmpty()) {
                val found = openAppByLabel(appQuery)
                val msg = if (found) "Opening $appQuery. Come back to Haven anytime."
                          else "I couldn't find $appQuery on your phone."
                addBubble(msg, isUser = false)
                speakWithGTTS(msg)
                micButton.text = "Speak to Haven"
                micButton.isEnabled = true
                return true
            }
        }
        if (lower.contains("go home") || lower.contains("home screen")) {
            HavenAccessibilityService.performHome()
            val msg = "Going home."
            addBubble(msg, isUser = false)
            speakWithGTTS(msg)
            micButton.text = "Speak to Haven"
            micButton.isEnabled = true
            return true
        }
        if (lower.contains("go back")) {
            HavenAccessibilityService.performBack()
            val msg = "Going back."
            addBubble(msg, isUser = false)
            speakWithGTTS(msg)
            micButton.text = "Speak to Haven"
            micButton.isEnabled = true
            return true
        }
        return false
    }

    private fun sendToHaven(userMessage: String) {
        isWaitingForResponse = true
        statusText.text = "Haven is thinking..."
        conversationHistory.add(JSONObject().put("role", "user").put("content", userMessage))
        val body = JSONObject()
            .put("messages", JSONArray(conversationHistory.toString()))
            .put("user_id", userId)
            .toString()
        val request = Request.Builder()
            .url(RAILWAY_URL)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = client.newCall(request).execute()
                val responseText = JSONObject(response.body?.string() ?: "").optString("response", "I am here.")
                conversationHistory.add(JSONObject().put("role", "assistant").put("content", responseText))
                withContext(Dispatchers.Main) {
                    isWaitingForResponse = false
                    addBubble(responseText, isUser = false)
                    micButton.text = "Speaking..."
                    micButton.isEnabled = false
                    statusText.text = "Haven is speaking..."
                    speakWithGTTS(responseText)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isWaitingForResponse = false
                    statusText.text = "Connection error."
                    micButton.text = "Speak to Haven"
                    micButton.isEnabled = true
                }
            }
        }
    }

    private fun speakWithGTTS(text: String) {
        isSpeaking = true
        val body = JSONObject().put("text", text).toString()
        val request = Request.Builder()
            .url(TTS_URL)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = client.newCall(request).execute()
                val audioBytes = response.body?.bytes()
                if (audioBytes == null || audioBytes.size < 1000) {
                    withContext(Dispatchers.Main) { onSpeakDone() }
                    return@launch
                }
                val tempFile = File(cacheDir, "haven_tts.mp3")
                FileOutputStream(tempFile).use { it.write(audioBytes) }
                withContext(Dispatchers.Main) {
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
                                release()
                                mediaPlayer = null
                                onSpeakDone()
                            }
                            setOnErrorListener { _, _, _ ->
                                release()
                                mediaPlayer = null
                                onSpeakDone()
                                true
                            }
                            prepareAsync()
                        }
                    } catch (e: Exception) {
                        onSpeakDone()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onSpeakDone() }
            }
        }
    }

    private fun onSpeakDone() {
        isSpeaking = false
        micButton.text = "Speak to Haven"
        micButton.isEnabled = true
        statusText.text = "DexOS · ReasonFlow active · memory synced"
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        speechRecognizer.destroy()
        super.onDestroy()
    }
}
