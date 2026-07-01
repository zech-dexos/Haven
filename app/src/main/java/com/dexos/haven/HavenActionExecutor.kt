package com.dexos.haven

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.app.DownloadManager

class HavenActionExecutor {

    /**
     * COMPATIBILITY ROUTER: Keeps HavenNetworkBridge from crashing the compiler.
     * Maps the old execution footprint straight into the new async wildcard pipeline.
     */
    fun executeDeviceAction(context: Context, jsonResponseString: String) {
        processVoiceIntents(context, jsonResponseString)
    }

    /**
     * CORE PATTERN: Process fuzzy wildcard lookups completely independently of the network shell.
     * This stops case, capitalization, or bracket variations from blocking your app controls.
     */
    fun processVoiceIntents(context: Context, spokenText: String) {
        Thread {
            try {
                val cleanText = spokenText.lowercase().trim()

                // Wildcard Arrays matching any transcription typo known
                val playStoreKeywords = listOf("google play", "googleplay", "play store", "playstore", "googl play", "googll play", "vending")
                val downloadKeywords = listOf("download", "downloads", "downloaded", "my downloads", "open downloads")
                val solitaireKeywords = listOf("solitaire", "solitare", "solatair", "solitaire game")

                val matchesPlayStore = playStoreKeywords.any { cleanText.contains(it) }
                val matchesDownloads = downloadKeywords.any { cleanText.contains(it) }
                val matchesSolitaire = solitaireKeywords.any { cleanText.contains(it) }

                when {
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

                    matchesDownloads -> {
                        val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
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
                Log.e("HavenAction", "Intent execution layer faulted", e)
            }
        }.start()
    }
}
