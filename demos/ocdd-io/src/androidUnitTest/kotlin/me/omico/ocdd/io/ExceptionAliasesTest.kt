package me.omico.ocdd.io

import kotlin.test.Test
import kotlin.test.assertEquals

class ExceptionAliasesTest {
    @Test
    fun publicExceptionsAreTheRequiredJavaTypes() {
        // Android exposes the Java platform classes without wrapper identities.
        assertEquals(java.io.IOException::class, IOException::class)
        assertEquals(java.net.ProtocolException::class, ProtocolException::class)
        assertEquals(java.io.EOFException::class, EOFException::class)
        assertEquals(java.io.FileNotFoundException::class, FileNotFoundException::class)
    }
}
