package me.omico.ocdd.io

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FileAttributesTest {
    @Test
    fun creationAttributeValidatesAndSnapshotsPermissions() {
        // Public creation attributes admit only the declared POSIX permission value.
        val source = mutableSetOf(PosixFilePermission.OWNER_READ)
        val attribute = FileAttribute("posix:permissions", source)
        source += PosixFilePermission.OWNER_WRITE

        assertEquals(setOf(PosixFilePermission.OWNER_READ), attribute.value)
        assertFailsWith<IllegalArgumentException> { FileAttribute("basic:size", 1L) }
        assertFailsWith<IllegalArgumentException> { FileAttribute("posix:permissions", setOf("OWNER_READ")) }
    }

    @Test
    fun basicAndStringAttributeReadsShareOneSnapshotShape(): Unit =
        withTemporaryDirectory { directory ->
            // Typed and string APIs expose the same file type, size, and timestamp values.
            val file = directory.resolve("file").createFile()
            val attributes = file.readAttributes()
            val selected = file.readAttributes("basic:size,lastModifiedTime")

            assertEquals(FileType.REGULAR_FILE, attributes.type)
            assertEquals(0L, attributes.size)
            assertEquals(0L, selected["basic:size"])
            assertEquals(attributes.lastModifiedTime, selected["basic:lastModifiedTime"])
            assertEquals(attributes.lastModifiedTime, file.getAttribute("basic:lastModifiedTime"))
            assertTrue(file.fileAttributesView("basic").read().type == FileType.REGULAR_FILE)
            assertNotNull(directory.resolve("missing").fileAttributesViewOrNull("basic"))
        }

    @Test
    fun writableTimeAndPermissionsRoundTrip(): Unit =
        withTemporaryDirectory { directory ->
            // Writable attributes round to milliseconds and return immutable permission snapshots.
            val file = directory.resolve("file").createFile()
            val requestedTime = FileTime(1_700_000_000L, 123_999_999)
            val permissions = setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)

            file.setLastModifiedTime(requestedTime)
            file.setPosixFilePermissions(permissions)

            assertEquals(FileTime(1_700_000_000L, 123_000_000), file.getLastModifiedTime())
            assertEquals(permissions, file.getPosixFilePermissions())
            assertEquals(permissions, file.getAttribute("posix:permissions"))
        }

    @Test
    fun attributeSyntaxRejectsUnknownOrReadOnlyWrites(): Unit =
        withTemporaryDirectory { directory ->
            // Syntax, unknown names, wrong value types, and read-only writes remain distinct argument errors.
            val file = directory.resolve("file").createFile()

            assertFailsWith<IllegalArgumentException> { file.readAttributes("unknown:size") }
            assertFailsWith<IllegalArgumentException> { file.getAttribute("basic:*") }
            assertFailsWith<IllegalArgumentException> { file.setAttribute("basic:lastModifiedTime", "now") }
            assertFailsWith<UnsupportedOperationException> { file.setAttribute("basic:size", 1L) }
            @Suppress("UNCHECKED_CAST")
            val invalidPermissions = setOf<Any?>(null) as Set<PosixFilePermission>
            assertFailsWith<IllegalArgumentException> { file.setPosixFilePermissions(invalidPermissions) }
        }

    @Test
    fun fileStoreProducesBoundedSpaceValues(): Unit =
        withTemporaryDirectory { directory ->
            // Platform volume metadata is a non-negative immutable snapshot when available.
            val store = directory.fileStore()

            assertTrue(store.name.isNotEmpty())
            assertTrue(store.type.isNotEmpty())
            store.totalSpace?.let { assertTrue(it >= 0L) }
            store.usableSpace?.let { usable ->
                assertTrue(usable >= 0L)
                store.totalSpace?.let { assertTrue(usable <= it) }
            }
            store.unallocatedSpace?.let { free ->
                assertTrue(free >= 0L)
                store.totalSpace?.let { assertTrue(free <= it) }
            }
            assertFalse(store.isReadOnly && directory.isWritable())
        }

    @Test
    fun ownerRoundTripsWithoutChangingIdentity(): Unit =
        withTemporaryDirectory { directory ->
            val file = directory.resolve("owner").createFile()
            val owner = file.readAttributes().owner

            if (owner == null) {
                assertFailsWith<UnsupportedOperationException> { file.getOwner() }
            } else {
                assertEquals(file, file.setOwner(owner))
                assertEquals(owner, file.getOwner())
            }
            assertFailsWith<IllegalArgumentException> { file.setOwner("") }
        }
}
