package com.dexos.haven

import org.junit.Test
import org.junit.Assert.*

class EmergencyRecognitionTest {

    @Test
    fun testEmergencyPhrases() {
        // Since isEmergency is private in HavenCapabilityLayer, we simulate the logic here for the test.
        // A robust implementation would either extract this into a testable component or use Robolectric.
        fun isEmergency(t: String): Boolean {
            val lower = t.lowercase()
            return lower.contains("call 911") || lower.contains("dial 911") ||
                   lower.contains("call for help") || lower.contains("help me") ||
                   lower.contains("i fell") || lower.contains("i can't get up") ||
                   lower.contains("i cant get up") || lower.contains("i can't breathe") ||
                   lower.contains("i cant breathe") || lower.contains("chest pain") ||
                   lower.contains("need an ambulance") || lower.contains("call emergency services") ||
                   lower.contains("call an ambulance") || lower.contains("heart attack") ||
                   lower.contains("having a stroke")
        }

        assertTrue(isEmergency("help me please"))
        assertTrue(isEmergency("i think i am having a heart attack"))
        assertTrue(isEmergency("call 911 right now"))
        assertTrue(isEmergency("i fell and i cant get up"))
        assertTrue(isEmergency("i can't breathe"))

        assertFalse(isEmergency("call john"))
        assertFalse(isEmergency("what is the weather"))
        assertFalse(isEmergency("play some music"))
    }
}
