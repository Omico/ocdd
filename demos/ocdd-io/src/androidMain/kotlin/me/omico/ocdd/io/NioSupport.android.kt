@file:Suppress("NewApi") // java.nio.file is supplied on API 21-25 by desugar_jdk_libs_nio.

package me.omico.ocdd.io

import java.nio.file.AccessDeniedException
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileSystemLoopException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.NotDirectoryException
import java.nio.file.Paths

internal fun Path.toNioPath(): java.nio.file.Path = nio

internal fun Array<out LinkOption>.toNioLinkOptions(): Array<java.nio.file.LinkOption> =
    if (LinkOption.NOFOLLOW_LINKS in this) arrayOf(java.nio.file.LinkOption.NOFOLLOW_LINKS) else emptyArray()

internal fun Throwable.toFileSystemException(
    operation: FileSystemOperation,
    path: Path,
    otherPath: Path? = null,
    partialResult: Boolean = false,
    reasonOverride: FileSystemErrorReason? = null,
): FileSystemException {
    if (this is FileSystemException) return this
    val nestedErrnoReason = structuredErrnoReason()
    val reason =
        reasonOverride ?: when (this) {
            is android.system.ErrnoException -> {
                toFileSystemReason()
            }

            is NoSuchFileException, is java.io.FileNotFoundException -> {
                FileSystemErrorReason.NOT_FOUND
            }

            is FileAlreadyExistsException -> {
                FileSystemErrorReason.ALREADY_EXISTS
            }

            is NotDirectoryException -> {
                FileSystemErrorReason.NOT_A_DIRECTORY
            }

            is DirectoryNotEmptyException -> {
                FileSystemErrorReason.DIRECTORY_NOT_EMPTY
            }

            is AccessDeniedException, is SecurityException -> {
                FileSystemErrorReason.ACCESS_DENIED
            }

            is FileSystemLoopException -> {
                FileSystemErrorReason.FILE_SYSTEM_LOOP
            }

            is java.nio.file.FileSystemException -> {
                when {
                    nestedErrnoReason != null -> nestedErrnoReason
                    failurePath(path).hasSymbolicLinkLoop() -> FileSystemErrorReason.FILE_SYSTEM_LOOP
                    file.hasNonDirectoryParent() -> FileSystemErrorReason.NOT_A_DIRECTORY
                    else -> FileSystemErrorReason.IO_FAILURE
                }
            }

            else -> {
                nestedErrnoReason ?: FileSystemErrorReason.IO_FAILURE
            }
        }
    return FileSystemException(
        message = message ?: "$operation failed for $path",
        operation = operation,
        path = path,
        otherPath = otherPath,
        reason = reason,
        partialResult = partialResult,
    )
}

internal inline fun <T> nioOperation(
    operation: FileSystemOperation,
    path: Path,
    otherPath: Path? = null,
    partialResult: Boolean = false,
    reasonOverride: FileSystemErrorReason? = null,
    block: () -> T,
): T =
    try {
        block()
    } catch (exception: UnsupportedOperationException) {
        throw exception
    } catch (exception: IllegalArgumentException) {
        throw exception
    } catch (exception: Throwable) {
        throw exception.toFileSystemException(operation, path, otherPath, partialResult, reasonOverride)
    }

private fun String?.hasNonDirectoryParent(): Boolean {
    val parent = this?.let(Paths::get)?.parent ?: return false
    val nearestExisting =
        generateSequence(parent as java.nio.file.Path?) { it.parent }
            .firstOrNull { Files.exists(it, java.nio.file.LinkOption.NOFOLLOW_LINKS) }
            ?: return false
    return !Files.isDirectory(nearestExisting)
}

private fun Throwable.structuredErrnoReason(): FileSystemErrorReason? =
    generateSequence(cause) { it.cause }
        .filterIsInstance<android.system.ErrnoException>()
        .firstOrNull()
        ?.toFileSystemReason()

private fun java.nio.file.FileSystemException.failurePath(fallback: Path): java.nio.file.Path =
    file?.let(Paths::get) ?: fallback.toNioPath()

private fun java.nio.file.Path.hasSymbolicLinkLoop(): Boolean {
    var loopDetected = false
    val activeLinks = mutableSetOf<java.nio.file.Path>()

    fun resolve(
        value: java.nio.file.Path,
        base: java.nio.file.Path,
    ): java.nio.file.Path? {
        var resolved = if (value.isAbsolute) value.root ?: return null else base
        for (namePath in value) {
            when (val name = namePath.toString()) {
                "." -> {
                    Unit
                }

                ".." -> {
                    resolved = resolved.parent ?: resolved
                }

                else -> {
                    val candidate = resolved.resolve(name)
                    if (!Files.isSymbolicLink(candidate)) {
                        resolved = candidate
                        continue
                    }
                    val identity = candidate.toAbsolutePath().normalize()
                    if (!activeLinks.add(identity)) {
                        loopDetected = true
                        return null
                    }
                    val target = Files.readSymbolicLink(candidate)
                    val targetResult = resolve(target, candidate.parent ?: resolved)
                    activeLinks.remove(identity)
                    resolved = targetResult ?: return null
                }
            }
        }
        return resolved
    }

    return try {
        val absolute = toAbsolutePath()
        resolve(absolute, absolute.root ?: return false)
        loopDetected
    } catch (_: java.io.IOException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
