package me.omico.ocdd.io

import okio.buffer

@Throws(IOException::class)
public actual fun Path.copyTo(
    target: Path,
    vararg options: FileCopyOption,
): Path {
    val validated = options.toSet()
    require(FileCopyOption.ATOMIC_MOVE !in validated) { "ATOMIC_MOVE is not valid for copying" }
    val replace = FileCopyOption.REPLACE_EXISTING in validated
    val followLinks = FileCopyOption.NOFOLLOW_LINKS !in validated
    val copyAttributes = FileCopyOption.COPY_ATTRIBUTES in validated
    val sourceIsLink = isSymbolicLink()
    val effectiveSource =
        try {
            if (followLinks && sourceIsLink) toRealPath() else this
        } catch (exception: FileSystemException) {
            throw exception.asTransferFailure(FileSystemOperation.COPY, this, target)
        }
    val sourceAttributes =
        try {
            effectiveSource.readAttributes(*if (followLinks) emptyArray() else arrayOf(LinkOption.NOFOLLOW_LINKS))
        } catch (exception: FileSystemException) {
            throw exception.asTransferFailure(FileSystemOperation.COPY, this, target)
        }
    val symbolicLinkTarget = if (!followLinks && sourceIsLink) readSymbolicLink() else null
    if (copyAttributes && !followLinks && sourceIsLink) {
        throw UnsupportedOperationException("iOS cannot copy writable attributes to a symbolic link without following it")
    }
    var changed = false
    return try {
        iosOperation(FileSystemOperation.COPY, this, target) {
            if (target.exists(LinkOption.NOFOLLOW_LINKS)) {
                if (!replace) {
                    throw FileSystemException(
                        "Target already exists",
                        FileSystemOperation.COPY,
                        this,
                        target,
                        FileSystemErrorReason.ALREADY_EXISTS,
                    )
                }
                if (target.isDirectory(LinkOption.NOFOLLOW_LINKS) && target.listDirectoryEntries().isNotEmpty()) {
                    throw FileSystemException(
                        "Target directory is not empty",
                        FileSystemOperation.COPY,
                        this,
                        target,
                        FileSystemErrorReason.DIRECTORY_NOT_EMPTY,
                    )
                }
                target.deleteExisting()
                changed = true
            }
            when (sourceAttributes.type) {
                FileType.SYMBOLIC_LINK -> {
                    changed = true
                    target.createSymbolicLinkPointingTo(requireNotNull(symbolicLinkTarget))
                }

                FileType.DIRECTORY -> {
                    changed = true
                    target.createDirectory()
                }

                FileType.REGULAR_FILE -> {
                    changed = true
                    effectiveSource.copyRegularFileTo(target)
                }

                FileType.OTHER -> {
                    throw FileSystemException(
                        "Unsupported file type",
                        FileSystemOperation.COPY,
                        this,
                        target,
                        FileSystemErrorReason.IO_FAILURE,
                    )
                }
            }
            if (copyAttributes) effectiveSource.copyWritableAttributesTo(target)
            target
        }
    } catch (exception: FileSystemException) {
        if (!changed) throw exception
        throw FileSystemException(
            exception.message.orEmpty(),
            exception.operation,
            exception.path,
            exception.otherPath,
            exception.reason,
            partialResult = true,
        )
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
    val replace = FileCopyOption.REPLACE_EXISTING in validated
    val atomic = FileCopyOption.ATOMIC_MOVE in validated
    try {
        readAttributes(LinkOption.NOFOLLOW_LINKS)
    } catch (exception: FileSystemException) {
        throw exception.asTransferFailure(FileSystemOperation.MOVE, this, target)
    }
    if (target.exists(LinkOption.NOFOLLOW_LINKS)) {
        if (!replace) {
            throw FileSystemException(
                "Target already exists",
                FileSystemOperation.MOVE,
                this,
                target,
                FileSystemErrorReason.ALREADY_EXISTS,
            )
        }
        if (target.isDirectory(LinkOption.NOFOLLOW_LINKS) && target.listDirectoryEntries().isNotEmpty()) {
            throw FileSystemException(
                "Target directory is not empty",
                FileSystemOperation.MOVE,
                this,
                target,
                FileSystemErrorReason.DIRECTORY_NOT_EMPTY,
            )
        }
    }
    return try {
        iosOperation(FileSystemOperation.MOVE, this, target) {
            systemFileSystem.atomicMove(toOkioPath(), target.toOkioPath())
            target
        }
    } catch (exception: FileSystemException) {
        if (atomic) {
            if (exception.reason == FileSystemErrorReason.IO_FAILURE) {
                throw UnsupportedOperationException("Atomic move is unavailable", exception)
            }
            throw exception
        }
        copyToRecursively(target, followLinks = false, overwrite = replace)
        deleteRecursively()
        target
    }
}

@Throws(IOException::class)
public actual fun Path.moveTo(
    target: Path,
    overwrite: Boolean,
): Path = if (overwrite) moveTo(target, FileCopyOption.REPLACE_EXISTING) else moveTo(target, *emptyArray())

private fun Path.copyRegularFileTo(target: Path) {
    val source = systemFileSystem.source(toOkioPath()).buffer()
    val sink = systemFileSystem.sink(target.toOkioPath(), mustCreate = true).buffer()
    try {
        sink.writeAll(source)
    } finally {
        var failure: Throwable? = null
        try {
            source.close()
        } catch (exception: Throwable) {
            failure = exception
        }
        try {
            sink.close()
        } catch (exception: Throwable) {
            if (failure == null) failure = exception else failure.addSuppressed(exception)
        }
        failure?.let { throw it }
    }
}

private fun Path.copyWritableAttributesTo(target: Path) {
    val attributes = readAttributes()
    target.setLastModifiedTime(attributes.lastModifiedTime)
    attributes.permissions?.let(target::setPosixFilePermissions)
    attributes.owner?.let(target::setOwner)
}

private fun FileSystemException.asTransferFailure(
    operation: FileSystemOperation,
    source: Path,
    target: Path,
): FileSystemException =
    FileSystemException(
        message.orEmpty(),
        operation,
        source,
        target,
        reason,
        partialResult,
    )
