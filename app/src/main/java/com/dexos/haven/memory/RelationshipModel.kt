package com.dexos.haven.memory

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONObject

@Entity(tableName = "relationship_memories")
data class RelationshipMemory(
    @PrimaryKey val id: String,
    val userId: String,
    val category: String, // fact, preference, boundary, open_topic
    val content: String,
    val isInferred: Boolean,
    val confidence: Float,
    val sourceContext: String,
    val timestamp: Long,
    val isActive: Boolean = true
) {
    fun toJSONObject(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("category", category)
            put("content", content)
            put("is_inferred", isInferred)
        }
    }
}
