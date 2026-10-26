package com.dexos.haven

import android.content.Context
import org.json.JSONObject

import org.json.JSONArray

class RelationshipManager(private val context: Context) {
    fun loadModel(): JSONObject {
        val prefs = context.getSharedPreferences("haven_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString("relationship_model", "{}") ?: "{}"
        return try { JSONObject(json) } catch (e: Exception) { JSONObject() }
    }

    fun saveModel(model: JSONObject) {
        context.getSharedPreferences("haven_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("relationship_model", model.toString())
            .apply()
    }

    fun clearModel() {
        saveModel(JSONObject())
    }

    fun extractFactsFromMessage(message: String) {
        val currentModel = loadModel()
        val updatedModel = parseMemories(message, currentModel)
        saveModel(updatedModel)
    }

    companion object {
        fun parseMemories(message: String, currentModel: JSONObject): JSONObject {
            val lower = message.lowercase().trim()
            val facts = currentModel.optJSONArray("facts") ?: JSONArray()
            val boundaries = currentModel.optJSONArray("boundaries") ?: JSONArray()

            if (lower.startsWith("i like ")) {
                facts.put(lower.removePrefix("i like ").trim())
            } else if (lower.startsWith("don't talk about ")) {
                boundaries.put(lower.removePrefix("don't talk about ").trim())
            } else if (lower.startsWith("do not talk about ")) {
                boundaries.put(lower.removePrefix("do not talk about ").trim())
            }

            // Enforce bounds (max 50 items per array)
            val boundedFacts = JSONArray()
            val startFacts = maxOf(0, facts.length() - 50)
            for (i in startFacts until facts.length()) {
                boundedFacts.put(facts.getString(i))
            }

            val boundedBoundaries = JSONArray()
            val startBoundaries = maxOf(0, boundaries.length() - 50)
            for (i in startBoundaries until boundaries.length()) {
                boundedBoundaries.put(boundaries.getString(i))
            }

            currentModel.put("facts", boundedFacts)
            currentModel.put("boundaries", boundedBoundaries)
            return currentModel
        }
    }
}
