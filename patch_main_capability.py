"""
Patch MainActivity.kt to wire HavenCapabilityLayer.
Tested in sandbox. Apply once.
"""
import sys

path = "app/src/main/java/com/dexos/haven/MainActivity.kt"
content = open(path).read()

# 1. Replace sendToHaven to run capability layer first
old = '''    private fun sendToHaven(userMessage: String) {
        // Fire capability layer on user's spoken intent before sending to backend
        HavenActionExecutor().processVoiceIntents(this, userMessage)
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
                val rawResponseStr = response.body?.string() ?: "{}"
                val json = JSONObject(rawResponseStr)

                // Extract voice response
                val responseText = if (json.has("voice_response")) 
                    json.optString("voice_response") 
                else 
                    json.optString("response", "I am right here with you.")

                // Execute device action if present
                if (json.has("device_action") && !json.isNull("device_action")) {
                    val action = json.getJSONObject("device_action")
                    val actionType = action.optString("type", "")
                    val packageName = action.optString("package", "")
                    val query = action.optString("query", "")

                    when (actionType) {
                        "OPEN_OR_DOWNLOAD_APP" -> {
                            if (packageName.isNotEmpty()) {
                                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                                if (launchIntent != null) {
                                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    startActivity(launchIntent)
                                } else {
                                    // App not installed — open Play Store to download it
                                    val storeIntent = Intent(Intent.ACTION_VIEW).apply {
                                        data = android.net.Uri.parse("market://details?id=$packageName")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    try { startActivity(storeIntent) } catch (e: Exception) {
                                        val webIntent = Intent(Intent.ACTION_VIEW).apply {
                                            data = android.net.Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        startActivity(webIntent)
                                    }
                                }
                            }
                        }
                        "CONTACT_INTENT" -> {
                            // Handled by existing contact system
                        }
                    }
                }

                conversationHistory.add(JSONObject().put("role", "assistant").put("content", responseText))'''

new = '''    private fun sendToHaven(userMessage: String) {
        // Capability layer — runs locally before hitting backend
        val capability = HavenCapabilityLayer(this)
        val action = capability.process(userMessage)

        if (action != null && action.handled) {
            // Local action executed — speak the response, skip backend call
            conversationHistory.add(JSONObject().put("role", "user").put("content", userMessage))
            conversationHistory.add(JSONObject().put("role", "assistant").put("content", action.spokenResponse))
            addBubble(action.spokenResponse, isUser = false)
            speakWithGTTS(action.spokenResponse)
            return
        }

        // No local action — send to Haven backend for conversation
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
                val rawResponseStr = response.body?.string() ?: "{}"
                val json = JSONObject(rawResponseStr)
                val responseText = if (json.has("voice_response"))
                    json.optString("voice_response")
                else
                    json.optString("response", "I am right here with you.")

                conversationHistory.add(JSONObject().put("role", "assistant").put("content", responseText))'''

assert 'HavenActionExecutor().processVoiceIntents' in content, "old sendToHaven not found"
content = content.replace(old, new)

open(path, "w").write(content)
print("Patched OK")
