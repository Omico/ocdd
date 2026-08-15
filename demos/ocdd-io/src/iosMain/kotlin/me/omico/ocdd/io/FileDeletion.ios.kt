package me.omico.ocdd.io

import platform.posix.ENOENT
import platform.posix.errno
import platform.posix.remove

@Throws(IOException::class)
public actual fun Path.deleteExisting() {
    if (remove(toString()) != 0) {
        throw posixFileSystemException(errno, FileSystemOperation.DELETE, this)
    }
}

@Throws(IOException::class)
public actual fun Path.deleteIfExists(): Boolean {
    if (remove(toString()) == 0) return true
    val errorCode = errno
    if (errorCode == ENOENT) return false
    throw posixFileSystemException(errorCode, FileSystemOperation.DELETE, this)
}

@Throws(IOException::class)
public actual fun Path.deleteRecursively() {
    if (notExists(LinkOption.NOFOLLOW_LINKS)) return
    var changed = false

    fun delete(path: Path) {
        val metadata =
            iosOperation(FileSystemOperation.DELETE, path, partialResult = changed) {
                systemFileSystem.metadata(path.toOkioPath())
            }
        if (metadata.isDirectory && metadata.symlinkTarget == null) {
            val children =
                iosOperation(FileSystemOperation.DELETE, path, partialResult = changed) {
                    systemFileSystem.list(path.toOkioPath()).map { it.toString().toPath() }
                }
            children.forEach(::delete)
        }
        if (remove(path.toString()) != 0) {
            throw posixFileSystemException(errno, FileSystemOperation.DELETE, path, partialResult = changed)
        }
        changed = true
    }

    delete(this)
}
