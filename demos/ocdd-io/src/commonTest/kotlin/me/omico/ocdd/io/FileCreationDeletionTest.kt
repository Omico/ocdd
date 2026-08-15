package me.omico.ocdd.io

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileCreationDeletionTest {
    @Test
    fun exclusiveCreationAndDirectoryChainsPreserveExistingEntries(): Unit =
        withTemporaryDirectory { directory ->
            // File creation is exclusive and multi-level creation returns a complete directory chain.
            val file = directory.resolve("file").createFile()
            val nested = directory.resolve("a/b/c").createDirectories()
            val sibling = directory.resolve("parents/child")

            assertTrue(file.exists())
            assertTrue(nested.isDirectory())
            assertEquals(sibling, sibling.createParentDirectories())
            assertTrue(directory.resolve("parents").isDirectory())
            assertFalse(sibling.exists())
            val conflict = assertFailsWith<FileSystemException> { file.createFile() }
            assertEquals(FileSystemOperation.CREATE, conflict.operation)
            assertEquals(FileSystemErrorReason.ALREADY_EXISTS, conflict.reason)
            val nonDirectory = assertFailsWith<FileSystemException> { file.createDirectories() }
            assertEquals(FileSystemOperation.CREATE, nonDirectory.operation)
            assertEquals(FileSystemErrorReason.NOT_A_DIRECTORY, nonDirectory.reason)
            val nonDirectoryParent = assertFailsWith<FileSystemException> { file.resolve("child").createDirectories() }
            assertEquals(FileSystemOperation.CREATE, nonDirectoryParent.operation)
            assertEquals(FileSystemErrorReason.NOT_A_DIRECTORY, nonDirectoryParent.reason)
        }

    @Test
    fun temporaryEntriesHonorDirectoryPrefixAndSuffix(): Unit =
        withTemporaryDirectory { directory ->
            // Temporary names use the requested parent and stable prefix/suffix portions.
            val tempDirectory = createTempDirectory(directory, prefix = "dir-")
            val tempFile = createTempFile(directory, prefix = "file-", suffix = ".data")

            assertEquals(directory, tempDirectory.parent)
            assertTrue(tempDirectory.name.startsWith("dir-"))
            assertTrue(tempDirectory.isDirectory())
            assertEquals(directory, tempFile.parent)
            assertTrue(tempFile.name.startsWith("file-"))
            assertTrue(tempFile.name.endsWith(".data"))
            assertTrue(tempFile.isRegularFile())
            assertFailsWith<IllegalArgumentException> { createTempFile(directory, prefix = "bad/name") }
        }

    @Test
    fun directoryChainsPreserveAccessDeniedForIndeterminateAncestors(): Unit =
        withTemporaryDirectory { directory ->
            // An inaccessible parent is not evidence that the requested path is a non-directory.
            val blocked = directory.resolve("blocked").createDirectory()
            val originalPermissions = blocked.getPosixFilePermissions()

            try {
                blocked.setPosixFilePermissions(emptySet())
                val failure = assertFailsWith<FileSystemException> { blocked.resolve("child").createDirectories() }

                assertEquals(FileSystemOperation.CREATE, failure.operation)
                assertEquals(FileSystemErrorReason.ACCESS_DENIED, failure.reason)
            } finally {
                blocked.setPosixFilePermissions(originalPermissions)
            }
        }

    @Test
    fun deletionDistinguishesMissingAndNonEmptyTargets(): Unit =
        withTemporaryDirectory { directory ->
            // Conditional deletion masks only absence and recursive deletion removes complete trees.
            val missing = directory.resolve("missing")
            val nonDirectoryParent = directory.resolve("file-parent").createFile()
            val tree = directory.resolve("tree").createDirectory()
            tree.resolve("child").createFile()

            assertFalse(missing.deleteIfExists())
            val nonDirectory =
                assertFailsWith<FileSystemException> { nonDirectoryParent.resolve("child").deleteIfExists() }
            assertEquals(FileSystemErrorReason.NOT_A_DIRECTORY, nonDirectory.reason)
            val nonEmpty = assertFailsWith<FileSystemException> { tree.deleteExisting() }
            assertEquals(FileSystemErrorReason.DIRECTORY_NOT_EMPTY, nonEmpty.reason)
            tree.deleteRecursively()
            assertTrue(tree.notExists())
            val absent = assertFailsWith<FileSystemException> { missing.deleteExisting() }
            assertEquals(FileSystemErrorReason.NOT_FOUND, absent.reason)
        }

    @Test
    fun recursiveDeletionReportsChangesBeforeRootRemovalFails(): Unit =
        withTemporaryDirectory { directory ->
            val tree = directory.resolve("tree").createDirectory()
            val child = tree.resolve("child").createFile()
            directory.setPosixFilePermissions(
                setOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_EXECUTE,
                ),
            )
            try {
                val failure = assertFailsWith<FileSystemException> { tree.deleteRecursively() }

                assertEquals(FileSystemOperation.DELETE, failure.operation)
                assertEquals(tree, failure.path)
                assertEquals(FileSystemErrorReason.ACCESS_DENIED, failure.reason)
                assertTrue(failure.partialResult)
                assertFalse(child.exists(LinkOption.NOFOLLOW_LINKS))
                assertTrue(tree.isDirectory(LinkOption.NOFOLLOW_LINKS))
            } finally {
                directory.setPosixFilePermissions(
                    setOf(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE,
                    ),
                )
            }
        }
}
