package me.omico.ocdd.io

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class CharsetTest {
    @Test
    fun sameNameHasValueSemantics() {
        // The name is the sole value used by equality and hashing.
        val first = Charset("UTF-8")
        val second = Charset("UTF-8")

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun nameComparisonIsCaseSensitive() {
        // ASCII case differences remain observable and unequal.
        assertNotEquals(Charset("UTF-8"), Charset("utf-8"))
    }

    @Test
    fun nameAndStringRepresentationRemainStable() {
        // Repeated reads expose the unchanged construction value.
        val charset = Charset("UTF-8")

        assertEquals("UTF-8", charset.name)
        assertEquals("UTF-8", charset.name)
        assertEquals(charset.name, charset.toString())
    }
}
