@file:Suppress("NewApi") // File tree walking is supplied on API 21-25 by desugar_jdk_libs_nio.

package me.omico.ocdd.io

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

@Throws(IOException::class)
public actual fun Path.deleteExisting() {
    nioOperation(FileSystemOperation.DELETE, this) {
        if (usesAndroidPosixFallback) Os.remove(androidPathString()) else Files.delete(toNioPath())
    }
}

@Throws(IOException::class)
public actual fun Path.deleteIfExists(): Boolean =
    nioOperation(FileSystemOperation.DELETE, this) {
        if (!usesAndroidPosixFallback) return@nioOperation Files.deleteIfExists(toNioPath())
        try {
            Os.remove(androidPathString())
            true
        } catch (exception: ErrnoException) {
            if (exception.errno == OsConstants.ENOENT) false else throw exception
        }
    }

@Throws(IOException::class)
public actual fun Path.deleteRecursively() {
    if (notExists(LinkOption.NOFOLLOW_LINKS)) return
    if (usesAndroidPosixFallback) {
        deleteAndroidRecursively()
        return
    }
    var changed = false
    try {
        Files.walkFileTree(
            toNioPath(),
            object : SimpleFileVisitor<java.nio.file.Path>() {
                override fun visitFile(
                    file: java.nio.file.Path,
                    attrs: BasicFileAttributes,
                ): FileVisitResult {
                    deleteAndroidEntry(file)
                    changed = true
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(
                    directory: java.nio.file.Path,
                    exception: java.io.IOException?,
                ): FileVisitResult {
                    if (exception != null) throw exception
                    deleteAndroidEntry(directory)
                    changed = true
                    return FileVisitResult.CONTINUE
                }
            },
        )
    } catch (exception: Throwable) {
        val failurePath = (exception as? java.nio.file.FileSystemException)?.file?.let(String::toPath) ?: this
        throw exception.toFileSystemException(FileSystemOperation.DELETE, failurePath, partialResult = changed)
    }
}

private fun deleteAndroidEntry(path: java.nio.file.Path) {
    Files.delete(path)
}

private fun Path.deleteAndroidRecursively() {
    var changed = false
    var failurePath = this
    try {
        fun delete(path: Path) {
            failurePath = path
            if (OsConstants.S_ISDIR(path.androidStat(noFollowLinks = true).st_mode)) {
                path.listDirectoryEntries().forEach(::delete)
            }
            failurePath = path
            Os.remove(path.androidPathString())
            changed = true
        }
        delete(this)
    } catch (exception: FileSystemException) {
        throw FileSystemException(
            exception.message.orEmpty(),
            FileSystemOperation.DELETE,
            failurePath,
            reason = exception.reason,
            partialResult = changed || exception.partialResult,
        )
    } catch (exception: Throwable) {
        throw exception.toFileSystemException(FileSystemOperation.DELETE, failurePath, partialResult = changed)
    }
}
