package com.dexos.haven

data class VoiceContext(
    val userId: String,
    val displayName: String,
    val identityConfidence: Float,        // 0.0 - 1.0
    val isEnrolled: Boolean,
    val stressScore: Float = 0f,          // deviation from baseline
    val fatigueScore: Float = 0f,
    val confusionScore: Float = 0f,
    val overallDeviation: Float = 0f,
    val routingHint: RoutingHint = RoutingHint.NORMAL
)

enum class RoutingHint {
    LOCAL,          // simple, confident, stable voice
    NORMAL,         // default backend
    ESCALATE,       // voice deviation or low confidence
    ALERT_FAMILY    // sustained high deviation
}
