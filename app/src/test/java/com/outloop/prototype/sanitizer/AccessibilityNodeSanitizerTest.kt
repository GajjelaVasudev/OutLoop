package com.outloop.prototype.sanitizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class AccessibilityNodeSanitizerTest {

    @Test
    fun testNullRootNodeReturnsEmptySnapshot() {
        val snapshot = AccessibilityNodeSanitizer.sanitize(null, 1920)

        assertNull(snapshot.packageName)
        assertFalse(snapshot.isForeground)
        assertFalse(snapshot.hasRootNode)
        assertEquals(0, snapshot.totalNodeCount)
        assertEquals(0, snapshot.maxDepth)
    }
}
