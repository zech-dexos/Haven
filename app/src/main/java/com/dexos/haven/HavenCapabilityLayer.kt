package com.dexos.haven

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import android.telecom.TelecomManager
import android.util.Log
import android.app.DownloadManager
import android.media.AudioManager
import android.os.Build

/**
 * HavenCapabilityLayer
 *
 * Haven's hands. Every action an elder might ask a family member to do.
 * Intent is detected locally. Execution is immediate. No cloud needed for device actions.
 *
 * Returns a HavenAction that MainActivity uses to:
 *   1. Execute the action locally
 *   2. Tell the backend what happened (so Haven speaks naturally about it)
 */

data class HavenAction(
    val type: String,           // CALL, SMS, OPEN_APP, INSTALL_APP, VOLUME, etc.
    val handled: Boolean,       // true = we handled it locally, don't wait for backend
    val spokenResponse: String, // what Haven says
    val packageName: String = "",
    val contactName: String = "",
    val phoneNumber: String = "",
    val extras: Map<String, String> = emptyMap()
)

class HavenCapabilityLayer(private val context: Context) {

    companion object {
        private const val TAG = "HavenCapability"

        // App package map — covers typos and common names
        val APP_PACKAGES = mapOf(
            "solitaire" to "com.microsoft.solitairecollection",
            "solatair" to "com.microsoft.solitairecollection",
            "solitare" to "com.microsoft.solitairecollection",
            "solataire" to "com.microsoft.solitairecollection",
            "spotify" to "com.spotify.music",
            "youtube music" to "com.google.android.apps.youtube.music",
            "youtube" to "com.google.android.youtube",
            "netflix" to "com.netflix.mediaclient",
            "facebook" to "com.facebook.katana",
            "google maps" to "com.google.android.apps.maps",
            "maps" to "com.google.android.apps.maps",
            "photos" to "com.google.android.apps.photos",
            "clock" to "com.google.android.deskclock",
            "calculator" to "com.google.android.calculator",
            "settings" to "com.android.settings",
            "play store" to "com.android.vending",
            "google play" to "com.android.vending",
            "playstore" to "com.android.vending",
            "messages" to "com.google.android.apps.messaging",
            "contacts" to "com.google.android.contacts",
            "gmail" to "com.google.android.gm",
            "chrome" to "com.android.chrome",
            "pandora" to "com.pandora.android",
            "amazon music" to "com.amazon.mp3",
            "audible" to "com.audible.application",
            "camera" to "com.android.camera2",
            "tiktok" to "com.zhiliaoapp.musically",
            "candy crush" to "com.king.candycrushsaga",
            "words with friends" to "com.zynga.words",
            "weather" to "com.google.android.apps.weatherfrog",
            "news" to "com.google.android.apps.magazines",
        )

        // Emergency numbers
        val EMERGENCY_NUMBERS = setOf("911", "112", "999")
    }

    /**
     * Main entry point. Call this with everything the user said.
     * Returns null if Haven should handle it via the backend (general conversation).
     */
    fun process(userText: String): HavenAction? {
        val t = userText.lowercase().trim()

        // EMERGENCY — always first, always local
        if (isEmergency(t)) return handleEmergency(t)

        // CALL
        if (isCall(t)) return handleCall(t)

        // SMS
        if (isSms(t)) return handleSms(t, userText)

        // Play Store / App Store
        if (isPlayStore(t)) return openApp("com.android.vending", "Opening Google Play for you.")

        // Camera
        if (isCamera(t)) return openApp("com.android.camera2", "Opening your camera.")

        // Open or install specific app
        val appPackage = detectAppPackage(t)
        if (appPackage != null) {
            return if (isInstall(t)) {
                installApp(appPackage, t)
            } else {
                openApp(appPackage, "Opening that for you right now.")
            }
        }

        // Generic open/install with no known package — let backend handle with search
        if (isInstall(t)) {
            return openApp("com.android.vending",
                "Let me open the Play Store so you can find that.")
        }

        // Volume
        if (isVolume(t)) return handleVolume(t)

        // Flashlight
        if (t.contains("flashlight") || t.contains("torch")) return handleFlashlight(t)

        // Alarm
        if (isAlarm(t)) return openApp("com.google.android.deskclock",
            "Opening your clock so we can set that alarm.")

        // Settings
        if (t.contains("setting") || t.contains("wifi") || t.contains("wi-fi") ||
            t.contains("bluetooth")) return handleSettings(t)

        // Text size
        if (isTextSize(t)) return handleTextSize()

        // Maps / Navigation
        if (isMaps(t)) return handleMaps(t, userText)

        // Read messages — open messages app
        if (isReadMessages(t)) return openApp("com.google.android.apps.messaging",
            "Opening your messages for you.")

        // Save contact
        if (isSaveContact(t)) return handleSaveContact(t, userText)

        // Not a local capability — let the backend handle it
        return null
    }

