@file:OptIn(ExperimentalForeignApi::class)

package me.omico.ocdd.io

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.posix.R_OK
import platform.posix.W_OK
import platform.posix.X_OK
import platform.posix.access

@Throws(IOException::class)
public actual fun Path.absolute(): Path =
    iosOperation(FileSystemOperation.ABSOLUTE_PATH, this) {
        if (isAbsolute) {
            this
        } else {
            NSFileManager.defaultManager.currentDirectoryPath
                .toPath()
                .resolve(this)
        }
    }

@Throws(IOException::class)
public actual fun Path.absolutePathString(): String = absolute().toString()

@Throws(IOException::class)
public actual fun Path.toRealPath(vararg options: LinkOption): Path =
    iosOperation(FileSystemOperation.REAL_PATH, this) {
        if (LinkOption.NOFOLLOW_LINKS !in options) {
            readIosFileMetadata(followLinks = true, operation = FileSystemOperation.REAL_PATH)
            systemFileSystem.canonicalize(toOkioPath()).toString().toPath()
        } else {
            readIosFileMetadata(followLinks = false, operation = FileSystemOperation.REAL_PATH)
            normalizeWithoutFollowingLinks()
        }
    }

public actual fun Path.exists(vararg options: LinkOption): Boolean = statusBoolean(options) { true }

public actual fun Path.notExists(vararg options: LinkOption): Boolean {
    val follow = LinkOption.NOFOLLOW_LINKS !in options
    return try {
        readIosFileMetadata(follow, FileSystemOperation.STATUS)
        false
    } catch (exception: FileSystemException) {
        exception.reason == FileSystemErrorReason.NOT_FOUND || exception.reason == FileSystemErrorReason.NOT_A_DIRECTORY
    }
}

public actual fun Path.isDirectory(vararg options: LinkOption): Boolean = statusBoolean(options) { type == FileType.DIRECTORY }

public actual fun Path.isRegularFile(vararg options: LinkOption): Boolean = statusBoolean(options) { type == FileType.REGULAR_FILE }

public actual fun Path.isSymbolicLink(): Boolean =
    statusBoolean(arrayOf(LinkOption.NOFOLLOW_LINKS)) {
        type == FileType.SYMBOLIC_LINK
    }

public actual fun Path.isReadable(): Boolean = access(toString(), R_OK) == 0

public actual fun Path.isWritable(): Boolean = access(toString(), W_OK) == 0

public actual fun Path.isExecutable(): Boolean = access(toString(), X_OK) == 0

@Throws(IOException::class)
public actual fun Path.isSameFileAs(other: Path): Boolean {
    if (this == other) return true
    val first = readIosFileMetadata(followLinks = true, operation = FileSystemOperation.STATUS, otherPath = other)
    val second = other.readIosFileMetadata(followLinks = true, operation = FileSystemOperation.STATUS, otherPath = this)
    return first.device == second.device && first.inode == second.inode
}

@Throws(IOException::class)
public actual fun Path.fileSize(): Long {
    val status = readIosFileMetadata(followLinks = true, operation = FileSystemOperation.STATUS)
    if (status.type == FileType.DIRECTORY) {
        throw FileSystemException(
            "Path is a directory",
            FileSystemOperation.STATUS,
            this,
            reason = FileSystemErrorReason.IS_A_DIRECTORY,
        )
    }
    if (status.type != FileType.REGULAR_FILE) {
        throw FileSystemException(
            "Path is not a regular file",
            FileSystemOperation.STATUS,
            this,
            reason = FileSystemErrorReason.IO_FAILURE,
        )
    }
    return status.size
}

@Throws(IOException::class)
public actual fun Path.readSymbolicLink(): Path {
    val status = readIosFileMetadata(followLinks = false, operation = FileSystemOperation.STATUS)
    if (status.type != FileType.SYMBOLIC_LINK) {
        throw FileSystemException(
            "Path is not a symbolic link",
            FileSystemOperation.STATUS,
            this,
            reason = FileSystemErrorReason.NOT_A_SYMBOLIC_LINK,
        )
    }
    val target =
        iosOperation(FileSystemOperation.STATUS, this) {
            NSFileManager.defaultManager.destinationOfSymbolicLinkAtPath(toString(), error = null)
                ?: throw FileSystemException(
                    "Unable to read symbolic link target",
                    FileSystemOperation.STATUS,
                    this,
                    reason = FileSystemErrorReason.IO_FAILURE,
                )
        }
    return target.toPath()
}

private inline fun Path.statusBoolean(
    options: Array<out LinkOption>,
    predicate: IosFileMetadata.() -> Boolean,
): Boolean =
    try {
        readIosFileMetadata(LinkOption.NOFOLLOW_LINKS !in options, FileSystemOperation.STATUS).predicate()
    } catch (_: FileSystemException) {
        false
    }

private fun Path.normalizeWithoutFollowingLinks(): Path {
    val absolute = absolute()
    val result = mutableListOf<String>()
    absolute.segments.forEach { segment ->
        when {
            segment == "." -> {
                Unit
            }

            segment == ".." && result.isNotEmpty() -> {
                val prefix = ("/" + result.joinToString("/")).toPath()
                if (prefix.isSymbolicLink()) result += segment else result.removeAt(result.lastIndex)
            }

            segment == ".." -> {
                Unit
            }

            else -> {
                result += segment
            }
        }
    }
    return ("/" + result.joinToString("/")).toPath()
}
