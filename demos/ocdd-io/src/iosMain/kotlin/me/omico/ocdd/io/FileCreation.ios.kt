package me.omico.ocdd.io

import platform.posix.F_OK
import platform.posix.O_CREAT
import platform.posix.O_EXCL
import platform.posix.O_WRONLY
import platform.posix.access
import platform.posix.close
import platform.posix.errno
import platform.posix.link
import platform.posix.mkdir
import platform.posix.open
import platform.posix.symlink
import kotlin.random.Random

@Throws(IOException::class)
public actual fun Path.createFile(vararg attributes: FileAttribute): Path {
    validateCreationAttributes(attributes)
    val mode = attributes.creationMode(defaultMode = defaultPosixFilePermissions.toMode())
    val descriptor = open(toString(), O_WRONLY or O_CREAT or O_EXCL, mode.toUInt())
    if (descriptor < 0) throw posixFileSystemException(errno, FileSystemOperation.CREATE, this)
    if (close(descriptor) != 0) {
        throw posixFileSystemException(errno, FileSystemOperation.CREATE, this, partialResult = true)
    }
    return this
}

@Throws(IOException::class)
public actual fun Path.createDirectory(vararg attributes: FileAttribute): Path {
    validateCreationAttributes(attributes)
    val mode = attributes.creationMode(defaultMode = defaultPosixDirectoryPermissions.toMode())
    if (mkdir(toString(), mode.toUShort()) != 0) throw posixFileSystemException(errno, FileSystemOperation.CREATE, this)
    return this
}

@Throws(IOException::class)
public actual fun Path.createDirectories(vararg attributes: FileAttribute): Path {
    validateCreationAttributes(attributes)
    val missing = mutableListOf<Path>()
    var cursor: Path? = this
    while (cursor != null) {
        val current = cursor
        if (access(current.toString(), F_OK) == 0) {
            val metadata = current.readIosFileMetadata(followLinks = true, operation = FileSystemOperation.CREATE)
            if (metadata.type != FileType.DIRECTORY) throw notDirectoryForCreation(current)
            break
        }
        val accessError = errno
        if (posixReason(accessError) != FileSystemErrorReason.NOT_FOUND) {
            throw posixFileSystemException(accessError, FileSystemOperation.CREATE, current)
        }
        try {
            val linkMetadata = current.readIosFileMetadata(followLinks = false, operation = FileSystemOperation.CREATE)
            if (linkMetadata.type != FileType.DIRECTORY) throw notDirectoryForCreation(current)
            break
        } catch (linkException: FileSystemException) {
            if (linkException.reason != FileSystemErrorReason.NOT_FOUND) throw linkException
        }
        missing.add(current)
        cursor = current.parent
    }
    var changed = false
    for (directory in missing.asReversed()) {
        try {
            directory.createDirectory(*attributes)
            changed = true
        } catch (exception: FileSystemException) {
            throw FileSystemException(
                exception.message.orEmpty(),
                exception.operation,
                exception.path,
                exception.otherPath,
                exception.reason,
                partialResult = changed || exception.partialResult,
            )
        }
    }
    return this
}

private fun notDirectoryForCreation(path: Path): FileSystemException =
    FileSystemException(
        "Path is not a directory",
        FileSystemOperation.CREATE,
        path,
        reason = FileSystemErrorReason.NOT_A_DIRECTORY,
    )

@Throws(IOException::class)
public actual fun Path.createParentDirectories(vararg attributes: FileAttribute): Path {
    validateCreationAttributes(attributes)
    parent?.createDirectories(*attributes)
    return this
}

@Throws(IOException::class)
public actual fun Path.createLinkPointingTo(target: Path): Path {
    if (link(target.toString(), toString()) != 0) {
        throw posixFileSystemException(errno, FileSystemOperation.CREATE, this, target)
    }
    return this
}

@Throws(IOException::class)
public actual fun Path.createSymbolicLinkPointingTo(
    target: Path,
    vararg attributes: FileAttribute,
): Path {
    validateCreationAttributes(attributes)
    if (attributes.isNotEmpty()) throw UnsupportedOperationException("iOS cannot atomically apply attributes to a symbolic link")
    if (symlink(target.toString(), toString()) != 0) {
        throw posixFileSystemException(errno, FileSystemOperation.CREATE, this, target)
    }
    return this
}

@Throws(IOException::class)
public actual fun createTempDirectory(
    prefix: String?,
    vararg attributes: FileAttribute,
): Path = createTempDirectory(null, prefix, attributes = attributes)

@Throws(IOException::class)
public actual fun createTempDirectory(
    directory: Path?,
    prefix: String?,
    vararg attributes: FileAttribute,
): Path {
    val safePrefix = validateTemporaryPart(prefix, "prefix")
    validateCreationAttributes(attributes)
    val parent =
        directory ?: systemFileSystem.let {
            okio.FileSystem.SYSTEM_TEMPORARY_DIRECTORY
                .toString()
                .toPath()
        }
    return createTemporary(parent, safePrefix, "", attributes, isDirectory = true)
}

@Throws(IOException::class)
public actual fun createTempFile(
    prefix: String?,
    suffix: String?,
    vararg attributes: FileAttribute,
): Path = createTempFile(null, prefix, suffix, attributes = attributes)

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
    val parent =
        directory ?: okio.FileSystem.SYSTEM_TEMPORARY_DIRECTORY
            .toString()
            .toPath()
    return createTemporary(parent, safePrefix, safeSuffix, attributes, isDirectory = false)
}

private fun Array<out FileAttribute>.creationMode(defaultMode: Int): Int =
    firstOrNull { it.name == "posix:permissions" }?.let { immutablePermissions(it.value).toMode() } ?: defaultMode

private fun createTemporary(
    directory: Path,
    prefix: String,
    suffix: String,
    attributes: Array<out FileAttribute>,
    isDirectory: Boolean,
): Path {
    repeat(10_000) {
        val generated = Random.nextLong().toULong().toString(16)
        val candidate = directory.resolve("$prefix$generated$suffix")
        try {
            return if (isDirectory) candidate.createDirectory(*attributes) else candidate.createFile(*attributes)
        } catch (exception: FileSystemException) {
            if (exception.reason != FileSystemErrorReason.ALREADY_EXISTS) throw exception
        }
    }
    throw FileSystemException(
        "Unable to create a unique temporary entry",
        FileSystemOperation.CREATE,
        directory,
        reason = FileSystemErrorReason.IO_FAILURE,
    )
}