    // ---------------------------------------------------------------------------
    // Intent detection
    // ---------------------------------------------------------------------------

    private fun isEmergency(t: String) =
        t.contains("911") || t.contains("emergency") || t.contains("i fell") ||
        t.contains("help me") || t.contains("i need help") || t.contains("chest pain") ||
        t.contains("can't breathe") || t.contains("cant breathe") || t.contains("i'm hurt") ||
        t.contains("i am hurt") || t.contains("call for help")

    private fun isCall(t: String) =
        (t.contains("call") || t.contains("phone") || t.contains("dial") || t.contains("ring")) &&
        !t.contains("911")

    private fun isSms(t: String) =
        (t.contains("text") && !t.contains("text bigger") && !t.contains("text size") &&
         !t.contains("text larger")) ||
        (t.contains("send") && t.contains("message")) ||
        t.contains(" sms ")

    private fun isPlayStore(t: String) =
        t.contains("google play") || t.contains("play store") || t.contains("playstore") ||
        t.contains("googl play") || t.contains("app store") ||
        (t.contains("open") && t.contains("play"))

    private fun isCamera(t: String) =
        t.contains("camera") || t.contains("take a photo") || t.contains("take a picture") ||
        t.contains("selfie")

    private fun isInstall(t: String) =
        t.contains("get me") || t.contains("download") || t.contains("install") ||
        t.contains("get a") || t.contains("find me") || (t.contains("get") && t.contains("app")) ||
        (t.contains("get") && t.contains("game"))

    private fun isVolume(t: String) =
        t.contains("volume") || t.contains("louder") || t.contains("quieter") ||
        t.contains("too loud") || t.contains("too quiet") || t.contains("turn up") ||
        t.contains("turn down") || t.contains("mute") || t.contains("unmute")

    private fun isAlarm(t: String) =
        t.contains("alarm") || t.contains("wake me") || t.contains("remind me") ||
        t.contains("set a reminder")

    private fun isTextSize(t: String) =
        t.contains("text bigger") || t.contains("text larger") || t.contains("font bigger") ||
        t.contains("font larger") || t.contains("make it bigger") || t.contains("can't read") ||
        t.contains("cant read") || t.contains("hard to read") || t.contains("text size")

    private fun isMaps(t: String) =
        t.contains("navigate") || t.contains("direction") || t.contains("how do i get to") ||
        t.contains("take me to") || t.contains("find") && t.contains("near")

    private fun isReadMessages(t: String) =
        t.contains("read my message") || t.contains("do i have") || t.contains("any message") ||
        t.contains("check my message") || t.contains("check my text") || t.contains("new message")

    private fun isSaveContact(t: String) =
        t.contains("save") && (t.contains("number") || t.contains("contact")) ||
        t.contains("add contact") || t.contains("new contact")

    private fun detectAppPackage(t: String): String? {
        for ((name, pkg) in APP_PACKAGES) {
            if (t.contains(name)) return pkg
        }
        return null
    }

