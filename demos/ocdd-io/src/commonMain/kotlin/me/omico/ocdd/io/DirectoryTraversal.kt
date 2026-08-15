package me.omico.ocdd.io

public enum class PathWalkOption {
    INCLUDE_DIRECTORIES,
    BREADTH_FIRST,
    FOLLOW_LINKS,
}

public enum class FileVisitResult {
    CONTINUE,
    SKIP_SUBTREE,
    SKIP_SIBLINGS,
    TERMINATE,
}

public interface FileVisitor {
    public fun preVisitDirectory(
        directory: Path,
        attributes: FileAttributes,
    ): FileVisitResult

    public fun visitFile(
        file: Path,
        attributes: FileAttributes,
    ): FileVisitResult

    public fun visitFileFailed(
        file: Path,
        exception: IOException,
    ): FileVisitResult

    public fun postVisitDirectory(
        directory: Path,
        exception: IOException?,
    ): FileVisitResult
}

public expect class FileVisitorBuilder internal constructor() {
    public fun onPreVisitDirectory(action: (Path, FileAttributes) -> FileVisitResult)

    public fun onVisitFile(action: (Path, FileAttributes) -> FileVisitResult)

    public fun onVisitFileFailed(action: (Path, IOException) -> FileVisitResult)

    public fun onPostVisitDirectory(action: (Path, IOException?) -> FileVisitResult)
}

@Throws(IOException::class)
public expect fun Path.listDirectoryEntries(glob: String = "*"): List<Path>

@Throws(IOException::class)
public expect fun Path.forEachDirectoryEntry(
    glob: String = "*",
    action: (Path) -> Unit,
)

@Throws(IOException::class)
public expect fun <T> Path.useDirectoryEntries(
    glob: String = "*",
    block: (Sequence<Path>) -> T,
): T

public expect fun fileVisitor(builderAction: FileVisitorBuilder.() -> Unit): FileVisitor

@Throws(IOException::class)
public expect fun Path.visitFileTree(
    visitor: FileVisitor,
    maxDepth: Int = Int.MAX_VALUE,
    followLinks: Boolean = false,
)

@Throws(IOException::class)
public expect fun Path.visitFileTree(
    maxDepth: Int = Int.MAX_VALUE,
    followLinks: Boolean = false,
    builderAction: FileVisitorBuilder.() -> Unit,
)

@Throws(IOException::class)
public expect fun Path.walk(vararg options: PathWalkOption): Sequence<Path>

internal fun <T> List<Path>.useAsDirectoryEntries(block: (Sequence<Path>) -> T): T {
    var valid = true
    val entries =
        sequence {
            check(valid) { "Directory entry sequence is no longer valid" }
            this@useAsDirectoryEntries.forEach { entry ->
                check(valid) { "Directory entry sequence is no longer valid" }
                yield(entry)
            }
        }.constrainOnce()
    return try {
        block(entries)
    } finally {
        valid = false
    }
}

internal fun validateGlob(glob: String) {
    requireValidUnicode(glob, "glob")
    require('/' !in glob) { "Glob must match a direct child name" }
}

internal fun String.matchesGlob(glob: String): Boolean {
    val textScalars = unicodeScalars()
    val globScalars = glob.unicodeScalars()
    var textIndex = 0
    var globIndex = 0
    var starIndex = -1
    var starTextIndex = -1
    while (textIndex < textScalars.size) {
        when {
            globIndex < globScalars.size && (globScalars[globIndex] == "?" || globScalars[globIndex] == textScalars[textIndex]) -> {
                textIndex++
                globIndex++
            }

            globIndex < globScalars.size && globScalars[globIndex] == "*" -> {
                starIndex = globIndex++
                starTextIndex = textIndex
            }

            starIndex >= 0 -> {
                globIndex = starIndex + 1
                textIndex = ++starTextIndex
            }

            else -> {
                return false
            }
        }
    }
    while (globIndex < globScalars.size && globScalars[globIndex] == "*") globIndex++
    return globIndex == globScalars.size
}

private fun String.unicodeScalars(): List<String> =
    buildList {
        var index = 0
        while (index < length) {
            val end = if (this@unicodeScalars[index].code in 0xd800..0xdbff) index + 2 else index + 1
            add(substring(index, end))
            index = end
        }
    }

