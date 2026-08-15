package me.omico.ocdd.io

import java.nio.file.FileSystemLoopException
import kotlin.test.Test
import kotlin.test.assertEquals

class NioSupportTest {
    @Test
    fun genericNioReasonsDoNotControlStableClassification() {
        // Diagnostic text cannot promote an untyped NIO failure into a stable symbolic-link-loop reason.
        val platformFailure =
            java.nio.file.FileSystemException(
                "synthetic",
                null,
                "Too many levels of symbolic links",
            )

        val failure =
            platformFailure.toFileSystemException(
                FileSystemOperation.STATUS,
                "synthetic".toPath(),
            )

        assertEquals(FileSystemErrorReason.IO_FAILURE, failure.reason)
    }

    @Test
    fun typedNioLoopFailuresKeepTheirStructuredClassification() {
        // The typed platform exception remains sufficient evidence for the stable loop reason.
        val failure =
            FileSystemLoopException("synthetic").toFileSystemException(
                FileSystemOperation.STATUS,
                "synthetic".toPath(),
            )

        assertEquals(FileSystemErrorReason.FILE_SYSTEM_LOOP, failure.reason)
    }
}
