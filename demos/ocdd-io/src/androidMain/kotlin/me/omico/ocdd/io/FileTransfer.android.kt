@file:Suppress("NewApi") // Copy/move options are supplied on API 21-25 by desugar_jdk_libs_nio.

package me.omico.ocdd.io

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.CopyOption
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Throws(IOException::class)
public actual fun Path.copyTo(
    target: Path,
    vararg options: FileCopyOption,
): Path {
    val validated = options.toSet()
    require(FileCopyOption.ATOMIC_MOVE !in validated) { "ATOMIC_MOVE is not valid for copying" }
    if (usesAndroidPosixFallback) return copyToWithAndroidFallback(target, validated)
    return nioOperation(FileSystemOperation.COPY, this, target) {
        Files.copy(toNioPath(), target.toNioPath(), *validated.toNioCopyOptions())
        target
    }
}

@Throws(IOException::class)
public actual fun Path.copyTo(
    target: Path,
    overwrite: Boolean,
): Path = if (overwrite) copyTo(target, FileCopyOption.REPLACE_EXISTING) else copyTo(target, *emptyArray())

@Throws(IOException::class)
public actual fun Path.copyToRecursively(
    target: Path,
    onError: (Path, Path, IOException) -> OnErrorResult,
    followLinks: Boolean,
    overwrite: Boolean,
): Path = copyTree(target, followLinks, overwrite, onError)

@Throws(IOException::class)
public actual fun Path.copyToRecursively(
    target: Path,
    onError: (Path, Path, IOException) -> OnErrorResult,
    followLinks: Boolean,
    copyAction: CopyActionContext.(Path, Path) -> CopyActionResult,
): Path {
    val progress = CopyProgress()
    val context =
        object : CopyActionContext {
            override fun copyToIgnoringExistingDirectory(
                source: Path,
                target: Path,
                followLinks: Boolean,
            ): CopyActionResult =
                source.copyEntry(target, followLinks, overwrite = false) {
                    progress.targetChanged = true
                }
        }
    return copyTree(target, followLinks, onError, progress, context, copyAction)
}

@Throws(IOException::class)
public actual fun Path.moveTo(
    target: Path,
    vararg options: FileCopyOption,
): Path {
    val validated = options.toSet()
    require(FileCopyOption.COPY_ATTRIBUTES !in validated && FileCopyOption.NOFOLLOW_LINKS !in validated) {
        "COPY_ATTRIBUTES and NOFOLLOW_LINKS are not valid for moving"
    }
    val nioOptions =
        buildList<CopyOption> {
            if (FileCopyOption.REPLACE_EXISTING in validated) add(StandardCopyOption.REPLACE_EXISTING)
            if (FileCopyOption.ATOMIC_MOVE in validated) add(StandardCopyOption.ATOMIC_MOVE)
        }.toTypedArray()
    return try {
        nioOperation(FileSystemOperation.MOVE, this, target) {
            Files.move(toNioPath(), target.toNioPath(), *nioOptions)
            target
        }
    } catch (exception: FileSystemException) {
        if (exception.reason == FileSystemErrorReason.IO_FAILURE && FileCopyOption.ATOMIC_MOVE in validated) {
            throw UnsupportedOperationException("Atomic move is unavailable", exception)
        }
        throw exception
    } catch (exception: AtomicMoveNotSupportedException) {
        throw UnsupportedOperationException("Atomic move is unavailable", exception)
    }
}

@Throws(IOException::class)
public actual fun Path.moveTo(
    target: Path,
    overwrite: Boolean,
): Path = if (overwrite) moveTo(target, FileCopyOption.REPLACE_EXISTING) else moveTo(target, *emptyArray())

private fun Set<FileCopyOption>.toNioCopyOptions(): Array<CopyOption> =
    buildList {
        if (FileCopyOption.REPLACE_EXISTING in this@toNioCopyOptions) add(StandardCopyOption.REPLACE_EXISTING)
        if (FileCopyOption.COPY_ATTRIBUTES in this@toNioCopyOptions) add(StandardCopyOption.COPY_ATTRIBUTES)
        if (FileCopyOption.NOFOLLOW_LINKS in this@toNioCopyOptions) add(java.nio.file.LinkOption.NOFOLLOW_LINKS)
    }.toTypedArray()

