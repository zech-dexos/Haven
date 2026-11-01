package com.dexos.haven.memory

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RelationshipManager(private val context: Context) {
    private val layer: RelationshipLayer by lazy { RelationshipLayer(context) }

    suspend fun extractAndSaveMemories(userId: String, utterance: String) {
        layer.processUserUtterance(userId, utterance)
    }

    suspend fun getContextString(userId: String): String {
        return layer.buildSystemContext(userId)
    }
}
