package com.dexos.haven.memory

data class Extraction(val category: String, val content: String, val isCertain: Boolean)

object RelationshipAnalyzer {
    // Analyze text and return candidate memories
    fun analyze(userText: String): List<Extraction> {
        val lower = userText.lowercase()
        val extractions = mutableListOf<Extraction>()

        // 3. Correction / Deletion intent (check first to avoid matching preferences)
        val forgetRegex = Regex("(?:forget that|delete memory|erase that|never mind about)(.*)")
        val forgetMatch = forgetRegex.find(lower)
        if (forgetMatch != null) {
            extractions.add(Extraction("correction", forgetMatch.groupValues[1].trim(), true))
            return extractions // Return early if it's a correction
        }

        // 1. Explicit Boundary
        val boundaryRegex = Regex("(?:never talk about|don't bring up|do not bring up|stop talking about) (.*)")
        boundaryRegex.find(lower)?.let {
            extractions.add(Extraction("boundary", it.groupValues[1].trim(), true))
        }

        // 2. Fact/Topic with uncertainty
        val granddaughterRegex = Regex("granddaughter is visiting")
        if (granddaughterRegex.containsMatchIn(lower)) {
            extractions.add(Extraction("open_topic", "granddaughter's visit", false))
        }

        val likeRegex = Regex("i (?:like|love|prefer) (.*)")
        likeRegex.find(lower)?.let {
            val matched = it.groupValues[1].trim()
            if (!matched.startsWith("to ")) {
                extractions.add(Extraction("preference", matched, true))
            }
        }

        return extractions
    }
}
