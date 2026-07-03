content = open('app/src/main/java/com/dexos/haven/MainActivity.kt').read()

old = '''                val rawResponseStr = response.body?.string() ?: "{}"

                // Use the bridge to process device actions and extract the voice lines
                val responseText = if (JSONObject(rawResponseStr).has("voice_response")) JSONObject(rawResponseStr).optString("voice_response") else JSONObject(rawResponseStr).optString("response", "I am right here with you.")

                conversationHistory.add(JSONObject().put("role", "assistant").put("content", responseText))'''

new = '''                val rawResponseStr = response.body?.string() ?: "{}"
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

assert 'val rawResponseStr = response.body?.string() ?: "{}"' in content, "target not found"
content = content.replace(old, new)
open('app/src/main/java/com/dexos/haven/MainActivity.kt', 'w').write(content)
print("Patched OK")
