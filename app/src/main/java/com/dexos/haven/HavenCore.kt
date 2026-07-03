package com.dexos.haven

import android.content.Context

/**
 * HavenCore — Orchestration spine only.
 * plan → execute → observe → retry → fallback
 * Nothing else. No new capabilities, no new executor logic.
 */
class HavenCore(
    private val context: Context,
    private val capability: HavenCapabilityLayer,
    private val executor: HavenActionExecutor
) {

    fun handle(
        input: String,
        onLocalSuccess: (spokenResponse: String) -> Unit,
        sendToBackend: (String) -> Unit
    ) {
        // STEP 1: first attempt
        val action = capability.process(input)
        if (action != null) {
            executor.executeDeviceAction(context, input)
            onLocalSuccess(action.spokenResponse)
            return
        }

        // STEP 2: refine and retry once
        val refined = refine(input)
        if (refined != input) {
            val retryAction = capability.process(refined)
            if (retryAction != null) {
                executor.executeDeviceAction(context, refined)
                onLocalSuccess(retryAction.spokenResponse)
                return
            }
        }

        // STEP 3: both failed — backend is last resort
        sendToBackend(input)
    }

    private fun refine(text: String): String {
        val t = text.lowercase().trim()
        return when {
            "my downloads" in t  -> "open downloads"
            "downloads" in t     -> "open downloads"
            "downloaded" in t    -> "open downloads"
            "my music" in t      -> "open spotify"
            "play a song" in t   -> "open spotify"
            "play music" in t    -> "open spotify"
            "solitare" in t      -> "solitaire"
            "solatair" in t      -> "solitaire"
            "app store" in t     -> "google play"
            else                 -> text
        }
    }
}
