package me.omico.ocdd.io

import android.content.Context
import android.os.Build
import android.system.OsConstants
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AndroidPlatformTest {
    @Test
    fun defaultTemporaryEntriesUseRuntimeTemporaryDirectory() {
        val expectedParent = requireNotNull(System.getProperty("java.io.tmpdir")).toPath().absolute().normalize()
        val applicationCache =
            ApplicationProvider
                .getApplicationContext<Context>()
                .cacheDir.path
                .toPath()
                .absolute()
                .normalize()
        assertEquals(applicationCache, expectedParent)
        val file = createTempFile(prefix = "ocdd-")
        val directory = createTempDirectory(prefix = "ocdd-")
        try {
            assertEquals(expectedParent, file.parent?.absolute()?.normalize())
            assertEquals(expectedParent, directory.parent?.absolute()?.normalize())
        } finally {
            file.deleteIfExists()
            directory.deleteIfExists()
        }
    }

    @Test
    fun connectedDeviceUsesExpectedPlatformBranch() {
        // Runtime dispatch changes exactly at the native Java NIO boundary.
        assertEquals(Build.VERSION.SDK_INT < 26, usesAndroidPosixFallback)
    }

    @Test
    fun lowApiFallbackCreatesLinksAndPermissionsAtomically() {
        // API 21-25 delegates desugaring gaps to android.system.Os.
        assumeTrue(usesAndroidPosixFallback)
        val directory = createTempDirectory(prefix = "ocdd-low-api-")
        try {
            val permissions =
                setOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                )
            val file = directory.resolve("file").createFile(FileAttribute("posix:permissions", permissions))
            val childDirectory =
                directory.resolve("child").createDirectory(FileAttribute("posix:permissions", permissions))
            assertEquals(permissions, file.getPosixFilePermissions())
            assertEquals(permissions, childDirectory.getPosixFilePermissions())

            val hardLink = directory.resolve("hard-link")
            try {
                hardLink.createLinkPointingTo(file)
                assertTrue(file.isSameFileAs(hardLink))
            } catch (exception: FileSystemException) {
                assertEquals(FileSystemErrorReason.ACCESS_DENIED, exception.reason)
                assertFalse(hardLink.exists(LinkOption.NOFOLLOW_LINKS))
            }
            val symbolicLink = directory.resolve("symbolic-link").createSymbolicLinkPointingTo(file.fileName!!)
            assertTrue(OsConstants.S_ISLNK(symbolicLink.androidStat(noFollowLinks = true).st_mode))
            assertTrue(OsConstants.S_ISREG(symbolicLink.androidStat(noFollowLinks = false).st_mode))
            assertEquals(file.fileName, symbolicLink.readSymbolicLink())

            assertNull(file.fileAttributesViewOrNull("owner"))
            assertFailsWith<UnsupportedOperationException> { file.getOwner() }
            val rejectedLink = directory.resolve("rejected-link")
            assertFailsWith<UnsupportedOperationException> {
                rejectedLink.createSymbolicLinkPointingTo(file, FileAttribute("posix:permissions", permissions))
            }
            assertFalse(rejectedLink.exists(LinkOption.NOFOLLOW_LINKS))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun lowApiFallbackCopiesWritableAttributes() {
        // The fallback copies whole-second time and POSIX permissions after content.
        assumeTrue(usesAndroidPosixFallback)
        val directory = createTempDirectory(prefix = "ocdd-low-copy-")
        try {
            val permissions = setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
            val time = FileTime(1_700_000_000L)
            val source = directory.resolve("source").also { it.writeText("content") }
            source.setLastModifiedTime(time)
            source.setPosixFilePermissions(permissions)

            val target = directory.resolve("target")
            source.copyTo(target, FileCopyOption.COPY_ATTRIBUTES)

            assertEquals("content", target.readText())
            assertEquals(time, target.getLastModifiedTime())
            assertEquals(permissions, target.getPosixFilePermissions())
        } finally {
            directory.deleteRecursively()
        }
    }
}