internal class FileVisitorActions {
    private var preVisitDirectoryAction: ((Path, FileAttributes) -> FileVisitResult)? = null
    private var visitFileAction: ((Path, FileAttributes) -> FileVisitResult)? = null
    private var visitFileFailedAction: ((Path, IOException) -> FileVisitResult)? = null
    private var postVisitDirectoryAction: ((Path, IOException?) -> FileVisitResult)? = null

    fun setPreVisitDirectory(action: (Path, FileAttributes) -> FileVisitResult) {
        require(preVisitDirectoryAction == null) { "onPreVisitDirectory can be set only once" }
        preVisitDirectoryAction = action
    }

    fun setVisitFile(action: (Path, FileAttributes) -> FileVisitResult) {
        require(visitFileAction == null) { "onVisitFile can be set only once" }
        visitFileAction = action
    }

    fun setVisitFileFailed(action: (Path, IOException) -> FileVisitResult) {
        require(visitFileFailedAction == null) { "onVisitFileFailed can be set only once" }
        visitFileFailedAction = action
    }

    fun setPostVisitDirectory(action: (Path, IOException?) -> FileVisitResult) {
        require(postVisitDirectoryAction == null) { "onPostVisitDirectory can be set only once" }
        postVisitDirectoryAction = action
    }

    fun visitor(): FileVisitor =
        object : FileVisitor {
            override fun preVisitDirectory(
                directory: Path,
                attributes: FileAttributes,
            ): FileVisitResult = preVisitDirectoryAction?.invoke(directory, attributes) ?: FileVisitResult.CONTINUE

            override fun visitFile(
                file: Path,
                attributes: FileAttributes,
            ): FileVisitResult = visitFileAction?.invoke(file, attributes) ?: FileVisitResult.CONTINUE

            override fun visitFileFailed(
                file: Path,
                exception: IOException,
            ): FileVisitResult = visitFileFailedAction?.invoke(file, exception) ?: throw exception

            override fun postVisitDirectory(
                directory: Path,
                exception: IOException?,
            ): FileVisitResult =
                postVisitDirectoryAction?.invoke(directory, exception)
                    ?: exception?.let { throw it }
                    ?: FileVisitResult.CONTINUE
        }
}

internal fun Path.walkEntries(options: Set<PathWalkOption>): Sequence<Path> {
    val followLinks = PathWalkOption.FOLLOW_LINKS in options
    val includeDirectories = PathWalkOption.INCLUDE_DIRECTORIES in options
    val breadthFirst = PathWalkOption.BREADTH_FIRST in options
    return sequence {
        requireDirectoryForTraversal(followLinks)
        if (breadthFirst) {
            val queue = ArrayDeque<Pair<Path, List<Path>>>()
            queue.addLast(this@walkEntries to initialAncestorIdentities(followLinks))
            while (queue.isNotEmpty()) {
                val (directory, ancestors) = queue.removeFirst()
                directory.traversalChildren().forEach { child ->
                    if (child.isTraversalDirectory(followLinks)) {
                        val childAncestors = child.checkedAncestorIdentities(ancestors, followLinks)
                        if (includeDirectories) yield(child)
                        queue.addLast(child to childAncestors)
                    } else {
                        yield(child)
                    }
                }
            }
        } else {
            suspend fun SequenceScope<Path>.visit(
                directory: Path,
                ancestors: List<Path>,
            ) {
                directory.traversalChildren().forEach { child ->
                    if (child.isTraversalDirectory(followLinks)) {
                        val childAncestors = child.checkedAncestorIdentities(ancestors, followLinks)
                        if (includeDirectories) yield(child)
                        visit(child, childAncestors)
                    } else {
                        yield(child)
                    }
                }
            }
            visit(this@walkEntries, initialAncestorIdentities(followLinks))
        }
    }.constrainOnce()
}

