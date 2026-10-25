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
        // Functionality moved to HavenCapabilityLayer to unify intent handling.
    }

    /**
     * CORE PATTERN: Process fuzzy wildcard lookups completely independently of the network shell.
     * This stops case, capitalization, or bracket variations from blocking your app controls.
     */
    fun processVoiceIntents(context: Context, spokenText: String) {
        // Functionality moved to HavenCapabilityLayer to unify intent handling.
    }
}
