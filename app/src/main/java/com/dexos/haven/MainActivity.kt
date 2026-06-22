package com.dexos.haven

import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
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
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
import android.provider.ContactsContract
import android.content.ContentValues
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
    private var speechQueue: MutableList<String> = mutableListOf()
    private val RECORD_AUDIO_REQUEST_CODE = 101
    private val READ_CONTACTS_REQUEST_CODE = 102
    private var pendingCallName: String? = null
    private val WRITE_CONTACTS_REQUEST_CODE = 103
    private var pendingSaveContactName: String? = null
    private var pendingSaveContactNumber: String? = null
    private var conversationMode: Boolean = false
    private var pendingWorkflow: String? = null
    private var workflowContactName: String? = null
    private var workflowContactNumber: String? = null
    private var workflowSmsMessage: String? = null

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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_REQUEST_CODE
            )
            return
        }
        if (isWaitingForResponse) return
        if (isSpeaking) {
            // Barge-in: stop current speech immediately, drop queued sentences, start listening
            speechQueue.clear()
            try { mediaPlayer?.stop() } catch (e: Exception) {}
            mediaPlayer?.release()
            mediaPlayer = null
            isSpeaking = false
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
        }
        micButton.text = "Listening..."
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        try { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0) } catch (e: Exception) {}
        Handler(Looper.getMainLooper()).postDelayed({
            try { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0) } catch (e: Exception) {}
        }, 500)
        speechRecognizer.startListening(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startListening()
            } else {
                addBubble("Haven needs microphone access to hear you. Please enable it in phone settings.", isUser = false)
            }
        } else if (requestCode == READ_CONTACTS_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pendingCallName?.let { name -> callContactByName(name) }
            } else {
                addBubble("Haven needs contacts access to call people by name. Please enable it in phone settings.", isUser = false)
            }
        } else if (requestCode == WRITE_CONTACTS_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                val name = pendingSaveContactName
                val number = pendingSaveContactNumber
                if (name != null && number != null) silentSaveContact(name, number)
            } else {
                addBubble("Haven needs contacts access to save numbers for you. Please enable it in phone settings.", isUser = false)
            }
        }
    }

    private fun extractPhoneNumber(text: String): String? {
        val regex = Regex("[0-9][0-9\\-\\s]{6,}[0-9]")
        val match = regex.find(text) ?: return null
        val digits = match.value.filter { it.isDigit() }
        return if (digits.length in 7..15) digits else null
    }

    private fun extractContactName(lowerText: String, phoneNumberDigits: String): String {
        val numberRegex = Regex("[0-9][0-9\\-\\s]{6,}[0-9]")
        val withoutNumber = numberRegex.replace(lowerText, " ").trim()
        // Strategy 1: grab what comes after "for" — "save 555 for Mom" -> "Mom"
        val forMatch = Regex("\\bfor\\s+(.+)$").find(withoutNumber)
        if (forMatch != null) {
            val candidate = forMatch.groupValues[1]
                .replace(Regex("\\b(to|my|the|contacts|contact|number|phone)\\b"), " ")
                .replace(Regex("'s\\b"), "")
                .trim()
                .split(Regex("\\s+")).filter { it.isNotBlank() }.joinToString(" ")
            if (candidate.isNotBlank()) return candidate.replaceFirstChar { it.uppercase() }
        }
        // Strategy 2: grab what comes after possessive — "Sarah's number" -> "Sarah"
        val possessive = Regex("([a-z]+)'s\\s+(number|phone|contact)").find(withoutNumber)
        if (possessive != null) return possessive.groupValues[1].replaceFirstChar { it.uppercase() }
        // Strategy 3: last word(s) before/after stripping all filler
        val stripped = withoutNumber
            .replace(Regex("\\b(save|add|store|keep|put|this|a|the|my|to|for|as|number|phone|contact|contacts|please|can|you|hey|haven)\\b"), " ")
            .trim()
            .split(Regex("\\s+")).filter { it.isNotBlank() }
        return stripped.joinToString(" ").replaceFirstChar { it.uppercase() }
    }

    private fun silentSaveContact(name: String, phoneNumber: String) {
        val displayName = name.split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { c -> c.uppercase() }
        }
        // Use ACTION_INSERT intent — no WRITE_CONTACTS permission needed,
        // user sees the pre-filled contact and confirms with one tap
        val intent = Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI).apply {
            putExtra(ContactsContract.Intents.Insert.NAME, displayName)
            putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
            val msg = "Opening contacts for $displayName — just tap Save to confirm."
            addBubble(msg, isUser = false)
            speakWithGTTS(msg)
        } catch (e: Exception) {
            val msg = "I couldn't open the contacts app. Try saving it manually."
            addBubble(msg, isUser = false)
            speakWithGTTS(msg)
        }
        micButton.text = "Speak to Haven"
        micButton.isEnabled = true
        pendingSaveContactName = null
        pendingSaveContactNumber = null
    }

    private fun findMatchingContacts(name: String): List<Pair<String, String>> {
        val results = mutableListOf<Pair<String, String>>()
        val filterUri = Uri.withAppendedPath(
            ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI,
            Uri.encode(name.trim())
        )
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        contentResolver.query(filterUri, projection, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val seen = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                val cName = cursor.getString(nameIdx) ?: continue
                val cNum = cursor.getString(numIdx) ?: continue
                if (seen.add("$cName|$cNum")) results.add(cName to cNum)
            }
        }
        return results
    }

    private fun callContactByName(name: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            pendingCallName = name
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.READ_CONTACTS), READ_CONTACTS_REQUEST_CODE
            )
            val msg = "I need permission to look through your contacts. Please allow it, then ask again."
            addBubble(msg, isUser = false)
            speakWithGTTS(msg)
            return
        }
        addBubble("[DEBUG] searching for: '" + name + "'", isUser = false)
        // Try the spoken name first, then common phonetic variants if no match
        var matches = findMatchingContacts(name)
        if (matches.isEmpty()) {
            val variants = mutableListOf<String>()
            val n = name.lowercase()
            if (n.endsWith("ck")) variants.add(name.dropLast(2) + "ch")
            if (n.endsWith("ch")) variants.add(name.dropLast(2) + "ck")
            if (n.endsWith("k")) variants.add(name.dropLast(1) + "ch")
            if (n.endsWith("c")) variants.add(name.dropLast(1) + "k")
            for (v in variants) {
                matches = findMatchingContacts(v)
                if (matches.isNotEmpty()) break
            }
        }
        val msg = when {
            matches.isEmpty() -> "I couldn't find $name in your contacts."
            matches.size == 1 -> {
                val (cName, number) = matches[0]
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try { startActivity(intent) } catch (e: Exception) {}
                "Opening the dialer for $cName. Tap call to connect."
            }
            else -> {
                val names = matches.map { it.first }.distinct()
                "I found a few matches: ${names.joinToString(", ")}. Try saying the full name."
            }
        }
        addBubble(msg, isUser = false)
        speakWithGTTS(msg)
        micButton.text = "Speak to Haven"
        micButton.isEnabled = true
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

    private fun endWorkflow() {
        conversationMode = false
        pendingWorkflow = null
        workflowContactName = null
        workflowContactNumber = null
        workflowSmsMessage = null
        micButton.text = "Speak to Haven"
        micButton.isEnabled = true
    }

    private fun say(msg: String) {
        addBubble(msg, isUser = false)
        speakWithGTTS(msg)
    }

    private fun handleLocalCommand(text: String): Boolean {
        val lower = text.lowercase()

        if (lower.contains("never mind") || lower.contains("cancel") || lower == "stop" || lower == "forget it") {
            if (conversationMode) {
                endWorkflow()
                say("Okay, never mind.")
                return true
            }
        }

        if (conversationMode && pendingWorkflow != null) {
            return continueWorkflow(text, lower)
        }

        if (lower.contains("call") || lower.contains("dial")) {
            val triggerWords = listOf("call", "dial")
            var name = lower
            for (trigger in triggerWords) {
                if (lower.contains(trigger)) {
                    name = lower.substringAfter(trigger).trim()
                    break
                }
            }
            for (filler in listOf("my ", "a ", "the ")) {
                if (name.startsWith(filler)) name = name.removePrefix(filler).trim()
            }
            if (name.isNotBlank()) {
                val matches = findMatchingContacts(name)
                when {
                    matches.size == 1 -> {
                        val (cName, number) = matches[0]
                        say("Calling $cName now.")
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try { startActivity(intent) } catch (e: Exception) {}
                        return true
                    }
                    matches.size > 1 -> {
                        val names = matches.map { it.first }.distinct()
                        workflowContactName = name
                        pendingWorkflow = "CALL_CLARIFY"
                        conversationMode = true
                        say("I found a few: ${names.joinToString(", ")}. Which one?")
                        return true
                    }
                    else -> {
                        workflowContactName = name
                        pendingWorkflow = "CALL_NOT_FOUND"
                        conversationMode = true
                        say("I don't see $name in your contacts. Do you want to try a different spelling, or say the number instead?")
                        return true
                    }
                }
            } else {
                pendingWorkflow = "CALL_WHO"
                conversationMode = true
                say("Who would you like to call?")
                return true
            }
        }

        if (lower.contains("save") && (lower.contains("contact") || lower.contains("number"))) {
            val phoneNumber = extractPhoneNumber(text)
            val name = if (phoneNumber != null) extractContactName(lower, phoneNumber) else ""
            when {
                phoneNumber != null && name.isNotBlank() -> {
                    workflowContactName = name
                    workflowContactNumber = phoneNumber
                    pendingWorkflow = "SAVE_CONFIRM"
                    conversationMode = true
                    say("Got it. Should I save $name at $phoneNumber?")
                }
                phoneNumber != null -> {
                    workflowContactNumber = phoneNumber
                    pendingWorkflow = "SAVE_NAME"
                    conversationMode = true
                    say("I have the number $phoneNumber. What name should I save it under?")
                }
                name.isNotBlank() -> {
                    workflowContactName = name
                    pendingWorkflow = "SAVE_NUMBER"
                    conversationMode = true
                    say("What's $name's phone number?")
                }
                else -> {
                    pendingWorkflow = "SAVE_NAME"
                    conversationMode = true
                    say("Sure. What's the name for this contact?")
                }
            }
            return true
        }

        if (lower.contains("text") || lower.contains("send a message") || lower.contains("send message")) {
            val triggerWords = listOf("send a message to", "send message to", "text", "message")
            var name = lower
            for (trigger in triggerWords) {
                if (lower.contains(trigger)) {
                    name = lower.substringAfter(trigger).trim()
                    break
                }
            }
            for (filler in listOf("my ", "a ", "the ", "to ")) {
                if (name.startsWith(filler)) name = name.removePrefix(filler).trim()
            }
            if (name.isNotBlank()) {
                workflowContactName = name
                pendingWorkflow = "SMS_MSG"
                conversationMode = true
                say("What would you like to say to $name?")
            } else {
                pendingWorkflow = "SMS_WHO"
                conversationMode = true
                say("Who would you like to text?")
            }
            return true
        }

        if (lower.contains("set an alarm") || lower.contains("wake me") || lower.contains("alarm for") || lower.contains("remind me")) {
            pendingWorkflow = "ALARM"
            conversationMode = true
            say("What time should I set the alarm for?")
            return true
        }

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
                val msg = if (found) "Opening $appQuery." else "I couldn't find $appQuery on your phone."
                say(msg)
                return true
            }
        }

        if (lower.contains("go home") || lower.contains("home screen")) {
            HavenAccessibilityService.performHome()
            say("Going home.")
            return true
        }

        if (lower.contains("go back")) {
            HavenAccessibilityService.performBack()
            say("Going back.")
            return true
        }

        return false
    }

    private fun continueWorkflow(text: String, lower: String): Boolean {
        when (pendingWorkflow) {
            "CALL_WHO" -> {
                var name = lower
                for (filler in listOf("my ", "a ", "the ", "call ")) {
                    if (name.startsWith(filler)) name = name.removePrefix(filler).trim()
                }
                val matches = findMatchingContacts(name)
                when {
                    matches.size == 1 -> {
                        val (cName, number) = matches[0]
                        endWorkflow()
                        say("Calling $cName now.")
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        try { startActivity(intent) } catch (e: Exception) {}
                    }
                    matches.size > 1 -> {
                        val names = matches.map { it.first }.distinct()
                        workflowContactName = name
                        pendingWorkflow = "CALL_CLARIFY"
                        say("I found ${names.joinToString(", ")}. Which one?")
                    }
                    else -> {
                        pendingWorkflow = "CALL_NUMBER"
                        workflowContactName = name
                        say("I don't see $name in your contacts. Do you know their number?")
                    }
                }
                return true
            }
            "CALL_CLARIFY" -> {
                val matches = findMatchingContacts(text)
                if (matches.isNotEmpty()) {
                    val (cName, number) = matches[0]
                    endWorkflow()
                    say("Calling $cName now.")
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    try { startActivity(intent) } catch (e: Exception) {}
                } else {
                    endWorkflow()
                    say("I'm sorry, I still couldn't find them. Try your contacts app directly.")
                }
                return true
            }
            "CALL_NOT_FOUND" -> {
                val phoneNumber = extractPhoneNumber(text)
                if (phoneNumber != null) {
                    endWorkflow()
                    say("Dialing $phoneNumber now.")
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    try { startActivity(intent) } catch (e: Exception) {}
                } else {
                    val matches = findMatchingContacts(text)
                    if (matches.size == 1) {
                        val (cName, number) = matches[0]
                        endWorkflow()
                        say("Found them — calling $cName now.")
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        try { startActivity(intent) } catch (e: Exception) {}
                    } else {
                        endWorkflow()
                        say("I wasn't able to find that contact. Try your contacts app directly.")
                    }
                }
                return true
            }
            "CALL_NUMBER" -> {
                val phoneNumber = extractPhoneNumber(text)
                if (phoneNumber != null) {
                    endWorkflow()
                    say("Dialing $phoneNumber for ${workflowContactName ?: "them"} now.")
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    try { startActivity(intent) } catch (e: Exception) {}
                } else {
                    endWorkflow()
                    say("I didn't catch a number. Try again when you're ready.")
                }
                return true
            }
            "SAVE_NAME" -> {
                val name = text.trim()
                if (name.isNotBlank()) {
                    workflowContactName = name
                    pendingWorkflow = if (workflowContactNumber != null) "SAVE_CONFIRM" else "SAVE_NUMBER"
                    if (workflowContactNumber != null) {
                        say("Should I save $name at ${workflowContactNumber}?")
                    } else {
                        say("What's $name's phone number?")
                    }
                } else {
                    say("I didn't catch the name. What should I call this contact?")
                }
                return true
            }
            "SAVE_NUMBER" -> {
                val phoneNumber = extractPhoneNumber(text)
                if (phoneNumber != null) {
                    workflowContactNumber = phoneNumber
                    pendingWorkflow = "SAVE_CONFIRM"
                    say("Should I save ${workflowContactName} at $phoneNumber?")
                } else {
                    say("I didn't catch a number. Please say it clearly, like 5 5 5 1 2 3 4 5 6 7.")
                }
                return true
            }
            "SAVE_CONFIRM" -> {
                if (lower.contains("yes") || lower.contains("yeah") || lower.contains("yep") || lower.contains("absolutely") || lower.contains("sure") || lower.contains("correct") || lower.contains("right") || lower.contains("save it") || lower.contains("do it") || lower.contains("go ahead")) {
                    val name = workflowContactName ?: ""
                    val number = workflowContactNumber ?: ""
                    endWorkflow()
                    if (name.isNotBlank() && number.isNotBlank()) {
                        silentSaveContact(name, number)
                    } else {
                        say("Something went wrong. Let's try again.")
                    }
                } else if (lower.contains("no") || lower.contains("nope") || lower.contains("wrong")) {
                    pendingWorkflow = "SAVE_NAME"
                    workflowContactName = null
                    workflowContactNumber = null
                    say("My mistake. What's the correct name?")
                } else {
                    say("Just say yes to save, or no to start over.")
                }
                return true
            }
            "SMS_WHO" -> {
                var name = lower
                for (filler in listOf("my ", "a ", "the ", "to ")) {
                    if (name.startsWith(filler)) name = name.removePrefix(filler).trim()
                }
                workflowContactName = name
                pendingWorkflow = "SMS_MSG"
                say("What would you like to say to $name?")
                return true
            }
            "SMS_MSG" -> {
                workflowSmsMessage = text
                pendingWorkflow = "SMS_CONFIRM"
                say("I'll send to ${workflowContactName}: \"${workflowSmsMessage}\". Should I send it?")
                return true
            }
            "SMS_CONFIRM" -> {
                if (lower.contains("yes") || lower.contains("yeah") || lower.contains("yep") || lower.contains("absolutely") || lower.contains("sure") || lower.contains("go ahead") || lower.contains("send it") || lower.contains("send")) {
                    val name = workflowContactName ?: ""
                    val message = workflowSmsMessage ?: ""
                    val matches = findMatchingContacts(name)
                    endWorkflow()
                    if (matches.isNotEmpty()) {
                        val number = matches[0].second
                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).apply {
                            putExtra("sms_body", message)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try { startActivity(intent) } catch (e: Exception) {}
                        say("Opening messages for $name. Tap send when ready.")
                    } else {
                        say("I couldn't find $name in your contacts.")
                    }
                } else {
                    endWorkflow()
                    say("Okay, I won't send it.")
                }
                return true
            }
            "ALARM" -> {
                val intent = Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    val timeRegex = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?", RegexOption.IGNORE_CASE)
                    val match = timeRegex.find(lower)
                    if (match != null) {
                        var hour = match.groupValues[1].toIntOrNull() ?: 0
                        val minute = match.groupValues[2].toIntOrNull() ?: 0
                        val ampm = match.groupValues[3].lowercase()
                        if (ampm == "pm" && hour < 12) hour += 12
                        if (ampm == "am" && hour == 12) hour = 0
                        putExtra(android.provider.AlarmClock.EXTRA_HOUR, hour)
                        putExtra(android.provider.AlarmClock.EXTRA_MINUTES, minute)
                        putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)
                    }
                }
                endWorkflow()
                try {
                    startActivity(intent)
                    say("Alarm set.")
                } catch (e: Exception) {
                    say("I couldn't set the alarm. Try opening your clock app.")
                }
                return true
            }
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
                    micButton.text = "Tap to interrupt"
                    micButton.isEnabled = true
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

    private fun splitIntoChunks(text: String): MutableList<String> {
        val sentences = text.split(Regex("(?<=[.!?])\\s+")).map { it.trim() }.filter { it.isNotBlank() }
        return if (sentences.isEmpty()) mutableListOf(text) else sentences.toMutableList()
    }

    private fun speakWithGTTS(text: String) {
        isSpeaking = true
        speechQueue = splitIntoChunks(text)
        playNextChunk()
    }

    private fun playNextChunk() {
        if (!isSpeaking || speechQueue.isEmpty()) {
            if (isSpeaking) onSpeakDone()
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
                if (audioBytes == null || audioBytes.size < 1000) {
                    withContext(Dispatchers.Main) { playNextChunk() }
                    return@launch
                }
                val tempFile = File(cacheDir, "haven_tts_${System.currentTimeMillis()}.mp3")
                FileOutputStream(tempFile).use { it.write(audioBytes) }
                withContext(Dispatchers.Main) {
                    if (!isSpeaking) {
                        tempFile.delete()
                        return@withContext
                    }
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
                                tempFile.delete()
                                playNextChunk()
                            }
                            setOnErrorListener { _, _, _ ->
                                release()
                                mediaPlayer = null
                                playNextChunk()
                                true
                            }
                            prepareAsync()
                        }
                    } catch (e: Exception) {
                        playNextChunk()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { playNextChunk() }
            }
        }
    }

    private fun onSpeakDone() {
        isSpeaking = false
        statusText.text = "DexOS · ReasonFlow active · memory synced"
        if (conversationMode) {
            micButton.text = "Listening..."
            micButton.isEnabled = true
            startListening()
        } else {
            micButton.text = "Speak to Haven"
            micButton.isEnabled = true
        }
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        speechRecognizer.destroy()
        super.onDestroy()
    }
}
