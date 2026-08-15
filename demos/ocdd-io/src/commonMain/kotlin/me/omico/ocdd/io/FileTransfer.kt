package me.omico.ocdd.io

public enum class FileCopyOption {
    REPLACE_EXISTING,
    COPY_ATTRIBUTES,
    ATOMIC_MOVE,
    NOFOLLOW_LINKS,
}

public enum class CopyActionResult {
    CONTINUE,
    SKIP_SUBTREE,
    TERMINATE,
}

public enum class OnErrorResult {
    SKIP_SUBTREE,
    TERMINATE,
}

public interface CopyActionContext {
    @Throws(IOException::class)
    public fun copyToIgnoringExistingDirectory(
        source: Path,
        target: Path,
        followLinks: Boolean,
    ): CopyActionResult
}

internal class CopyProgress {
    var targetChanged: Boolean = false
}

@Throws(IOException::class)
public expect fun Path.copyTo(
    target: Path,
    vararg options: FileCopyOption,
): Path

@Throws(IOException::class)
public expect fun Path.copyTo(
    target: Path,
    overwrite: Boolean = false,
): Path

@Throws(IOException::class)
public expect fun Path.copyToRecursively(
    target: Path,
    onError: (Path, Path, IOException) -> OnErrorResult = { _, _, exception -> throw exception },
    followLinks: Boolean,
    overwrite: Boolean,
): Path

@Throws(IOException::class)
public expect fun Path.copyToRecursively(
    target: Path,
    onError: (Path, Path, IOException) -> OnErrorResult = { _, _, exception -> throw exception },
    followLinks: Boolean,
    copyAction: CopyActionContext.(Path, Path) -> CopyActionResult = { source, target ->
        copyToIgnoringExistingDirectory(source, target, followLinks)
    },
): Path

@Throws(IOException::class)
public expect fun Path.moveTo(
    target: Path,
    vararg options: FileCopyOption,
): Path

@Throws(IOException::class)
public expect fun Path.moveTo(
    target: Path,
    overwrite: Boolean = false,
): Path

internal fun Path.copyTree(
    target: Path,
    followLinks: Boolean,
    onError: (Path, Path, IOException) -> OnErrorResult,
    progress: CopyProgress,
    context: CopyActionContext,
    copyAction: CopyActionContext.(Path, Path) -> CopyActionResult,
): Path {
    fun handleError(
        source: Path,
        destination: Path,
        exception: IOException,
    ): CopyActionResult =
        when (
            onError(
                source,
                destination,
                if (exception is FileSystemException) {
                    exception.withCopyPaths(source, destination, progress.targetChanged)
                } else {
                    exception
                },
            )
        ) {
            OnErrorResult.SKIP_SUBTREE -> CopyActionResult.SKIP_SUBTREE
            OnErrorResult.TERMINATE -> CopyActionResult.TERMINATE
        }

    try {
        readAttributes(*if (followLinks) emptyArray() else arrayOf(LinkOption.NOFOLLOW_LINKS))
    } catch (exception: IOException) {
        handleError(this, target, exception)
        return target
    }
    requireNonOverlappingTarget(target, followLinks)

    fun copyEntry(
        source: Path,
        destination: Path,
        ancestors: List<Path>,
    ): CopyActionResult {
        val attributes =
            try {
                source.readAttributes(*if (followLinks) emptyArray() else arrayOf(LinkOption.NOFOLLOW_LINKS))
            } catch (exception: IOException) {
                return handleError(source, destination, exception)
            }
        val directory = attributes.type == FileType.DIRECTORY
        val nextAncestors =
            if (directory && followLinks) {
                try {
                    val identity = source.toRealPath()
                    if (identity in ancestors) {
                        throw FileSystemException(
                            "Symbolic link cycle detected",
                            FileSystemOperation.COPY,
                            source,
                            destination,
                            FileSystemErrorReason.FILE_SYSTEM_LOOP,
                        )
                    }
                    ancestors.plusElement(identity)
                } catch (exception: IOException) {
                    return handleError(source, destination, exception)
                }
            } else {
                ancestors
            }
        val destinationExisted = destination.exists(LinkOption.NOFOLLOW_LINKS)

        fun captureCreatedDestination() {
            if (!destinationExisted && destination.exists(LinkOption.NOFOLLOW_LINKS)) {
                progress.targetChanged = true
            }
        }

        val result =
            try {
                context.copyAction(source, destination)
            } catch (exception: IOException) {
                captureCreatedDestination()
                handleError(source, destination, exception)
            }
        captureCreatedDestination()
        when (result) {
            CopyActionResult.TERMINATE -> return result
            CopyActionResult.SKIP_SUBTREE -> if (directory) return result
            CopyActionResult.CONTINUE, CopyActionResult.SKIP_SUBTREE -> Unit
        }
        if (!directory) return CopyActionResult.CONTINUE

        val children =
            try {
                source.listDirectoryEntries()
            } catch (exception: IOException) {
                return handleError(source, destination, exception)
            }
        for (child in children) {
            val childTarget = destination.resolve(child.name)
            if (copyEntry(child, childTarget, nextAncestors) == CopyActionResult.TERMINATE) {
                return CopyActionResult.TERMINATE
            }
        }
        return CopyActionResult.CONTINUE
    }

    copyEntry(this, target, emptyList())
    return target
}

