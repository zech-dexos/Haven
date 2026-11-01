package com.dexos.haven

import com.dexos.haven.memory.RelationshipMemory
import com.dexos.haven.memory.RelationshipMemoryDao

class FakeMemoryDao : RelationshipMemoryDao {
    private val memories = mutableListOf<RelationshipMemory>()

    override suspend fun getActiveMemories(userId: String): List<RelationshipMemory> {
        return memories.filter { it.userId == userId && it.isActive }
    }

    override suspend fun insertMemory(memory: RelationshipMemory) {
        val existingIndex = memories.indexOfFirst { it.id == memory.id }
        if (existingIndex != -1) {
            memories[existingIndex] = memory
        } else {
            memories.add(memory)
        }
    }

    override suspend fun updateMemory(memory: RelationshipMemory) {
        val existingIndex = memories.indexOfFirst { it.id == memory.id }
        if (existingIndex != -1) {
            memories[existingIndex] = memory
        }
    }

    override suspend fun deactivateMemory(id: String) {
        val existingIndex = memories.indexOfFirst { it.id == id }
        if (existingIndex != -1) {
            memories[existingIndex] = memories[existingIndex].copy(isActive = false)
        }
    }
}
