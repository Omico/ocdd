package me.omico.ocdd.io

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FileTimeTest {
    @Test
    fun valueSemanticsIncludeSecondsAndNanoseconds() {
        // Both fields participate in ordering, equality, hashing, and fixed-width text.
        val first = FileTime(1L, 2)
        val same = FileTime(1L, 2)
        val later = FileTime(1L, 3)

        assertEquals(first, same)
        assertEquals(first.hashCode(), same.hashCode())
        assertTrue(first < later)
        assertEquals("1:000000002", first.toString())
    }

    @Test
    fun nanosecondsMustBeWithinOneSecond() {
        // Invalid subsecond values fail before a FileTime can escape.
        assertFailsWith<IllegalArgumentException> { FileTime(0L, -1) }
        assertFailsWith<IllegalArgumentException> { FileTime(0L, 1_000_000_000) }
    }
}