internal fun Path.copyTree(
    target: Path,
    followLinks: Boolean,
    overwrite: Boolean,
    onError: (Path, Path, IOException) -> OnErrorResult,
): Path {
    val progress = CopyProgress()
    val context =
        object : CopyActionContext {
            override fun copyToIgnoringExistingDirectory(
                source: Path,
                target: Path,
                followLinks: Boolean,
            ): CopyActionResult =
                source.copyEntry(target, followLinks, overwrite) {
                    progress.targetChanged = true
                }
        }
    return copyTree(target, followLinks, onError, progress, context) { source, destination ->
        copyToIgnoringExistingDirectory(source, destination, followLinks)
    }
}

internal fun Path.copyEntry(
    target: Path,
    followLinks: Boolean,
    overwrite: Boolean,
    onTargetChange: () -> Unit = {},
): CopyActionResult {
    val sourceDirectory = isDirectory(*if (followLinks) emptyArray() else arrayOf(LinkOption.NOFOLLOW_LINKS))
    val targetDirectory = target.isDirectory(LinkOption.NOFOLLOW_LINKS)
    if (sourceDirectory && targetDirectory) return CopyActionResult.CONTINUE
    val options =
        buildList {
            if (overwrite) add(FileCopyOption.REPLACE_EXISTING)
            if (!followLinks) add(FileCopyOption.NOFOLLOW_LINKS)
        }
    copyTo(target, options = options.toTypedArray())
    onTargetChange()
    return CopyActionResult.CONTINUE
}

private fun Path.requireNonOverlappingTarget(
    target: Path,
    followLinks: Boolean,
) {
    val sourceLexicalLocation = absolute().normalize()
    val sourceRealLocation = if (!followLinks && isSymbolicLink()) sourceLexicalLocation else toRealPath()
    val targetLocation = target.locationThroughNearestExistingAncestor()
    require(
        targetLocation != sourceLexicalLocation &&
            !targetLocation.startsWith(sourceLexicalLocation) &&
            targetLocation != sourceRealLocation &&
            !targetLocation.startsWith(sourceRealLocation),
    ) {
        "Recursive copy target must not be the source or one of its descendants"
    }
}

private fun Path.locationThroughNearestExistingAncestor(): Path {
    val missingNames = mutableListOf<String>()
    var existing: Path? = this
    while (existing != null && existing.notExists(LinkOption.NOFOLLOW_LINKS)) {
        existing.fileName?.toString()?.let(missingNames::add)
        existing = existing.parent
    }
    if (existing == null || (existing.isSymbolicLink() && existing.notExists())) return absolute().normalize()
    return missingNames
        .asReversed()
        .fold(existing.toRealPath()) { location, name -> location.resolve(name) }
        .normalize()
}

private fun FileSystemException.withCopyPaths(
    source: Path,
    target: Path,
    priorPartialResult: Boolean = false,
): FileSystemException =
    FileSystemException(
        message.orEmpty(),
        operation =
            if (operation == FileSystemOperation.LIST || operation == FileSystemOperation.REAL_PATH) {
                FileSystemOperation.COPY
            } else {
                operation
            },
        path = source,
        otherPath = target,
        reason = reason,
        partialResult = partialResult || priorPartialResult,
    )
