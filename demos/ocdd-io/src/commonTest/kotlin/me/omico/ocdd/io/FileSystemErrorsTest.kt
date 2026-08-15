package me.omico.ocdd.io

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileSystemErrorsTest {
    @Test
    fun exceptionRetainsStableFailureContext() {
        // Stable fields carry classification without requiring callers to parse the message.
        val path = "/source".toPath()
        val otherPath = "/target".toPath()
        val exception =
            FileSystemException(
                message = "diagnostic only",
                operation = FileSystemOperation.COPY,
                path = path,
                otherPath = otherPath,
                reason = FileSystemErrorReason.ACCESS_DENIED,
                partialResult = true,
            )

        assertIs<IOException>(exception)
        assertEquals(FileSystemOperation.COPY, exception.operation)
        assertEquals(path, exception.path)
        assertEquals(otherPath, exception.otherPath)
        assertEquals(FileSystemErrorReason.ACCESS_DENIED, exception.reason)
        assertTrue(exception.partialResult)
    }

    @Test
    fun singlePathFailureUsesContractDefaults() {
        // Single-path failures carry no peer path and report no partial result by default.
        val exception =
            FileSystemException(
                message = "missing",
                operation = FileSystemOperation.OPEN,
                path = "missing".toPath(),
                reason = FileSystemErrorReason.NOT_FOUND,
            )

        assertNull(exception.otherPath)
        assertFalse(exception.partialResult)
    }
}
