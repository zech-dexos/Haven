package com.dexos.haven

import androidx.room.*

@Dao
interface VoiceProfileDao {

    @Query("SELECT * FROM voice_profiles WHERE userId = :userId")
    suspend fun getProfile(userId: String): VoiceProfile?

    @Query("SELECT * FROM voice_profiles")
    suspend fun getAllProfiles(): List<VoiceProfile>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: VoiceProfile)

    @Delete
    suspend fun delete(profile: VoiceProfile)

    @Query("UPDATE voice_profiles SET lastSeen = :timestamp WHERE userId = :userId")
    suspend fun updateLastSeen(userId: String, timestamp: Long)
}
