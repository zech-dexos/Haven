package com.dexos.haven

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

class VoiceProfileManager(context: Context) {

    private val dao = HavenDatabase.get(context).voiceProfileDao()

    // --- Identity ---

    suspend fun getProfile(userId: String): VoiceProfile? =
        withContext(Dispatchers.IO) { dao.getProfile(userId) }

    suspend fun getAllProfiles(): List<VoiceProfile> =
        withContext(Dispatchers.IO) { dao.getAllProfiles() }

    suspend fun createProfile(userId: String, displayName: String): VoiceProfile {
        val profile = VoiceProfile(
            userId = userId,
            displayName = displayName,
            voiceEmbedding = FloatArray(0),
            enrollmentComplete = false
        )
        withContext(Dispatchers.IO) { dao.upsert(profile) }
        return profile
    }

    // --- Enrollment: update rolling average embedding ---

    suspend fun updateEmbedding(userId: String, newEmbedding: FloatArray) {
        withContext(Dispatchers.IO) {
            val existing = dao.getProfile(userId) ?: return@withContext
            val updated = if (existing.voiceEmbedding.isEmpty()) {
                newEmbedding
            } else {
                rollingAverage(existing.voiceEmbedding, newEmbedding, existing.sampleCount)
            }
            val enrollmentComplete = existing.sampleCount + 1 >= 5 // 5 utterances to enroll
            dao.upsert(existing.copy(
                voiceEmbedding = updated,
                sampleCount = existing.sampleCount + 1,
                enrollmentComplete = enrollmentComplete,
                lastSeen = System.currentTimeMillis()
            ))
        }
    }

    // --- Baseline: update voice analytics baseline ---

    suspend fun updateBaseline(
        userId: String,
        pitch: Float,
        speechRate: Float,
        energy: Float
    ) {
        withContext(Dispatchers.IO) {
            val existing = dao.getProfile(userId) ?: return@withContext
            val n = existing.sampleCount.toFloat()
            // Welford-style running mean
            val newPitch = if (n == 0f) pitch else (existing.pitchMean * n + pitch) / (n + 1)
            val newRate = if (n == 0f) speechRate else (existing.speechRateMean * n + speechRate) / (n + 1)
            val newEnergy = if (n == 0f) energy else (existing.energyMean * n + energy) / (n + 1)
            dao.upsert(existing.copy(
                pitchMean = newPitch,
                speechRateMean = newRate,
                energyMean = newEnergy
            ))
        }
    }

    // --- Identification: find best matching profile ---

    suspend fun identify(embedding: FloatArray): Pair<VoiceProfile?, Float> {
        return withContext(Dispatchers.IO) {
            val profiles = dao.getAllProfiles().filter { it.enrollmentComplete }
            if (profiles.isEmpty()) return@withContext Pair(null, 0f)

            var bestProfile: VoiceProfile? = null
            var bestScore = -1f

            for (profile in profiles) {
                val score = cosineSimilarity(embedding, profile.voiceEmbedding)
                if (score > bestScore) {
                    bestScore = score
                    bestProfile = profile
                }
            }

            // Threshold: 0.75 = confident match
            if (bestScore >= 0.75f) Pair(bestProfile, bestScore)
            else Pair(null, bestScore)
        }
    }

    // --- Voice deviation: compare current analytics to baseline ---

    fun computeDeviation(
        profile: VoiceProfile,
        currentPitch: Float,
        currentRate: Float,
        currentEnergy: Float
    ): Triple<Float, Float, Float> {
        if (profile.sampleCount < 5) return Triple(0f, 0f, 0f)

        val pitchDev = safeDev(currentPitch, profile.pitchMean)
        val rateDev = safeDev(currentRate, profile.speechRateMean)
        val energyDev = safeDev(currentEnergy, profile.energyMean)

        return Triple(pitchDev, rateDev, energyDev)
    }

    fun buildVoiceContext(
        profile: VoiceProfile?,
        confidence: Float,
        pitchDev: Float,
        rateDev: Float,
        energyDev: Float
    ): VoiceContext {
        val userId = profile?.userId ?: "unknown"
        val name = profile?.displayName ?: "Friend"
        val enrolled = profile?.enrollmentComplete ?: false

        val overallDev = (pitchDev + rateDev + energyDev) / 3f

        // Stress = high pitch deviation + high energy
        val stress = (pitchDev * 0.5f + energyDev * 0.5f).coerceIn(0f, 1f)
        // Fatigue = low energy + slow rate
        val fatigue = ((1f - energyDev.coerceIn(0f, 1f)) * 0.5f + rateDev * 0.5f).coerceIn(0f, 1f)
        // Confusion = high rate deviation + low confidence
        val confusion = (rateDev * 0.6f + (1f - confidence) * 0.4f).coerceIn(0f, 1f)

        val routing = when {
            confidence < 0.6f -> RoutingHint.ESCALATE
            overallDev > 0.7f -> RoutingHint.ALERT_FAMILY
            overallDev > 0.4f -> RoutingHint.ESCALATE
            confidence > 0.85f && overallDev < 0.2f -> RoutingHint.LOCAL
            else -> RoutingHint.NORMAL
        }

        return VoiceContext(
            userId = userId,
            displayName = name,
            identityConfidence = confidence,
            isEnrolled = enrolled,
            stressScore = stress,
            fatigueScore = fatigue,
            confusionScore = confusion,
            overallDeviation = overallDev,
            routingHint = routing
        )
    }

    // --- Math ---

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0f; var normA = 0f; var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom == 0f) 0f else dot / denom
    }

    private fun rollingAverage(existing: FloatArray, new: FloatArray, n: Int): FloatArray {
        if (existing.size != new.size) return new
        return FloatArray(existing.size) { i ->
            (existing[i] * n + new[i]) / (n + 1)
        }
    }

    private fun safeDev(current: Float, baseline: Float): Float {
        if (baseline == 0f) return 0f
        return (kotlin.math.abs(current - baseline) / baseline).coerceIn(0f, 1f)
    }
}
