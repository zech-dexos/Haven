package com.dexos.haven

import org.junit.Test
import org.junit.Assert.*
import kotlinx.coroutines.runBlocking
import com.dexos.haven.memory.RelationshipLayer

class RelationshipLayerIntegrationTest {

    @Test
    fun testProcessUtteranceAndRetrieveContext() {
        runBlocking {
            val fakeDao = FakeMemoryDao()
            val layer = RelationshipLayer(fakeDao)
            val userId = "test_user"

            // 1. Initial empty state
            var contextStr = layer.buildSystemContext(userId)
            assertEquals("", contextStr)

            // 2. Add preference
            layer.processUserUtterance(userId, "I like classical music")
            contextStr = layer.buildSystemContext(userId)
            assertTrue("Context should contain classical music. Was: $contextStr", contextStr.contains("classical music"))
            assertTrue("Context should contain Preferences. Was: $contextStr", contextStr.contains("Preferences:"))

            // 3. Add boundary
            layer.processUserUtterance(userId, "never talk about my diet")
            contextStr = layer.buildSystemContext(userId)
            assertTrue("Context should contain my diet. Was: $contextStr", contextStr.contains("my diet"))
            assertTrue("Context should contain BOUNDARIES. Was: $contextStr", contextStr.contains("BOUNDARIES"))

            // 4. Test correction
            layer.processUserUtterance(userId, "forget that") // Use just forget that to delete last memory
            contextStr = layer.buildSystemContext(userId)
            assertFalse("Context should NOT contain my diet. Was: $contextStr", contextStr.contains("my diet")) // Should delete the last one (diet)
            assertTrue("Context should contain classical music. Was: $contextStr", contextStr.contains("classical music")) // classical music should remain
        }
    }
}
