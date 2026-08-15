package me.omico.ocdd.io

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileTransferTest {
    @Test
    fun singleCopyAndMoveHonorConflictsAndReplacement(): Unit =
        withTemporaryDirectory { directory ->
            val source = directory.resolve("source").also { it.writeText("source") }
            val target = directory.resolve("target")

            assertEquals(target, source.copyTo(target))
            assertEquals("source", target.readText())
            source.writeText("changed")
            val conflict = assertFailsWith<FileSystemException> { source.copyTo(target) }
            assertEquals(FileSystemErrorReason.ALREADY_EXISTS, conflict.reason)
            assertEquals(target, source.copyTo(target, overwrite = true))
            assertEquals("changed", target.readText())

            val moved = directory.resolve("moved")
            assertEquals(moved, source.moveTo(moved))
            assertTrue(source.notExists(LinkOption.NOFOLLOW_LINKS))
            assertEquals("changed", moved.readText())
            assertFailsWith<IllegalArgumentException> { moved.copyTo(source, FileCopyOption.ATOMIC_MOVE) }
        }

    @Test
    fun symbolicLinkCopyCanFollowOrPreserveTheLink(): Unit =
        withTemporaryDirectory { directory ->
            val file = directory.resolve("file").also { it.writeText("data") }
            val link = directory.resolve("link").createSymbolicLinkPointingTo(file.fileName!!)
            val followed = directory.resolve("followed")
            val preserved = directory.resolve("preserved")

            link.copyTo(followed)
            link.copyTo(preserved, FileCopyOption.NOFOLLOW_LINKS)

            assertFalse(followed.isSymbolicLink())
            assertEquals("data", followed.readText())
            assertTrue(preserved.isSymbolicLink())
            assertEquals(file.fileName, preserved.readSymbolicLink())
        }

    @Test
    fun recursiveCopyBuildsCompleteTreeAndRejectsOverlap(): Unit =
        withTemporaryDirectory { directory ->
            val source = directory.resolve("source").createDirectory()
            source
                .resolve("a/file")
                .createParentDirectories()
                .resolveSibling("file")
                .writeText("a")
            source.resolve("b").writeText("b")
            val target = directory.resolve("target")

            assertEquals(target, source.copyToRecursively(target, followLinks = false, overwrite = false))
            assertEquals("a", target.resolve("a/file").readText())
            assertEquals("b", target.resolve("b").readText())
            assertFailsWith<IllegalArgumentException> {
                source.copyToRecursively(source.resolve("nested"), followLinks = false, overwrite = false)
            }

            val alias = directory.resolve("source-alias").createSymbolicLinkPointingTo(source)
            assertFailsWith<IllegalArgumentException> {
                source.copyToRecursively(alias.resolve("missing/nested"), followLinks = false, overwrite = false)
            }
            assertFalse(source.resolve("missing").exists())
        }

    @Test
    fun recursiveCallbacksCanSkipOrTerminate(): Unit =
        withTemporaryDirectory { directory ->
            val source = directory.resolve("source").createDirectory()
            source.resolve("a").writeText("a")
            source.resolve("b").writeText("b")
            val target = directory.resolve("target")
            val visited = mutableListOf<String>()

            source.copyToRecursively(target, followLinks = false) { entry, destination ->
                visited += entry.name
                if (entry.name == "b") {
                    CopyActionResult.TERMINATE
                } else {
                    copyToIgnoringExistingDirectory(entry, destination, followLinks = false)
                }
            }

            assertTrue("source" in visited)
            assertTrue(target.resolve("a").exists())
            assertFalse(target.resolve("b").exists())
        }

    @Test
    fun singleDirectoryCopyDoesNotCopyDescendantsAndNonEmptyTargetsSurvive(): Unit =
        withTemporaryDirectory { directory ->
            val sourceDirectory = directory.resolve("source-directory").createDirectory()
            sourceDirectory.resolve("child").writeText("child")
            val copiedDirectory = directory.resolve("copied-directory")

            sourceDirectory.copyTo(copiedDirectory, *emptyArray())
            assertTrue(copiedDirectory.isDirectory())
            assertFalse(copiedDirectory.resolve("child").exists())

            val sourceFile = directory.resolve("source-file").also { it.writeText("source") }
            copiedDirectory.resolve("keep").writeText("keep")
            val failure =
                assertFailsWith<FileSystemException> {
                    sourceFile.copyTo(copiedDirectory, overwrite = true)
                }
            assertEquals(FileSystemErrorReason.DIRECTORY_NOT_EMPTY, failure.reason)
            assertEquals("source", sourceFile.readText())
            assertEquals("keep", copiedDirectory.resolve("keep").readText())
        }

    @Test
    fun copiedAttributesAndAtomicMovesUsePlatformFacilities(): Unit =
        withTemporaryDirectory { directory ->
            val source = directory.resolve("source").also { it.writeText("data") }
            val time = FileTime(1_700_000_100L, 456_000_000)
            val permissions = setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
            source.setLastModifiedTime(time)
            source.setPosixFilePermissions(permissions)
            val copy = directory.resolve("copy")

            source.copyTo(copy, FileCopyOption.COPY_ATTRIBUTES)
            assertEquals(time, copy.getLastModifiedTime())
            assertEquals(permissions, copy.getPosixFilePermissions())

            val moved = directory.resolve("atomic")
            assertEquals(moved, copy.moveTo(moved, FileCopyOption.ATOMIC_MOVE))
            assertTrue(copy.notExists(LinkOption.NOFOLLOW_LINKS))
            assertEquals("data", moved.readText())
            assertFailsWith<IllegalArgumentException> { moved.moveTo(copy, FileCopyOption.COPY_ATTRIBUTES) }
        }

    @Test
    fun recursiveOnErrorCanSkipOneConflictAndContinue(): Unit =
        withTemporaryDirectory { directory ->
            val source = directory.resolve("source").createDirectory()
            source.resolve("a").writeText("source-a")
            source.resolve("b").writeText("source-b")
            val target = directory.resolve("target").createDirectory()
            target.resolve("a").writeText("target-a")
            val failures = mutableListOf<String>()

            source.copyToRecursively(
                target,
                onError = { failed, _, _ ->
                    failures += failed.name
                    OnErrorResult.SKIP_SUBTREE
                },
                followLinks = false,
                overwrite = false,
            )

            assertEquals(listOf("a"), failures)
            assertEquals("target-a", target.resolve("a").readText())
            assertEquals("source-b", target.resolve("b").readText())
        }

    @Test
    fun recursiveRootReadFailuresReachOnError(): Unit =
        withTemporaryDirectory { directory ->
            // The source root is an entry, so its read failure follows the same callback path as descendant failures.
            val source = directory.resolve("missing")
            val target = directory.resolve("target")
            var callbackCount = 0

            val result =
                source.copyToRecursively(
                    target,
                    onError = { failedSource, failedTarget, exception ->
                        callbackCount++
                        assertEquals(source, failedSource)
                        assertEquals(target, failedTarget)
                        assertTrue(exception is FileSystemException)
                        assertEquals(FileSystemErrorReason.NOT_FOUND, exception.reason)
                        OnErrorResult.SKIP_SUBTREE
                    },
                    followLinks = false,
                    overwrite = false,
                )

            assertEquals(target, result)
            assertEquals(1, callbackCount)
            assertTrue(target.notExists(LinkOption.NOFOLLOW_LINKS))
        }

    @Test
    fun recursiveFailureReportsEarlierTargetChanges(): Unit =
        withTemporaryDirectory { directory ->
            val source = directory.resolve("source").createDirectory()
            source.resolve("a").writeText("source-a")
            source.resolve("b").writeText("source-b")
            val target = directory.resolve("target").createDirectory()
            target.resolve("b").writeText("target-b")

            val failure =
                assertFailsWith<FileSystemException> {
                    source.copyToRecursively(target, followLinks = false, overwrite = false)
                }

            assertEquals(FileSystemOperation.COPY, failure.operation)
            assertEquals(source.resolve("b"), failure.path)
            assertEquals(target.resolve("b"), failure.otherPath)
            assertEquals(FileSystemErrorReason.ALREADY_EXISTS, failure.reason)
            assertTrue(failure.partialResult)
            assertEquals("source-a", target.resolve("a").readText())
            assertEquals("target-b", target.resolve("b").readText())
        }
}
