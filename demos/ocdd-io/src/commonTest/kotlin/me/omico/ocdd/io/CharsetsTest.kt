package me.omico.ocdd.io

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CharsetsTest {
    @Test
    fun utf8HasTheContractNameOnEveryRead() {
        // The public constant remains the same immutable charset value.
        val first = Charsets.UTF_8
        val second = Charsets.UTF_8

        assertEquals("UTF-8", first.name)
        assertEquals(first, second)
    }

    @Test
    fun utf8CoversEverySequenceLengthAndKeepsBomAsContent() {
        // Empty, one- through four-byte scalars, and BOM content define the complete valid encoding matrix.
        val cases =
            listOf(
                "" to byteArrayOf(),
                "A" to bytes(0x41),
                "¢" to bytes(0xc2, 0xa2),
                "文" to bytes(0xe6, 0x96, 0x87),
                "😀" to bytes(0xf0, 0x9f, 0x98, 0x80),
                "\uFEFF" to bytes(0xef, 0xbb, 0xbf),
            )

        cases.forEach { (text, encoded) ->
            assertContentEquals(encoded, Charsets.UTF_8.encode(text), text)
            assertEquals(text, Charsets.UTF_8.decode("utf8".toPath(), encoded), text)
        }
    }

    @Test
    fun utf8RejectsMalformedAndTruncatedSequenceClasses() {
        // Each invalid structural class must fail instead of producing replacement characters.
        val invalidSequences =
            listOf(
                bytes(0x80),
                bytes(0xc0, 0xaf),
                bytes(0xed, 0xa0, 0x80),
                bytes(0xf4, 0x90, 0x80, 0x80),
                bytes(0xc2),
                bytes(0xe2, 0x82),
                bytes(0xf0, 0x9f, 0x98),
            )
        val path = "utf8".toPath()

        invalidSequences.forEach { encoded ->
            val failure = assertFailsWith<FileSystemException> { Charsets.UTF_8.decode(path, encoded) }

            assertEquals(FileSystemOperation.READ, failure.operation)
            assertEquals(FileSystemErrorReason.INVALID_ENCODING, failure.reason)
            assertEquals(path, failure.path)
        }
    }

    private fun bytes(vararg values: Int): ByteArray = values.map(Int::toByte).toByteArray()
}
