package me.omico.ocdd.io

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileStatusTest {
    @Test
    fun statusQueriesDescribeFilesDirectoriesAndMissingPaths(): Unit =
        withTemporaryDirectory { directory ->
            // Boolean queries confirm only observed state while strict queries classify failures.
            val file = directory.resolve("file").createFile()
            val childDirectory = directory.resolve("directory").createDirectory()
            val missing = directory.resolve("missing")

            assertTrue(file.exists())
            assertTrue(file.isRegularFile())
            assertFalse(file.isDirectory())
            assertEquals(0L, file.fileSize())
            assertTrue(childDirectory.isDirectory())
            assertFalse(missing.exists())
            assertTrue(missing.notExists())
            val exception = assertFailsWith<FileSystemException> { missing.fileSize() }
            assertEquals(FileSystemOperation.STATUS, exception.operation)
            assertEquals(FileSystemErrorReason.NOT_FOUND, exception.reason)
            val realPathException = assertFailsWith<FileSystemException> { missing.toRealPath() }
            assertEquals(FileSystemOperation.REAL_PATH, realPathException.operation)
            assertEquals(FileSystemErrorReason.NOT_FOUND, realPathException.reason)
        }

    @Test
    fun absoluteRealAndSameFileOperationsUseFileIdentity(): Unit =
        withTemporaryDirectory { directory ->
            // Real paths are absolute and hard links compare by identity rather than path text.
            val file = directory.resolve("source").createFile()
            val hardLink =
                try {
                    directory.resolve("hard-link").createLinkPointingTo(file)
                } catch (exception: FileSystemException) {
                    // Some Android app sandboxes deny hard links even though the system call exists.
                    assertEquals(FileSystemErrorReason.ACCESS_DENIED, exception.reason)
                    null
                }

            assertTrue(file.absolute().isAbsolute)
            assertEquals(file.absolute().toString(), file.absolutePathString())
            assertTrue(file.toRealPath().isAbsolute)
            assertTrue(file.isSameFileAs(hardLink ?: file.toRealPath()))
        }

    @Test
    fun symbolicLinkQueriesRespectFollowOptions(): Unit =
        withTemporaryDirectory { directory ->
            // Link identity remains queryable after its target becomes missing.
            val target = directory.resolve("target").createFile()
            val link = directory.resolve("link").createSymbolicLinkPointingTo(target.fileName!!)

            assertTrue(link.isSymbolicLink())
            assertTrue(link.exists())
            assertTrue(link.exists(LinkOption.NOFOLLOW_LINKS))
            assertEquals(target.fileName, link.readSymbolicLink())
            assertTrue(link.toRealPath(LinkOption.NOFOLLOW_LINKS).isSymbolicLink())

            target.deleteExisting()
            assertFalse(link.exists())
            assertTrue(link.exists(LinkOption.NOFOLLOW_LINKS))
        }

    @Test
    fun noFollowRealPathPreservesIntermediateLinksBeforeParentSegments(): Unit =
        withTemporaryDirectory { directory ->
            val real = directory.resolve("real").createDirectory()
            real.resolve("nested").createDirectory()
            val sibling = real.resolve("sibling").createFile()
            val link = directory.resolve("link").createSymbolicLinkPointingTo("real/nested".toPath())
            val throughLink = link.resolve("../sibling")

            assertEquals(sibling.toRealPath(), throughLink.toRealPath())
            assertEquals(throughLink.absolute(), throughLink.toRealPath(LinkOption.NOFOLLOW_LINKS))
        }

    @Test
    fun symbolicLinkLoopsRemainIndeterminateForBooleanQueries(): Unit =
        withTemporaryDirectory { directory ->
            val first = directory.resolve("first")
            val second = directory.resolve("second")
            first.createSymbolicLinkPointingTo(second.fileName!!)
            second.createSymbolicLinkPointingTo(first.fileName!!)

            assertTrue(first.isSymbolicLink())
            assertFalse(first.exists())
            assertFalse(first.notExists())
            assertFalse(first.isDirectory())
            assertFalse(first.isRegularFile())
            val realPathFailure = assertFailsWith<FileSystemException> { first.toRealPath() }
            assertEquals(FileSystemOperation.REAL_PATH, realPathFailure.operation, "toRealPath operation")
            assertEquals(FileSystemErrorReason.FILE_SYSTEM_LOOP, realPathFailure.reason, "toRealPath reason")
            val sizeFailure = assertFailsWith<FileSystemException> { first.fileSize() }
            assertEquals(FileSystemOperation.STATUS, sizeFailure.operation, "fileSize operation")
            assertEquals(FileSystemErrorReason.FILE_SYSTEM_LOOP, sizeFailure.reason, "fileSize reason")
        }

    @Test
    fun hiddenNamesArePurelyLexical() {
        // Hidden-name checks never require a filesystem entry.
        assertTrue(".hidden".toPath().isHidden())
        assertFalse(".".toPath().isHidden())
        assertFalse("..".toPath().isHidden())
        assertFalse("visible".toPath().isHidden())
    }

    @Test
    fun fileSizeRejectsNonRegularFilesystemObjects() {
        val exception = assertFailsWith<FileSystemException> { "/dev/null".toPath().fileSize() }

        assertEquals(FileSystemOperation.STATUS, exception.operation)
        assertEquals(FileSystemErrorReason.IO_FAILURE, exception.reason)
    }

    @Test
    fun accessQueriesReflectPermissionChanges(): Unit =
        withTemporaryDirectory { directory ->
            val file = directory.resolve("access").createFile()

            assertTrue(file.isReadable())
            assertTrue(file.isWritable())
            assertFalse(file.isExecutable())

            file.setPosixFilePermissions(
                file.getPosixFilePermissions() + PosixFilePermission.OWNER_EXECUTE,
            )
            assertTrue(file.isExecutable())
        }

    @Test
    fun inaccessibleAncestorsMakeStatusIndeterminate(): Unit =
        withTemporaryDirectory { directory ->
            val restricted = directory.resolve("restricted").createDirectory()
            val file = restricted.resolve("file").also { it.writeText("content") }
            restricted.setPosixFilePermissions(emptySet())
            try {
                assertFalse(file.exists())
                assertFalse(file.notExists())
                assertFalse(file.isDirectory())
                assertFalse(file.isRegularFile())
                assertFalse(file.isReadable())
                assertFalse(file.isWritable())
                assertFalse(file.isExecutable())
                val failure = assertFailsWith<FileSystemException> { file.fileSize() }
                assertEquals(FileSystemOperation.STATUS, failure.operation)
                assertEquals(FileSystemErrorReason.ACCESS_DENIED, failure.reason)
            } finally {
                restricted.setPosixFilePermissions(
                    setOf(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE,
                    ),
                )
            }
        }
}
