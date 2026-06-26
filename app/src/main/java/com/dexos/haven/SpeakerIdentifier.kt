package com.dexos.haven

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class IdentityResult {
    data class Known(val profile: VoiceProfile, val confidence: Float) : IdentityResult()
    data class Unknown(val bestScore: Float) : IdentityResult()
    object NoProfiles : IdentityResult()
}

class SpeakerIdentifier(context: Context) {

    private val profileManager = VoiceProfileManager(context)

    suspend fun identify(pcmData: ShortArray): IdentityResult {
        return withContext(Dispatchers.Default) {
            val embedding = VoiceAnalyzer.extractFeatureVector(pcmData)
            val (profile, confidence) = profileManager.identify(embedding)

            when {
                profile == null && confidence == 0f -> IdentityResult.NoProfiles
                profile == null -> IdentityResult.Unknown(confidence)
                else -> IdentityResult.Known(profile, confidence)
            }
        }
    }

    suspend fun enroll(userId: String, displayName: String, pcmData: ShortArray) {
        val embedding = VoiceAnalyzer.extractFeatureVector(pcmData)

        // Create profile if first time
        val existing = profileManager.getProfile(userId)
        if (existing == null) {
            profileManager.createProfile(userId, displayName)
        }

        // Update rolling voiceprint
        profileManager.updateEmbedding(userId, embedding)

        // Update analytics baseline
        val analysis = VoiceAnalyzer.analyze(pcmData, 0L)
        profileManager.updateBaseline(
            userId,
            analysis.pitch,
            analysis.speechRate,
            analysis.energy
        )
    }

    suspend fun buildContext(
        pcmData: ShortArray,
        identityResult: IdentityResult
    ): VoiceContext {
        return withContext(Dispatchers.Default) {
            val analysis = VoiceAnalyzer.analyze(pcmData, 0L)

            when (identityResult) {
                is IdentityResult.Known -> {
                    val profile = identityResult.profile
                    val (pitchDev, rateDev, energyDev) = profileManager.computeDeviation(
                        profile,
                        analysis.pitch,
                        analysis.speechRate,
                        analysis.energy
                    )
                    profileManager.buildVoiceContext(
                        profile,
                        identityResult.confidence,
                        pitchDev,
                        rateDev,
                        energyDev
                    )
                }
                is IdentityResult.Unknown -> {
                    profileManager.buildVoiceContext(
                        null, identityResult.bestScore,
                        0f, 0f, 0f
                    )
                }
                IdentityResult.NoProfiles -> {
                    profileManager.buildVoiceContext(
                        null, 0f, 0f, 0f, 0f
                    )
                }
            }
        }
    }
}
