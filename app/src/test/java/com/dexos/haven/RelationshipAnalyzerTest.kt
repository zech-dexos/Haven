package com.dexos.haven

import org.junit.Test
import org.junit.Assert.*
import com.dexos.haven.memory.RelationshipAnalyzer

class RelationshipAnalyzerTest {

    @Test
    fun testExplicitPreference() {
        val extractions = RelationshipAnalyzer.analyze("I like classical music")
        assertEquals(1, extractions.size)
        assertEquals("preference", extractions[0].category)
        assertEquals("classical music", extractions[0].content)
        assertTrue(extractions[0].isCertain)
    }

    @Test
    fun testBoundary() {
        val extractions = RelationshipAnalyzer.analyze("never talk about my ex-husband")
        assertEquals(1, extractions.size)
        assertEquals("boundary", extractions[0].category)
        assertEquals("my ex-husband", extractions[0].content)
        assertTrue(extractions[0].isCertain)
    }

    @Test
    fun testOpenTopicUncertain() {
        val extractions = RelationshipAnalyzer.analyze("My granddaughter is visiting next week and I'm a bit nervous")
        assertEquals(1, extractions.size)
        assertEquals("open_topic", extractions[0].category)
        assertFalse(extractions[0].isCertain)
    }

    @Test
    fun testCorrection() {
        val extractions = RelationshipAnalyzer.analyze("forget that")
        assertEquals(1, extractions.size)
        assertEquals("correction", extractions[0].category)
    }
}
