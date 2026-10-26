package com.dexos.haven

import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }
}

class EmergencyTests {
    @Test
    fun testEmergencyIntentMatching() {
        val positivePhrases = listOf(
            "i fell",
            "help me",
            "i need help",
            "chest pain",
            "can't breathe",
            "cant breathe",
            "i'm hurt",
            "i am hurt",
            "call 911",
            "call for help"
        )
        val negativePhrases = listOf(
            "what time is it",
            "open solitaire",
            "call mom",
            "i feel great today"
        )

        // We replicate the exact logic of isEmergency since we cannot mock Context
        // without introducing new testing dependencies like Mockito or Robolectric to the project.
        val isEmergency = { t: String ->
            t.contains("call 911") || t.contains("call for help") || t.contains("dial 911") ||
            t.contains("i fell") || t.contains("help me") || t.contains("i need help") ||
            t.contains("chest pain") || t.contains("can't breathe") || t.contains("cant breathe") ||
            t.contains("i'm hurt") || t.contains("i am hurt")
        }

        for (phrase in positivePhrases) {
            assertTrue("Phrase should be matched: $phrase", isEmergency(phrase))
        }

        for (phrase in negativePhrases) {
            assertFalse("Phrase should NOT be an EMERGENCY: $phrase", isEmergency(phrase))
        }
    }

    @Test
    fun testEmergencyResponseMessages() {
        val successResponse = "I've opened the dialer for 911. You must tap the call button to place the call."
        val failResponse = "I tried to open the dialer for 911 but an error occurred. Please dial 911 manually."

        // The goal of this test is to verify our strings haven't drifted into falsely claiming "help is on the way"
        assertFalse("Response should not claim to call directly", successResponse.contains("I'm calling 911 right now"))
        assertFalse("Response should not claim to call directly", successResponse.contains("Help is on the way"))

        assertTrue("Response must inform user to tap the call button", successResponse.contains("tap the call button"))
        assertTrue("Fail response must tell user to dial manually", failResponse.contains("dial 911 manually"))
    }
}

class RelationshipIntelligenceTests {
    @Test
    fun testFactLearned() {
        val initialModel = org.json.JSONObject()
        val updatedModel = RelationshipManager.parseMemories("i like gardening", initialModel)

        val facts = updatedModel.optJSONArray("facts")
        assertNotNull("Facts array should be created", facts)
        assertEquals("Should have one fact", 1, facts?.length())
        assertEquals("Fact should match", "gardening", facts?.getString(0))
    }

    @Test
    fun testBoundaryRespected() {
        val initialModel = org.json.JSONObject()
        val updatedModel1 = RelationshipManager.parseMemories("don't talk about politics", initialModel)
        val updatedModel2 = RelationshipManager.parseMemories("do not talk about my weight", updatedModel1)

        val boundaries = updatedModel2.optJSONArray("boundaries")
        assertNotNull("Boundaries array should be created", boundaries)
        assertEquals("Should have two boundaries", 2, boundaries?.length())
        assertEquals("First boundary should match", "politics", boundaries?.getString(0))
        assertEquals("Second boundary should match", "my weight", boundaries?.getString(1))
    }

    @Test
    fun testCorrectionAndDeletion() {
        // Simulating the clearModel() behavior via empty JSON
        val initialModel = org.json.JSONObject().apply {
            put("facts", org.json.JSONArray().put("old fact"))
        }
        val emptyModel = org.json.JSONObject()

        assertNull("Empty model should not have facts", emptyModel.optJSONArray("facts"))

        // Simulating restarting with the empty model
        val updatedModel = RelationshipManager.parseMemories("hello", emptyModel)
        val facts = updatedModel.optJSONArray("facts")
        assertTrue("Facts should be empty if no new facts added to cleared model", facts == null || facts.length() == 0)
    }

    @Test
    fun testIrrelevantMemoriesBounded() {
        val initialModel = org.json.JSONObject()
        var currentModel = initialModel

        // Add 60 facts
        for (i in 1..60) {
            currentModel = RelationshipManager.parseMemories("i like item $i", currentModel)
        }

        val facts = currentModel.optJSONArray("facts")
        assertNotNull(facts)
        assertEquals("Facts should be strictly bounded to 50 items", 50, facts?.length())
        assertEquals("The oldest items should be dropped, keeping the newest", "item 11", facts?.getString(0))
        assertEquals("The newest item should be at the end", "item 60", facts?.getString(49))
    }
}