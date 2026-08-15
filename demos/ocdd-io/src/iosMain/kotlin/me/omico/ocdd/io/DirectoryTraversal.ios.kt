@file:OptIn(kotlinx.cinterop.BetaInteropApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package me.omico.ocdd.io

import kotlinx.cinterop.pointed
import kotlinx.cinterop.toKString
import platform.posix.closedir
import platform.posix.errno
import platform.posix.opendir
import platform.posix.readdir
import platform.posix.set_posix_errno

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
    val stream = opendir(toString()) ?: throw posixFileSystemException(errno, FileSystemOperation.LIST, this)
    return useResource(
        close = {
            if (closedir(stream) != 0) {
                throw posixFileSystemException(errno, FileSystemOperation.CLOSE, this)
            }
        },
    ) {
        val entries = mutableListOf<Path>()
        while (true) {
            set_posix_errno(0)
            val entry = readdir(stream)
            if (entry == null) {
                val readError = errno
                if (readError != 0) throw posixFileSystemException(readError, FileSystemOperation.LIST, this)
                break
            }
            val name = entry.pointed.d_name.toKString()
            if (name != "." && name != ".." && name.matchesGlob(glob)) {
                entries.add(resolve(name))
            }
        }
        entries.sorted().useAsDirectoryEntries(block)
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
