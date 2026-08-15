package me.omico.ocdd.io

import java.nio.file.Paths
import kotlin.math.sign
import kotlin.test.Test
import kotlin.test.assertEquals

class PathReferenceTest {
    @Test
    fun coveredUnixLexicalBehaviorMatchesJavaNio() {
        // Java NIO provides an independent oracle for the shared lexical subset.
        val values = listOf("", ".", "/", "//a//b/", "a/./b", "a/../../b", "a\\b")

        values.forEach { value ->
            val actual = value.toPath()
            val reference = Paths.get(value)
            assertEquals(reference.toString(), actual.toString(), value)
            assertEquals(reference.normalize().toString(), actual.normalize().toString(), value)
            assertEquals(reference.nameCount, actual.nameCount, value)
        }
    }

    @Test
    fun coveredCompositionAndOrderingMatchJavaNio() {
        // The reference comparison checks results rather than implementation structure.
        val pairs =
            listOf(
                "/a/b" to "/a/c",
                "a" to "a/b",
                "a/./b" to "a/c",
                "A" to "a",
            )

        pairs.forEach { (left, right) ->
            val actualLeft = left.toPath()
            val actualRight = right.toPath()
            val referenceLeft = Paths.get(left)
            val referenceRight = Paths.get(right)
            assertEquals(referenceLeft.compareTo(referenceRight).sign, actualLeft.compareTo(actualRight).sign)
            if (referenceLeft.isAbsolute == referenceRight.isAbsolute && "." !in actualLeft.segments) {
                assertEquals(
                    referenceLeft.relativize(referenceRight).toString(),
                    actualLeft.relativize(actualRight).toString(),
                )
            }
        }
    }

    @Test
    fun specifiedUnsignedOrderingDifferenceIsIndependent() {
        // OCDD deliberately uses unsigned UTF-8 bytes instead of Java's signed-byte ordering.
        assertEquals(1, Paths.get("\u007f").compareTo(Paths.get("é")).sign)
        assertEquals(-1, "\u007f".toPath().compareTo("é".toPath()).sign)
    }
}