internal fun Path.visitEntries(
    visitor: FileVisitor,
    maxDepth: Int,
    followLinks: Boolean,
) {
    require(maxDepth >= 0) { "maxDepth must not be negative" }

    fun visit(
        path: Path,
        depth: Int,
        ancestors: List<Path>,
    ): FileVisitResult {
        val attributes =
            try {
                path.readAttributes(*if (followLinks) emptyArray() else arrayOf(LinkOption.NOFOLLOW_LINKS))
            } catch (exception: IOException) {
                return visitor.visitFileFailed(path, exception)
            }
        val directory = attributes.type == FileType.DIRECTORY
        if (!directory || depth == maxDepth) return visitor.visitFile(path, attributes).withoutSubtreeControl()

        val nextAncestors =
            try {
                path.checkedAncestorIdentities(ancestors, followLinks)
            } catch (exception: IOException) {
                return visitor.visitFileFailed(path, exception)
            }
        val children =
            try {
                path.traversalChildren()
            } catch (exception: IOException) {
                return visitor.visitFileFailed(path, exception)
            }
        when (val result = visitor.preVisitDirectory(path, attributes)) {
            FileVisitResult.SKIP_SUBTREE -> return FileVisitResult.CONTINUE
            FileVisitResult.SKIP_SIBLINGS, FileVisitResult.TERMINATE -> return result
            FileVisitResult.CONTINUE -> Unit
        }

        for (child in children) {
            when (visit(child, depth + 1, nextAncestors)) {
                FileVisitResult.CONTINUE, FileVisitResult.SKIP_SUBTREE -> Unit
                FileVisitResult.SKIP_SIBLINGS -> break
                FileVisitResult.TERMINATE -> return FileVisitResult.TERMINATE
            }
        }
        return visitor.postVisitDirectory(path, null).withoutSubtreeControl()
    }

    visit(this, 0, emptyList())
}

private fun Path.requireDirectoryForTraversal(followLinks: Boolean) {
    val options = if (followLinks) emptyArray() else arrayOf(LinkOption.NOFOLLOW_LINKS)
    val attributes =
        try {
            readAttributes(*options)
        } catch (exception: FileSystemException) {
            throw exception.withOperation(FileSystemOperation.TRAVERSE, this)
        }
    if (attributes.type != FileType.DIRECTORY) {
        throw FileSystemException(
            "Path is not a directory",
            FileSystemOperation.TRAVERSE,
            this,
            reason = FileSystemErrorReason.NOT_A_DIRECTORY,
        )
    }
}

private fun Path.traversalChildren(): List<Path> =
    try {
        listDirectoryEntries()
    } catch (exception: FileSystemException) {
        throw exception.withOperation(FileSystemOperation.TRAVERSE, this)
    }

private fun Path.isTraversalDirectory(followLinks: Boolean): Boolean {
    val options = if (followLinks) emptyArray() else arrayOf(LinkOption.NOFOLLOW_LINKS)
    return try {
        readAttributes(*options).type == FileType.DIRECTORY
    } catch (exception: FileSystemException) {
        throw exception.withOperation(FileSystemOperation.TRAVERSE, this)
    }
}

private fun Path.initialAncestorIdentities(followLinks: Boolean): List<Path> = if (followLinks) listOf(traversalIdentity()) else emptyList()

private fun Path.checkedAncestorIdentities(
    ancestors: List<Path>,
    followLinks: Boolean,
): List<Path> {
    if (!followLinks) return ancestors
    val identity = traversalIdentity()
    if (identity in ancestors) {
        throw FileSystemException(
            "Symbolic link cycle detected",
            FileSystemOperation.TRAVERSE,
            this,
            reason = FileSystemErrorReason.FILE_SYSTEM_LOOP,
        )
    }
    return ancestors.plusElement(identity)
}

private fun Path.traversalIdentity(): Path =
    try {
        toRealPath()
    } catch (exception: FileSystemException) {
        throw exception.withOperation(FileSystemOperation.TRAVERSE, this)
    }

private fun FileVisitResult.withoutSubtreeControl(): FileVisitResult =
    if (this == FileVisitResult.SKIP_SUBTREE) FileVisitResult.CONTINUE else this

internal fun FileSystemException.withOperation(
    operation: FileSystemOperation,
    path: Path = this.path,
): FileSystemException = FileSystemException(message.orEmpty(), operation, path, otherPath, reason, partialResult)
