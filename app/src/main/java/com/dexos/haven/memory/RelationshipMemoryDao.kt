package com.dexos.haven.memory

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface RelationshipMemoryDao {
    @Query("SELECT * FROM relationship_memories WHERE userId = :userId AND isActive = 1")
    suspend fun getActiveMemories(userId: String): List<RelationshipMemory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: RelationshipMemory)

    @Update
    suspend fun updateMemory(memory: RelationshipMemory)

    @Query("UPDATE relationship_memories SET isActive = 0 WHERE id = :id")
    suspend fun deactivateMemory(id: String)
}
