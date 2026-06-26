package com.dexos.haven

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "voice_profiles")
data class VoiceProfile(
    @PrimaryKey val userId: String,
    val displayName: String,
    val voiceEmbedding: FloatArray,       // speaker ID vector, rolling average
    val pitchMean: Float = 0f,            // baseline pitch
    val pitchStdDev: Float = 0f,
    val speechRateMean: Float = 0f,       // baseline words/sec
    val energyMean: Float = 0f,           // baseline volume
    val sampleCount: Int = 0,             // how many utterances trained on
    val lastSeen: Long = System.currentTimeMillis(),
    val enrollmentComplete: Boolean = false
)
