package me.omico.ocdd.io

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

private class DerivedIOException : IOException()

private class DerivedEOFException : EOFException()

class ExceptionsTest {
    @Test
    fun ioExceptionPreservesMessageAndCause() {
        // The two-argument constructor retains both observable values.
        val cause = IllegalStateException("cause")
        val exception = IOException("read failed", cause)

        assertEquals("read failed", exception.message)
        assertSame(cause, exception.cause)
    }

    @Test
    fun ioExceptionConvenienceConstructorsUseNullDefaults() {
        // Convenience constructors introduce no implicit cause or message.
        val withMessage = IOException("read failed")
        val empty = IOException()

        assertEquals("read failed", withMessage.message)
        assertNull(withMessage.cause)
        assertNull(empty.message)
        assertNull(empty.cause)
    }

    @Test
    fun protocolExceptionHasTheRequiredHierarchy() {
        // Protocol failures are I/O failures with the supplied message.
        val exception = ProtocolException("unexpected frame")

        assertIs<IOException>(exception)
        assertEquals("unexpected frame", exception.message)
        assertNull(exception.cause)
    }

    @Test
    fun eofExceptionSupportsBothConstructors() {
        // EOF supports an explicit nullable message and a null default.
        val withMessage = EOFException("truncated")
        val empty = EOFException()

        assertIs<IOException>(withMessage)
        assertEquals("truncated", withMessage.message)
        assertNull(withMessage.cause)
        assertNull(empty.message)
        assertNull(empty.cause)
    }

    @Test
    fun fileNotFoundExceptionHasTheRequiredHierarchy() {
        // Missing-file failures retain their nullable message as I/O failures.
        val exception = FileNotFoundException("missing")

        assertIs<IOException>(exception)
        assertEquals("missing", exception.message)
        assertNull(exception.cause)
    }

    @Test
    fun openExceptionTypesCanBeExtended() {
        // Test-only subclasses make the promised open modality compile-time visible.
        assertIs<IOException>(DerivedIOException())
        assertIs<EOFException>(DerivedEOFException())
    }
}
