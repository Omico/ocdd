package me.omico.ocdd.io

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DirectoryTraversalTest {
    @Test
    fun missingDirectoryUsesTheStableNotFoundReason(): Unit =
        withTemporaryDirectory { directory ->
            // Direct listing classifies a missing directory before exposing any entries.
            val missing = directory.resolve("missing")
            val exception = assertFailsWith<FileSystemException> { missing.listDirectoryEntries() }

            assertEquals(FileSystemOperation.LIST, exception.operation)
            assertEquals(FileSystemErrorReason.NOT_FOUND, exception.reason)
        }

    @Test
    fun walkDefersFilesystemAccessUntilIteration(): Unit =
        withTemporaryDirectory { directory ->
            val missing = directory.resolve("missing")

            val entries = missing.walk()
            val exception = assertFailsWith<FileSystemException> { entries.iterator().hasNext() }

            assertEquals(FileSystemOperation.TRAVERSE, exception.operation)
            assertEquals(FileSystemErrorReason.NOT_FOUND, exception.reason)
        }

    @Test
    fun directoryEntriesApplyGlobSortAndSingleUseSequence(): Unit =
        withTemporaryDirectory { directory ->
            directory.resolve("b.txt").createFile()
            directory.resolve("a.txt").createFile()
            directory.resolve("other.bin").createFile()

            assertEquals(listOf("a.txt", "b.txt"), directory.listDirectoryEntries("?.txt").map(Path::name))
            val observed = mutableListOf<String>()
            directory.forEachDirectoryEntry("*.txt") { observed += it.name }
            assertEquals(listOf("a.txt", "b.txt"), observed)
            assertEquals(3, directory.useDirectoryEntries { it.count() })
            assertFailsWith<IllegalArgumentException> { directory.listDirectoryEntries("bad/name") }
        }

    @Test
    fun walkSupportsDepthFirstBreadthFirstAndDirectoryInclusion(): Unit =
        withTemporaryDirectory { directory ->
            directory.resolve("a/inner").createDirectories()
            directory.resolve("a/first").createFile()
            directory.resolve("a/inner/deep").createFile()
            directory.resolve("b").createFile()

            assertEquals(
                listOf("a/first", "a/inner/deep", "b"),
                directory.walk().map { it.relativeTo(directory).pathString }.toList(),
            )
            assertEquals(
                listOf("a", "a/first", "a/inner", "a/inner/deep", "b"),
                directory.walk(PathWalkOption.INCLUDE_DIRECTORIES).map { it.relativeTo(directory).pathString }.toList(),
            )
            assertEquals(
                listOf("a", "b", "a/first", "a/inner", "a/inner/deep"),
                directory
                    .walk(PathWalkOption.INCLUDE_DIRECTORIES, PathWalkOption.BREADTH_FIRST)
                    .map { it.relativeTo(directory).pathString }
                    .toList(),
            )
        }

    @Test
    fun visitorControlSkipsSubtreesAndBalancesCallbacks(): Unit =
        withTemporaryDirectory { directory ->
            directory
                .resolve("keep/file")
                .createParentDirectories()
                .resolveSibling("file")
                .createFile()
            directory
                .resolve("skip/file")
                .createParentDirectories()
                .resolveSibling("file")
                .createFile()
            val events = mutableListOf<String>()

            directory.visitFileTree {
                onPreVisitDirectory { path, _ ->
                    events += "pre:${path.name}"
                    if (path.name == "skip") FileVisitResult.SKIP_SUBTREE else FileVisitResult.CONTINUE
                }
                onVisitFile { path, _ ->
                    events += "file:${path.name}"
                    FileVisitResult.CONTINUE
                }
                onPostVisitDirectory { path, exception ->
                    assertEquals(null, exception)
                    events += "post:${path.name}"
                    FileVisitResult.CONTINUE
                }
            }

            assertTrue("file:file" in events)
            assertTrue("pre:skip" in events)
            assertFalse(events.count { it == "file:file" } > 1)
            assertFalse("post:skip" in events)
            assertFailsWith<IllegalArgumentException> { directory.visitFileTree(maxDepth = -1) {} }
        }

    @Test
    fun directoryEnumerationFailureUsesOnlyTheFailureCallback(): Unit =
        withTemporaryDirectory { directory ->
            val blocked = directory.resolve("blocked").createDirectory()
            blocked.resolve("child").createFile()
            val originalPermissions = blocked.getPosixFilePermissions()
            val preVisited = mutableListOf<Path>()
            val postVisited = mutableListOf<Path>()
            var failedPath: Path? = null

            try {
                blocked.setPosixFilePermissions(emptySet())
                directory.visitFileTree {
                    onPreVisitDirectory { path, _ ->
                        preVisited.add(path)
                        FileVisitResult.CONTINUE
                    }
                    onVisitFileFailed { path, exception ->
                        failedPath = path
                        assertTrue(exception is FileSystemException)
                        assertEquals(FileSystemOperation.TRAVERSE, exception.operation)
                        assertEquals(FileSystemErrorReason.ACCESS_DENIED, exception.reason)
                        FileVisitResult.CONTINUE
                    }
                    onPostVisitDirectory { path, exception ->
                        assertEquals(null, exception)
                        postVisited.add(path)
                        FileVisitResult.CONTINUE
                    }
                }
            } finally {
                blocked.setPosixFilePermissions(originalPermissions)
            }

            assertEquals(blocked, failedPath)
            assertFalse(blocked in preVisited)
            assertFalse(blocked in postVisited)
            assertTrue(directory in preVisited)
            assertTrue(directory in postVisited)
        }

    @Test
    fun followingDirectoryLinksDetectsCycles(): Unit =
        withTemporaryDirectory { directory ->
            val child = directory.resolve("child").createDirectory()
            child.resolve("back").createSymbolicLinkPointingTo("..".toPath())

            assertEquals(listOf("child/back"), directory.walk().map { it.relativeTo(directory).pathString }.toList())
            val loop =
                assertFailsWith<FileSystemException> {
                    directory.walk(PathWalkOption.FOLLOW_LINKS).toList()
                }
            assertEquals(FileSystemErrorReason.FILE_SYSTEM_LOOP, loop.reason)
            assertEquals(FileSystemOperation.TRAVERSE, loop.operation)
        }

    @Test
    fun followingPureSymbolicLinkLoopsReportTraversalFailure(): Unit =
        withTemporaryDirectory { directory ->
            // A loop with no directory target must fail during type resolution instead of being yielded as a file.
            val first = directory.resolve("first")
            val second = directory.resolve("second")
            first.createSymbolicLinkPointingTo(second.fileName!!)
            second.createSymbolicLinkPointingTo(first.fileName!!)

            val loop =
                assertFailsWith<FileSystemException> {
                    directory.walk(PathWalkOption.FOLLOW_LINKS).toList()
                }

            assertEquals(FileSystemErrorReason.FILE_SYSTEM_LOOP, loop.reason)
            assertEquals(FileSystemOperation.TRAVERSE, loop.operation)
        }

    @Test
    fun resourceClosurePreservesPrimaryFailureAndReportsCloseOnlyFailure() {
        // Fault injection fixes the callback/close precedence independently of platform directory handles.
        val closeOnly = IOException("close only")
        val reportedClose =
            assertFailsWith<IOException> {
                useResource(close = { throw closeOnly }) { }
            }
        assertSame(closeOnly, reportedClose)

        val callbackFailure = IllegalStateException("callback")
        val suppressedClose = IOException("suppressed close")
        val reportedCallback =
            assertFailsWith<IllegalStateException> {
                useResource(close = { throw suppressedClose }) { throw callbackFailure }
            }
        assertSame(callbackFailure, reportedCallback)
        assertEquals(listOf(suppressedClose), reportedCallback.suppressedExceptions)
    }

    @Test
    fun globQuestionMarkMatchesOneUnicodeScalarAndUseSequenceExpires(): Unit =
        withTemporaryDirectory { directory ->
            directory.resolve("😀.txt").createFile()
            directory.resolve("ab.txt").createFile()
            lateinit var escaped: Sequence<Path>

            assertEquals(listOf("😀.txt"), directory.listDirectoryEntries("?.txt").map(Path::name))
            directory.useDirectoryEntries { entries ->
                escaped = entries
                assertEquals(2, entries.count())
            }
            assertFailsWith<IllegalStateException> { escaped.toList() }
        }

    @Test
    fun visitorSkipSiblingsTerminateAndFailureCallbacksControlTraversal(): Unit =
        withTemporaryDirectory { directory ->
            directory.resolve("a").createFile()
            directory.resolve("b").createFile()
            directory.resolve("c").createFile()
            val visited = mutableListOf<String>()
            directory.visitFileTree {
                onVisitFile { path, _ ->
                    visited += path.name
                    FileVisitResult.SKIP_SIBLINGS
                }
            }
            assertEquals(listOf("a"), visited)

            var callbacks = 0
            directory.visitFileTree {
                onPreVisitDirectory { _, _ ->
                    callbacks++
                    FileVisitResult.TERMINATE
                }
                onPostVisitDirectory { _, _ ->
                    callbacks++
                    FileVisitResult.CONTINUE
                }
            }
            assertEquals(1, callbacks)

            val missing = directory.resolve("missing")
            var failure: IOException? = null
            missing.visitFileTree(
                fileVisitor {
                    onVisitFileFailed { _, exception ->
                        failure = exception
                        FileVisitResult.TERMINATE
                    }
                },
            )
            assertTrue(failure is FileSystemException)
            assertFailsWith<IllegalArgumentException> {
                fileVisitor {
                    onVisitFile { _, _ -> FileVisitResult.CONTINUE }
                    onVisitFile { _, _ -> FileVisitResult.CONTINUE }
                }
            }
        }
}
