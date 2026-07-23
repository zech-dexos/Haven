package com.dexos.haven

// Single shared source of truth for whether Kalimi is currently busy,
// checked by BOTH MainActivity and HavenListeningService before starting
// anything -- this is what prevents two instances of her talking at once.
object HavenState {
    @Volatile var isListening: Boolean = false
    @Volatile var isSpeaking: Boolean = false

    fun isBusy(): Boolean = isListening || isSpeaking
}
