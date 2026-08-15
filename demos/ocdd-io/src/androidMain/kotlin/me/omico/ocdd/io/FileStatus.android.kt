package me.omico.ocdd.io

import android.system.Os
import android.system.OsConstants
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes

@Throws(IOException::class)
public actual fun Path.absolute(): Path =
    nioOperation(FileSystemOperation.ABSOLUTE_PATH, this) {
        toNioPath().toAbsolutePath().toString().toPath()
    }

@Throws(IOException::class)
public actual fun Path.absolutePathString(): String = absolute().toString()

@Throws(IOException::class)
public actual fun Path.toRealPath(vararg options: LinkOption): Path =
    nioOperation(FileSystemOperation.REAL_PATH, this) {
        if (!usesAndroidPosixFallback) {
            return@nioOperation toNioPath().toRealPath(*options.toNioLinkOptions()).toString().toPath()
        }
        if (LinkOption.NOFOLLOW_LINKS in options) {
            androidStat(noFollowLinks = true)
            normalizeAndroidWithoutFollowingLinks()
        } else {
            androidStat(noFollowLinks = false)
            toNioPath().toFile().canonicalPath.toPath()
        }
    }

public actual fun Path.exists(vararg options: LinkOption): Boolean =
    if (usesAndroidPosixFallback) {
        androidStatus(options) { true }
    } else {
        Files.exists(toNioPath(), *options.toNioLinkOptions())
    }

public actual fun Path.notExists(vararg options: LinkOption): Boolean {
    if (!usesAndroidPosixFallback) return Files.notExists(toNioPath(), *options.toNioLinkOptions())
    return try {
        androidStat(LinkOption.NOFOLLOW_LINKS in options)
        false
    } catch (exception: android.system.ErrnoException) {
        exception.errno == OsConstants.ENOENT || exception.errno == OsConstants.ENOTDIR
    }
}

public actual fun Path.isDirectory(vararg options: LinkOption): Boolean =
    if (usesAndroidPosixFallback) {
        androidStatus(options) { OsConstants.S_ISDIR(st_mode) }
    } else {
        Files.isDirectory(toNioPath(), *options.toNioLinkOptions())
    }

public actual fun Path.isRegularFile(vararg options: LinkOption): Boolean =
    if (usesAndroidPosixFallback) {
        androidStatus(options) { OsConstants.S_ISREG(st_mode) }
    } else {
        Files.isRegularFile(toNioPath(), *options.toNioLinkOptions())
    }

public actual fun Path.isSymbolicLink(): Boolean =
    if (usesAndroidPosixFallback) {
        androidStatus(arrayOf(LinkOption.NOFOLLOW_LINKS)) { OsConstants.S_ISLNK(st_mode) }
    } else {
        Files.isSymbolicLink(toNioPath())
    }

public actual fun Path.isReadable(): Boolean =
    if (usesAndroidPosixFallback) androidAccess(OsConstants.R_OK) else Files.isReadable(toNioPath())

public actual fun Path.isWritable(): Boolean =
    if (usesAndroidPosixFallback) androidAccess(OsConstants.W_OK) else Files.isWritable(toNioPath())

public actual fun Path.isExecutable(): Boolean =
    if (usesAndroidPosixFallback) androidAccess(OsConstants.X_OK) else Files.isExecutable(toNioPath())

@Throws(IOException::class)
public actual fun Path.isSameFileAs(other: Path): Boolean =
    nioOperation(FileSystemOperation.STATUS, this, other) {
        if (this == other) return@nioOperation true
        if (usesAndroidPosixFallback) {
            val first = androidStat(noFollowLinks = false)
            val second = other.androidStat(noFollowLinks = false)
            first.st_dev == second.st_dev && first.st_ino == second.st_ino
        } else {
            Files.isSameFile(toNioPath(), other.toNioPath())
        }
    }

@Throws(IOException::class)
public actual fun Path.fileSize(): Long =
    nioOperation(FileSystemOperation.STATUS, this) {
        if (usesAndroidPosixFallback) {
            val status = androidStat(noFollowLinks = false)
            if (OsConstants.S_ISDIR(status.st_mode)) {
                throw FileSystemException(
                    message = "Cannot read a directory as a regular file",
                    operation = FileSystemOperation.STATUS,
                    path = this,
                    reason = FileSystemErrorReason.IS_A_DIRECTORY,
                )
            }
            if (!OsConstants.S_ISREG(status.st_mode)) {
                throw FileSystemException(
                    message = "Path is not a regular file",
                    operation = FileSystemOperation.STATUS,
                    path = this,
                    reason = FileSystemErrorReason.IO_FAILURE,
                )
            }
            return@nioOperation status.st_size
        }
        val attributes = Files.readAttributes(toNioPath(), BasicFileAttributes::class.java)
        if (attributes.isDirectory) {
            throw FileSystemException(
                message = "Cannot read a directory as a regular file",
                operation = FileSystemOperation.STATUS,
                path = this,
                reason = FileSystemErrorReason.IS_A_DIRECTORY,
            )
        }
        if (!attributes.isRegularFile) {
            throw FileSystemException(
                message = "Path is not a regular file",
                operation = FileSystemOperation.STATUS,
                path = this,
                reason = FileSystemErrorReason.IO_FAILURE,
            )
        }
        attributes.size()
    }

@Throws(IOException::class)
public actual fun Path.readSymbolicLink(): Path =
    nioOperation(FileSystemOperation.STATUS, this) {
        if (!isSymbolicLink()) {
            if (notExists(LinkOption.NOFOLLOW_LINKS)) {
                throw FileSystemException(
                    "Path does not exist",
                    FileSystemOperation.STATUS,
                    this,
                    reason = FileSystemErrorReason.NOT_FOUND,
                )
            }
            throw FileSystemException(
                "Path is not a symbolic link",
                FileSystemOperation.STATUS,
                this,
                reason = FileSystemErrorReason.NOT_A_SYMBOLIC_LINK,
            )
        }
        if (usesAndroidPosixFallback) {
            Os.readlink(androidPathString()).toPath()
        } else {
            Files.readSymbolicLink(toNioPath()).toString().toPath()
        }
    }

private inline fun Path.androidStatus(
    options: Array<out LinkOption>,
    predicate: android.system.StructStat.() -> Boolean,
): Boolean =
    try {
        androidStat(LinkOption.NOFOLLOW_LINKS in options).predicate()
    } catch (_: Throwable) {
        false
    }

private fun Path.androidAccess(mode: Int): Boolean =
    try {
        Os.access(androidPathString(), mode)
    } catch (_: Throwable) {
        false
    }

private fun Path.normalizeAndroidWithoutFollowingLinks(): Path {
    val absolute = absolute()
    val normalized = mutableListOf<String>()
    absolute.segments.forEach { segment ->
        when {
            segment == "." -> {
                Unit
            }

            segment == ".." && normalized.isNotEmpty() -> {
                val prefix = ("/" + normalized.joinToString("/")).toPath()
                if (prefix.isSymbolicLink()) normalized += segment else normalized.removeAt(normalized.lastIndex)
            }

            segment == ".." -> {
                Unit
            }

            else -> {
                normalized += segment
            }
        }
    }
    return ("/" + normalized.joinToString("/")).toPath()
}
