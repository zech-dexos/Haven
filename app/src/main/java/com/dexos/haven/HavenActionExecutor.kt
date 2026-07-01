package com.dexos.haven

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import android.util.Log
import android.app.DownloadManager

class HavenActionExecutor {

    fun executeDeviceAction(context: Context, jsonResponseString: String) {
        // Run asynchronously to prevent locking the UI / main execution thread
        Thread {
            try {
                // Parse the native uncorrupted response object directly to protect system casing
                val rootJson = JSONObject(jsonResponseString)
                
                // Extract voice context safely
                val voiceText = rootJson.optString("voice_response", "").lowercase().trim()
                
                // Read the device action block safely
                val deviceAction = if (rootJson.has("device_action") && !rootJson.isNull("device_action")) {
                    rootJson.getJSONObject("device_action")
                } else {
                    null
                }
                val actionType = deviceAction?.optString("type", "")?.lowercase() ?: ""

                // ROOT ARCHITECTURE: Case-Insensitive Wildcard Mappings
                val googlePlayVariations = listOf("google play", "googleplay", "play store", "playstore", "googl play", "googll play", "vending")
                val downloadVariations = listOf("download", "downloads", "downloaded", "my downloads", "open downloads")
                val solitaireVariations = listOf("solitaire", "solitare", "solatair", "solitaire game")

                // Map variations instantly to their corresponding systemic flags
                val matchesPlayStore = googlePlayVariations.any { voiceText.contains(it) || actionType.contains(it) }
                val matchesDownloads = downloadVariations.any { voiceText.contains(it) || actionType.contains(it) }
                val matchesSolitaire = solitaireVariations.any { voiceText.contains(it) }

                when {
                    // 1. Solitaire Intent Route
                    matchesSolitaire -> {
                        val packageName = "com.microsoft.solitairecollection"
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(launchIntent)
                        } else {
                            val playStoreIntent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("https://google.com")
                                setPackage("com.android.vending")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(playStoreIntent)
                        }
                    }

                    // 2. Google Play Store Intent Route
                    matchesPlayStore -> {
                        val packageName = "com.android.vending"
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(launchIntent)
                        } else {
                            val playStoreIntent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("https://google.com")
                                setPackage("com.android.vending")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(playStoreIntent)
                        }
                    }

                    // 3. Downloads Framework Intent Route
                    matchesDownloads -> {
                        Log.d("HavenAction", "Executing explicit document layer routing...")
                        val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // Unified Android fallback picker structure if download folder restrictions are active
                            val fallbackIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                type = "*/*"
                                addCategory(Intent.CATEGORY_OPENABLE)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(fallbackIntent)
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e("HavenAction", "Threaded variable engine execution error", e)
            }
        }.start()
    }
}