    // ---------------------------------------------------------------------------
    // Action handlers
    // ---------------------------------------------------------------------------

    private fun handleEmergency(t: String): HavenAction {
        // Immediately call 911 or emergency contact
        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:911")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Emergency call failed", e)
            // Try dial instead
            try {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:911")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "Emergency dial failed", e2)
            }
        }
        return HavenAction(
            type = "EMERGENCY",
            handled = true,
            spokenResponse = "I'm calling 911 right now. Help is on the way. Stay calm, I'm right here with you."
        )
    }

    private fun handleCall(t: String): HavenAction {
        // Extract contact name from text
        val contactName = extractContactName(t, "call")
        val phoneNumber = extractPhoneNumber(t)

        return if (phoneNumber != null) {
            // Direct number dial
            dialNumber(phoneNumber)
            HavenAction(
                type = "CALL",
                handled = true,
                spokenResponse = "Calling $phoneNumber for you right now.",
                phoneNumber = phoneNumber
            )
        } else if (contactName != null) {
            // Look up contact and call
            val number = lookupContact(contactName)
            if (number != null) {
                dialNumber(number)
                HavenAction(
                    type = "CALL",
                    handled = true,
                    spokenResponse = "Calling $contactName for you right now.",
                    contactName = contactName,
                    phoneNumber = number
                )
            } else {
                // Open dialer with search
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                HavenAction(
                    type = "CALL",
                    handled = true,
                    spokenResponse = "I couldn't find $contactName in your contacts. Opening your phone for you.",
                    contactName = contactName
                )
            }
        } else {
            // Open dialer
            val intent = Intent(Intent.ACTION_DIAL).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            HavenAction(
                type = "CALL",
                handled = true,
                spokenResponse = "Opening your phone for you. Who would you like to call?"
            )
        }
    }

    private fun handleSms(t: String, original: String): HavenAction {
        val contactName = extractContactName(t, "text") ?: extractContactName(t, "message")
        val number = if (contactName != null) lookupContact(contactName) else null

        return if (number != null) {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$number")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            HavenAction(
                type = "SMS",
                handled = true,
                spokenResponse = "Opening a message to $contactName for you.",
                contactName = contactName ?: "",
                phoneNumber = number
            )
        } else {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            HavenAction(
                type = "SMS",
                handled = true,
                spokenResponse = "Opening your messages so you can send a text."
            )
        }
    }

    private fun openApp(packageName: String, speech: String): HavenAction {
        val launched = launchPackage(packageName)
        return if (launched) {
            HavenAction(type = "OPEN_APP", handled = true,
                spokenResponse = speech, packageName = packageName)
        } else {
            // Not installed — go to Play Store
            launchPlayStorePage(packageName)
            HavenAction(type = "INSTALL_APP", handled = true,
                spokenResponse = "That app isn't on your phone yet. Opening the Play Store so you can get it.",
                packageName = packageName)
        }
    }

    private fun installApp(packageName: String, t: String): HavenAction {
        val launched = launchPackage(packageName)
        return if (launched) {
            HavenAction(type = "OPEN_APP", handled = true,
                spokenResponse = "You already have that! Opening it for you right now.",
                packageName = packageName)
        } else {
            launchPlayStorePage(packageName)
            HavenAction(type = "INSTALL_APP", handled = true,
                spokenResponse = "Opening the Play Store so you can download that.",
                packageName = packageName)
        }
    }

    private fun handleVolume(t: String): HavenAction {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        return when {
            t.contains("mute") -> {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                HavenAction(type = "VOLUME", handled = true, spokenResponse = "I've muted the sound for you.")
            }
            t.contains("louder") || t.contains("turn up") || t.contains("too quiet") -> {
                val newVol = minOf(current + 2, max)
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                HavenAction(type = "VOLUME", handled = true, spokenResponse = "I've turned the volume up for you.")
            }
            t.contains("quieter") || t.contains("turn down") || t.contains("too loud") -> {
                val newVol = maxOf(current - 2, 0)
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                HavenAction(type = "VOLUME", handled = true, spokenResponse = "I've turned the volume down for you.")
            }
            else -> {
                HavenAction(type = "VOLUME", handled = false,
                    spokenResponse = "Would you like me to turn the volume up or down?")
            }
        }
    }

    private fun handleFlashlight(t: String): HavenAction {
        // Open settings as fallback — camera flashlight requires CameraManager
        val intent = Intent(Settings.ACTION_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return HavenAction(type = "FLASHLIGHT", handled = true,
            spokenResponse = "Let me help you with the flashlight. You can find it in your quick settings by swiping down from the top of your screen.")
    }

    private fun handleSettings(t: String): HavenAction {
        val intent = when {
            t.contains("wifi") || t.contains("wi-fi") ->
                Intent(Settings.ACTION_WIFI_SETTINGS)
            t.contains("bluetooth") ->
                Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            t.contains("sound") || t.contains("volume") ->
                Intent(Settings.ACTION_SOUND_SETTINGS)
            else ->
                Intent(Settings.ACTION_SETTINGS)
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
        return HavenAction(type = "SETTINGS", handled = true,
            spokenResponse = "Opening your settings for you.")
    }

    private fun handleTextSize(): HavenAction {
        val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return HavenAction(type = "TEXT_SIZE", handled = true,
            spokenResponse = "Opening display settings. You can make the text bigger from there. Look for Font Size.")
    }

    private fun handleMaps(t: String, original: String): HavenAction {
        // Extract destination
        val dest = Regex("(?:to|at|find)\\s+(.+)$", RegexOption.IGNORE_CASE)
            .find(original)?.groupValues?.get(1) ?: ""

        val uri = if (dest.isNotEmpty()) {
            Uri.parse("geo:0,0?q=${Uri.encode(dest)}")
        } else {
            Uri.parse("geo:0,0")
        }
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return HavenAction(type = "MAPS", handled = true,
            spokenResponse = if (dest.isNotEmpty()) "Getting directions to $dest for you."
                            else "Opening maps for you.")
    }

    private fun handleSaveContact(t: String, original: String): HavenAction {
        val number = extractPhoneNumber(t)
        val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            if (number != null) putExtra(ContactsContract.Intents.Insert.PHONE, number)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return HavenAction(type = "SAVE_CONTACT", handled = true,
            spokenResponse = "Opening your contacts so you can save that number.")
    }

    // ---------------------------------------------------------------------------
    // Utility functions
    // ---------------------------------------------------------------------------

    private fun launchPackage(packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else false
        } catch (e: Exception) {
            Log.e(TAG, "launchPackage failed: $packageName", e)
            false
        }
    }

    private fun launchPlayStorePage(packageName: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://details?id=$packageName")
                setPackage("com.android.vending")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "Play Store launch failed", e2)
            }
        }
    }

    private fun dialNumber(number: String) {
        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$number")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Call failed, trying dial", e)
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$number")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    private fun lookupContact(name: String): String? {
        return try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$name%"),
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    it.getString(it.getColumnIndexOrThrow(
                        ContactsContract.CommonDataKinds.Phone.NUMBER))
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Contact lookup failed", e)
            null
        }
    }

    private fun extractContactName(t: String, trigger: String): String? {
        val pattern = Regex("$trigger\\s+(?:my\\s+)?([a-z][a-z\\s]{1,30}?)(?:\\s+at|\\s+number|\\s+please|\$)")
        return pattern.find(t)?.groupValues?.get(1)?.trim()
            ?.takeIf { it.isNotEmpty() && it != "my" }
    }

    private fun extractPhoneNumber(t: String): String? {
        val match = Regex("\\b(\\d[\\d\\s\\-\\.\\(\\)]{6,14}\\d)\\b").find(t) ?: return null
        val digits = match.value.replace(Regex("[^\\d]"), "")
        return if (digits.length >= 7) digits else null
    }
}
