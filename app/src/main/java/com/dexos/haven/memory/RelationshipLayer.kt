package com.dexos.haven.memory

import android.content.Context
import com.dexos.haven.HavenDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class RelationshipLayer(private val memoryDao: RelationshipMemoryDao) {

    constructor(context: Context) : this(HavenDatabase.get(context).relationshipMemoryDao())

    suspend fun processUserUtterance(userId: String, utterance: String) {
        val extractions = RelationshipAnalyzer.analyze(utterance)

        withContext(Dispatchers.IO) {
            val activeMemories = memoryDao.getActiveMemories(userId)

            for (ext in extractions) {
                if (ext.category == "correction") {
                    val target = ext.content
                    if (target.isNotEmpty()) {
                        val match = activeMemories.find { it.content.contains(target, ignoreCase = true) }
                        if (match != null) {
                            memoryDao.deactivateMemory(match.id)
                        }
                    } else if (activeMemories.isNotEmpty()) {
                        val lastMemory = activeMemories.maxByOrNull { it.timestamp }
                        if (lastMemory != null) {
                            memoryDao.deactivateMemory(lastMemory.id)
                        }
                    }
                } else {
                    saveMemory(userId, ext.category, ext.content, ext.isCertain, utterance)
                }
            }
        }
    }

    private suspend fun saveMemory(userId: String, category: String, content: String, isCertain: Boolean, contextStr: String) {
        memoryDao.insertMemory(RelationshipMemory(
            id = UUID.randomUUID().toString(),
            userId = userId,
            category = category,
            content = content,
            isInferred = !isCertain,
            confidence = if (isCertain) 1.0f else 0.5f,
            sourceContext = contextStr,
            timestamp = System.currentTimeMillis(),
            isActive = true
        ))
    }

    suspend fun getActiveMemories(userId: String): List<RelationshipMemory> {
        return withContext(Dispatchers.IO) {
            memoryDao.getActiveMemories(userId)
        }
    }

    suspend fun buildSystemContext(userId: String): String {
        val memories = getActiveMemories(userId)
        if (memories.isEmpty()) return ""

        val sb = StringBuilder("Background Relationship Context:\n")
        val facts = memories.filter { it.category == "fact" }
        val preferences = memories.filter { it.category == "preference" }
        val topics = memories.filter { it.category == "open_topic" }
        val boundaries = memories.filter { it.category == "boundary" }

        if (facts.isNotEmpty()) {
            sb.append("- Facts: ${facts.joinToString(", ") { it.content }}\n")
        }
        if (preferences.isNotEmpty()) {
            sb.append("- Preferences: ${preferences.joinToString(", ") { it.content }}\n")
        }
        if (topics.isNotEmpty()) {
            sb.append("- Open topics to follow up on: ${topics.joinToString(", ") { it.content }}\n")
        }
        if (boundaries.isNotEmpty()) {
            sb.append("- BOUNDARIES (CRITICAL - DO NOT MENTION): ${boundaries.joinToString(", ") { it.content }}\n")
        }

        return sb.toString()
    }
}
