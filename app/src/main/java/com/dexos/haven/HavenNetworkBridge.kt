package com.dexos.haven

import android.content.Context
import org.json.JSONObject
import android.util.Log

class HavenNetworkBridge(private val context: Context) {

    private val actionExecutor = HavenActionExecutor()

    fun processIncomingResponse(rawServerResponse: String): String {
        return try {
            val rootJson = JSONObject(rawServerResponse)
            val spokenText = rootJson.optString("voice_response", "I am right here with you.")
            
            // Execute physical actions alongside speaking
            actionExecutor.executeDeviceAction(context, rawServerResponse)
            
            spokenText
        } catch (e: Exception) {
            Log.e("HavenBridge", "Failed to parse incoming engine payload", e)
            "I heard you, let me process that again for you."
        }
    }
}
