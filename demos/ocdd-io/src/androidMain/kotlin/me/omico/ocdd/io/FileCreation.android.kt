package me.omico.ocdd.io

import android.system.Os
import android.system.OsConstants
import java.nio.file.Files
import kotlin.random.Random

@Throws(IOException::class)
public actual fun Path.createFile(vararg attributes: FileAttribute): Path {
    validateCreationAttributes(attributes)
    if (usesAndroidPosixFallback) return createAndroidFile(attributes)
    return nioOperation(FileSystemOperation.CREATE, this) {
        Files.createFile(toNioPath(), *attributes.toNioFileAttributes())
        this
    }
}

@Throws(IOException::class)
public actual fun Path.createDirectory(vararg attributes: FileAttribute): Path {
    validateCreationAttributes(attributes)
    return nioOperation(FileSystemOperation.CREATE, this) {
        if (usesAndroidPosixFallback) {
            Os.mkdir(
                androidPathString(),
                attributes.androidCreationMode(defaultPosixDirectoryPermissions.toAndroidMode()),
            )
        } else {
            Files.createDirectory(toNioPath(), *attributes.toNioFileAttributes())
        }
        this
    }
}

@Throws(IOException::class)
public actual fun Path.createDirectories(vararg attributes: FileAttribute): Path {
    validateCreationAttributes(attributes)
    if (usesAndroidPosixFallback) return createAndroidDirectories(attributes)
    val nearestExisting =
        generateSequence(this as Path?) { it.parent }
            .firstOrNull { Files.exists(it.toNioPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS) }
    if (nearestExisting != null && !Files.isDirectory(nearestExisting.toNioPath())) {
        throw FileSystemException(
            "Path is not a directory",
            FileSystemOperation.CREATE,
            nearestExisting,
            reason = FileSystemErrorReason.NOT_A_DIRECTORY,
        )
    }
    if (nearestExisting == this) return this
    val missing =
        generateSequence(this as Path?) { it.parent }
            .takeWhile { Files.notExists(it.toNioPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS) }
            .toList()
    return try {
        nioOperation(FileSystemOperation.CREATE, this) {
            Files.createDirectories(toNioPath(), *attributes.toNioFileAttributes())
            this
        }
    } catch (exception: FileSystemException) {
        val changed = missing.any { Files.exists(it.toNioPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS) }
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
public actual fun Path.createParentDirectories(vararg attributes: FileAttribute): Path {
    validateCreationAttributes(attributes)
    parent?.createDirectories(*attributes)
    return this
}

@Throws(IOException::class)
public actual fun Path.createLinkPointingTo(target: Path): Path =
    nioOperation(FileSystemOperation.CREATE, this, target) {
        if (usesAndroidPosixFallback) {
            Os.link(target.androidPathString(), androidPathString())
        } else {
            Files.createLink(toNioPath(), target.toNioPath())
        }
        this
    }

@Throws(IOException::class)
public actual fun Path.createSymbolicLinkPointingTo(
    target: Path,
    vararg attributes: FileAttribute,
): Path {
    validateCreationAttributes(attributes)
    if (usesAndroidPosixFallback && attributes.isNotEmpty()) {
        throw UnsupportedOperationException("Symbolic-link creation attributes are unavailable on Android API 21-25")
    }
    return nioOperation(FileSystemOperation.CREATE, this, target) {
        if (usesAndroidPosixFallback) {
            Os.symlink(target.toString(), androidPathString())
        } else {
            Files.createSymbolicLink(toNioPath(), target.toNioPath(), *attributes.toNioFileAttributes())
        }
        this
    }
}

@Throws(IOException::class)
public actual fun createTempDirectory(
    prefix: String?,
    vararg attributes: FileAttribute,
): Path = createTempDirectory(directory = null, prefix = prefix, attributes = attributes)

@Throws(IOException::class)
public actual fun createTempDirectory(
    directory: Path?,
    prefix: String?,
    vararg attributes: FileAttribute,
): Path {
    val safePrefix = validateTemporaryPart(prefix, "prefix")
    validateCreationAttributes(attributes)
    val parent = directory ?: defaultTemporaryDirectory()
    if (usesAndroidPosixFallback) {
        return createAndroidTemporary(parent, safePrefix, "", attributes, directory = true)
    }
    return nioOperation(FileSystemOperation.CREATE, parent) {
        val result = Files.createTempDirectory(parent.toNioPath(), safePrefix, *attributes.toNioFileAttributes())
        Path(result)
    }
}

@Throws(IOException::class)
public actual fun createTempFile(
    prefix: String?,
    suffix: String?,
    vararg attributes: FileAttribute,
): Path = createTempFile(directory = null, prefix = prefix, suffix = suffix, attributes = attributes)

@Throws(IOException::class)
public actual fun createTempFile(
    directory: Path?,
    prefix: String?,
    suffix: String?,
    vararg attributes: FileAttribute,
): Path {
    val safePrefix = validateTemporaryPart(prefix, "prefix")
    val safeSuffix = validateTemporaryPart(suffix ?: ".tmp", "suffix")
    validateCreationAttributes(attributes)
    val parent = directory ?: defaultTemporaryDirectory()
    if (usesAndroidPosixFallback) {
        return createAndroidTemporary(parent, safePrefix, safeSuffix, attributes, directory = false)
    }
    return nioOperation(FileSystemOperation.CREATE, parent) {
        val result = Files.createTempFile(parent.toNioPath(), safePrefix, safeSuffix, *attributes.toNioFileAttributes())
        Path(result)
    }
}

private fun defaultTemporaryDirectory(): Path {
    val value =
        System.getProperty("java.io.tmpdir")
            ?: throw FileSystemException(
                "The platform temporary directory is unavailable",
                FileSystemOperation.CREATE,
                ".".toPath(),
                reason = FileSystemErrorReason.IO_FAILURE,
            )
    return value.toPath()
}

private fun Path.createAndroidDirectories(attributes: Array<out FileAttribute>): Path {
    val missing = mutableListOf<Path>()
    var cursor: Path? = this
    while (cursor != null) {
        val current = cursor
        try {
            val status = current.androidStat(noFollowLinks = false)
            if (!OsConstants.S_ISDIR(status.st_mode)) throw notAndroidDirectoryForCreation(current)
            break
        } catch (exception: android.system.ErrnoException) {
            if (exception.errno != OsConstants.ENOENT) {
                throw exception.toFileSystemException(FileSystemOperation.CREATE, current)
            }
            try {
                val linkStatus = current.androidStat(noFollowLinks = true)
                if (!OsConstants.S_ISDIR(linkStatus.st_mode)) throw notAndroidDirectoryForCreation(current)
                break
            } catch (linkException: android.system.ErrnoException) {
                if (linkException.errno != OsConstants.ENOENT) {
                    throw linkException.toFileSystemException(FileSystemOperation.CREATE, current)
                }
            }
            missing.add(current)
            cursor = current.parent
        }
    }
    if (missing.isEmpty()) return this
    var changed = false
    try {
        missing.asReversed().forEach { directory ->
            Os.mkdir(
                directory.androidPathString(),
                attributes.androidCreationMode(defaultPosixDirectoryPermissions.toAndroidMode()),
            )
            changed = true
        }
        return this
    } catch (exception: Throwable) {
        throw exception.toFileSystemException(FileSystemOperation.CREATE, this, partialResult = changed)
    }
}

private fun notAndroidDirectoryForCreation(path: Path): FileSystemException =
    FileSystemException(
        "Path is not a directory",
        FileSystemOperation.CREATE,
        path,
        reason = FileSystemErrorReason.NOT_A_DIRECTORY,
    )

private fun Path.createAndroidFile(attributes: Array<out FileAttribute>): Path {
    var created = false
    try {
        val descriptor =
            Os.open(
                androidPathString(),
                OsConstants.O_WRONLY or OsConstants.O_CREAT or OsConstants.O_EXCL,
                attributes.androidCreationMode(defaultPosixFilePermissions.toAndroidMode()),
            )
        created = true
        Os.close(descriptor)
        return this
    } catch (exception: Throwable) {
        throw exception.toFileSystemException(FileSystemOperation.CREATE, this, partialResult = created)
    }
}

private fun createAndroidTemporary(
    parent: Path,
    prefix: String,
    suffix: String,
    attributes: Array<out FileAttribute>,
    directory: Boolean,
): Path {
    repeat(TEMPORARY_ATTEMPTS) {
        val random = Random.nextLong().toULong().toString(16)
        val candidate = parent.resolve("$prefix$random$suffix")
        try {
            return if (directory) candidate.createDirectory(*attributes) else candidate.createFile(*attributes)
        } catch (exception: FileSystemException) {
            if (exception.reason != FileSystemErrorReason.ALREADY_EXISTS) throw exception
        }
    }
    throw FileSystemException(
        "Unable to allocate a unique temporary name",
        FileSystemOperation.CREATE,
        parent,
        reason = FileSystemErrorReason.ALREADY_EXISTS,
    )
}

private const val TEMPORARY_ATTEMPTS: Int = 128
