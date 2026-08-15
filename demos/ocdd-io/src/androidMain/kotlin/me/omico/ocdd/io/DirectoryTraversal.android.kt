@file:Suppress("NewApi") // DirectoryStream is supplied on API 21-25 by desugar_jdk_libs_nio.

package me.omico.ocdd.io

import java.nio.file.Files

public actual class FileVisitorBuilder internal actual constructor() {
    internal val actions: FileVisitorActions = FileVisitorActions()

    public actual fun onPreVisitDirectory(action: (Path, FileAttributes) -> FileVisitResult) {
        actions.setPreVisitDirectory(action)
    }

    public actual fun onVisitFile(action: (Path, FileAttributes) -> FileVisitResult) {
        actions.setVisitFile(action)
    }

    public actual fun onVisitFileFailed(action: (Path, IOException) -> FileVisitResult) {
        actions.setVisitFileFailed(action)
    }

    public actual fun onPostVisitDirectory(action: (Path, IOException?) -> FileVisitResult) {
        actions.setPostVisitDirectory(action)
    }
}

@Throws(IOException::class)
public actual fun Path.listDirectoryEntries(glob: String): List<Path> = useDirectoryEntries(glob) { it.toList() }

@Throws(IOException::class)
public actual fun Path.forEachDirectoryEntry(
    glob: String,
    action: (Path) -> Unit,
) {
    listDirectoryEntries(glob).forEach(action)
}

@Throws(IOException::class)
public actual fun <T> Path.useDirectoryEntries(
    glob: String,
    block: (Sequence<Path>) -> T,
): T {
    validateGlob(glob)
    val stream =
        nioOperation(FileSystemOperation.LIST, this) {
            if (usesAndroidPosixFallback) {
                val status = androidStat(noFollowLinks = false)
                if (!android.system.OsConstants.S_ISDIR(status.st_mode)) {
                    throw FileSystemException(
                        "Path is not a directory",
                        FileSystemOperation.LIST,
                        this,
                        reason = FileSystemErrorReason.NOT_A_DIRECTORY,
                    )
                }
            }
            Files.newDirectoryStream(toNioPath())
        }
    return useResource(
        close = {
            nioOperation(FileSystemOperation.CLOSE, this) {
                stream.close()
            }
        },
    ) {
        val entries =
            nioOperation(FileSystemOperation.LIST, this) {
                stream
                    .asSequence()
                    .map(::Path)
                    .filter { it.name.matchesGlob(glob) }
                    .sorted()
                    .toList()
            }
        entries.useAsDirectoryEntries(block)
    }
}

public actual fun fileVisitor(builderAction: FileVisitorBuilder.() -> Unit): FileVisitor =
    FileVisitorBuilder().apply(builderAction).actions.visitor()

@Throws(IOException::class)
public actual fun Path.visitFileTree(
    visitor: FileVisitor,
    maxDepth: Int,
    followLinks: Boolean,
) {
    visitEntries(visitor, maxDepth, followLinks)
}

@Throws(IOException::class)
public actual fun Path.visitFileTree(
    maxDepth: Int,
    followLinks: Boolean,
    builderAction: FileVisitorBuilder.() -> Unit,
) {
    visitFileTree(fileVisitor(builderAction), maxDepth, followLinks)
}

@Throws(IOException::class)
public actual fun Path.walk(vararg options: PathWalkOption): Sequence<Path> = walkEntries(options.toSet())
