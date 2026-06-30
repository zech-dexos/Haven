package com.dexos.haven

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import android.util.Log

class HavenActionExecutor {

    fun executeDeviceAction(context: Context, jsonResponseString: String) {
        try {
            val rootJson = JSONObject(jsonResponseString)
            if (rootJson.isNull("device_action")) {
                Log.d("HavenAction", "No device action required.")
                return
            }

            val actionObj = rootJson.getJSONObject("device_action")
            val actionType = actionObj.optString("type")

            when (actionType) {
                "OPEN_OR_DOWNLOAD_APP" -> {
                    val packageName = actionObj.optString("package")
                    if (packageName.isNotEmpty()) {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(launchIntent)
                            Log.d("HavenAction", "Successfully launched app: $packageName")
                        } else {
                            Log.d("HavenAction", "App missing. Redirecting to Play Store: $packageName")
                            try {
                                val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
                                playStoreIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(playStoreIntent)
                            } catch (e: Exception) {
                                val webStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com"))
                                webStoreIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(webStoreIntent)
                            }
                        }
                    }
                }

                "SEARCH_EMAILS" -> {
                    Log.d("HavenAction", "Opening email application interface...")
                    val emailIntent = Intent(Intent.ACTION_MAIN)
                    emailIntent.addCategory(Intent.CATEGORY_APP_EMAIL)
                    emailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(emailIntent)
                    } catch (e: Exception) {
                        val searchIntent = Intent(Intent.ACTION_WEB_SEARCH)
                        context.startActivity(searchIntent)
                    }
                }
                
                else -> {
                    Log.w("HavenAction", "Unknown action type: $actionType")
                }
            }

        } catch (e: Exception) {
            Log.e("HavenAction", "Error running device command", e)
        }
    }
}