private fun Path.copyToWithAndroidFallback(
    target: Path,
    options: Set<FileCopyOption>,
): Path {
    val targetExisted = target.exists(LinkOption.NOFOLLOW_LINKS)
    var changed = false
    return try {
        val noFollowLinks = FileCopyOption.NOFOLLOW_LINKS in options
        val copyAttributes = FileCopyOption.COPY_ATTRIBUTES in options
        val sourceSnapshot = readAttributes(*if (noFollowLinks) arrayOf(LinkOption.NOFOLLOW_LINKS) else emptyArray())
        val symbolicLink = sourceSnapshot.type == FileType.SYMBOLIC_LINK
        if (symbolicLink && copyAttributes) {
            throw UnsupportedOperationException("Copying symbolic-link attributes is unavailable on Android API 21-25")
        }
        val snapshot = sourceSnapshot.takeIf { copyAttributes }
        if (symbolicLink) {
            val linkTarget = readSymbolicLink()
            if (target.exists(LinkOption.NOFOLLOW_LINKS)) {
                if (FileCopyOption.REPLACE_EXISTING !in options) {
                    throw FileSystemException(
                        "Target already exists",
                        FileSystemOperation.COPY,
                        this,
                        target,
                        FileSystemErrorReason.ALREADY_EXISTS,
                    )
                }
                target.deleteExisting()
                changed = true
            }
            target.createSymbolicLinkPointingTo(linkTarget)
            changed = true
        } else if (sourceSnapshot.type == FileType.DIRECTORY) {
            changed =
                prepareTargetForAndroidDirectoryCopy(
                    target,
                    replace = FileCopyOption.REPLACE_EXISTING in options,
                )
            target.createDirectory()
            changed = true
        } else {
            changed = prepareTargetForAndroidFileCopy(target, replace = FileCopyOption.REPLACE_EXISTING in options)
            Files.copy(toNioPath(), target.toNioPath())
            changed = true
        }

        snapshot?.let { attributes ->
            target.setLastModifiedTime(attributes.lastModifiedTime)
            attributes.permissions?.let(target::setPosixFilePermissions)
        }
        target
    } catch (exception: UnsupportedOperationException) {
        throw exception
    } catch (exception: IllegalArgumentException) {
        throw exception
    } catch (exception: Throwable) {
        val failure = exception.toFileSystemException(FileSystemOperation.COPY, this, target)
        val targetWasCreated = !targetExisted && target.exists(LinkOption.NOFOLLOW_LINKS)
        throw FileSystemException(
            failure.message.orEmpty(),
            FileSystemOperation.COPY,
            this,
            target,
            failure.reason,
            partialResult = changed || targetWasCreated || failure.partialResult,
        )
    }
}

private fun Path.prepareTargetForAndroidFileCopy(
    target: Path,
    replace: Boolean,
): Boolean {
    if (!target.exists(LinkOption.NOFOLLOW_LINKS)) return false
    if (!replace) {
        throw FileSystemException(
            "Target already exists",
            FileSystemOperation.COPY,
            path = this,
            otherPath = target,
            reason = FileSystemErrorReason.ALREADY_EXISTS,
        )
    }
    if (target.isDirectory(LinkOption.NOFOLLOW_LINKS) && target.listDirectoryEntries().isNotEmpty()) {
        throw FileSystemException(
            "Target directory is not empty",
            FileSystemOperation.COPY,
            path = this,
            otherPath = target,
            reason = FileSystemErrorReason.DIRECTORY_NOT_EMPTY,
        )
    }
    target.deleteExisting()
    return true
}

private fun Path.prepareTargetForAndroidDirectoryCopy(
    target: Path,
    replace: Boolean,
): Boolean {
    if (!target.exists(LinkOption.NOFOLLOW_LINKS)) return false
    if (!replace) {
        throw FileSystemException(
            "Target already exists",
            FileSystemOperation.COPY,
            path = this,
            otherPath = target,
            reason = FileSystemErrorReason.ALREADY_EXISTS,
        )
    }
    if (target.isDirectory(LinkOption.NOFOLLOW_LINKS) && target.listDirectoryEntries().isNotEmpty()) {
        throw FileSystemException(
            "Target directory is not empty",
            FileSystemOperation.COPY,
            path = this,
            otherPath = target,
            reason = FileSystemErrorReason.DIRECTORY_NOT_EMPTY,
        )
    }
    target.deleteExisting()
    return true
}
