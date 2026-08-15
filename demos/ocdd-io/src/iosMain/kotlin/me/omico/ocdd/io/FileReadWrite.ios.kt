@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package me.omico.ocdd.io

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import okio.BufferedSink
import okio.BufferedSource
import okio.FileHandle
import okio.buffer
import platform.posix.O_APPEND
import platform.posix.O_CREAT
import platform.posix.O_EXCL
import platform.posix.O_TRUNC
import platform.posix.O_WRONLY
import platform.posix.close
import platform.posix.errno
import platform.posix.fsync
import platform.posix.open
import platform.posix.write

@Throws(IOException::class)
public actual fun Path.readBytes(): ByteArray = readBytesFrom(inputStream())

@Throws(IOException::class)
public actual fun Path.readText(charset: Charset): String = readTextFrom(reader(charset))

@Throws(IOException::class)
public actual fun Path.readLines(charset: Charset): List<String> = readLinesFrom(reader(charset))

@Throws(IOException::class)
public actual fun Path.forEachLine(
    charset: Charset,
    action: (String) -> Unit,
) {
    forEachLineFrom(reader(charset), action)
}

@Throws(IOException::class)
public actual fun <T> Path.useLines(
    charset: Charset,
    block: (Sequence<String>) -> T,
): T = useLinesFrom(reader(charset), block)

@Throws(IOException::class)
public actual fun Path.writeBytes(
    array: ByteArray,
    vararg options: FileOpenOption,
) {
    writeBytesTo(array, outputStream(*options))
}

@Throws(IOException::class)
public actual fun Path.appendBytes(array: ByteArray) {
    writeBytes(array, FileOpenOption.APPEND, FileOpenOption.CREATE)
}

@Throws(IOException::class)
public actual fun Path.writeText(
    text: CharSequence,
    charset: Charset,
    vararg options: FileOpenOption,
) {
    writeTextTo(text, writer(charset, *options))
}

@Throws(IOException::class)
public actual fun Path.appendText(
    text: CharSequence,
    charset: Charset,
) {
    writeText(text, charset, FileOpenOption.APPEND, FileOpenOption.CREATE)
}

@Throws(IOException::class)
public actual fun Path.writeLines(
    lines: Iterable<CharSequence>,
    charset: Charset,
    vararg options: FileOpenOption,
): Path = writeLines(lines.asSequence(), charset, *options)

@Throws(IOException::class)
public actual fun Path.writeLines(
    lines: Sequence<CharSequence>,
    charset: Charset,
    vararg options: FileOpenOption,
): Path = writeLinesTo(lines, writer(charset, *options))

@Throws(IOException::class)
public actual fun Path.appendLines(
    lines: Iterable<CharSequence>,
    charset: Charset,
): Path = appendLines(lines.asSequence(), charset)

@Throws(IOException::class)
public actual fun Path.appendLines(
    lines: Sequence<CharSequence>,
    charset: Charset,
): Path = writeLines(lines, charset, FileOpenOption.APPEND, FileOpenOption.CREATE)

@Throws(IOException::class)
public actual fun Path.inputStream(vararg options: FileOpenOption): FileSource {
    val validated = options.validatedReadOptions()
    rejectIosDirectory(FileSystemOperation.OPEN)
    return iosOperation(FileSystemOperation.OPEN, this) {
        IosFileSource(
            path = this,
            source = systemFileSystem.source(toOkioPath()).buffer(),
            deleteOnClose = FileOpenOption.DELETE_ON_CLOSE in validated,
        )
    }
}

@Throws(IOException::class)
public actual fun Path.outputStream(vararg options: FileOpenOption): FileSink {
    val validated = options.validatedWriteOptions()
    preflightIosWrite(validated)
    val synchronized = FileOpenOption.SYNC in validated || FileOpenOption.DSYNC in validated
    val exclusiveAppend = FileOpenOption.APPEND in validated && FileOpenOption.CREATE_NEW in validated
    if (synchronized || exclusiveAppend) {
        return openPosixSink(validated, synchronizeWrites = synchronized)
    }
    return iosOperation(FileSystemOperation.OPEN, this) {
        val opened = openOkioSink(validated)
        IosFileSink(
            path = this,
            sink = opened.first,
            handle = opened.second,
            deleteOnClose = FileOpenOption.DELETE_ON_CLOSE in validated,
        )
    }
}

private fun Path.openPosixSink(
    options: Set<FileOpenOption>,
    synchronizeWrites: Boolean,
): FileSink =
    iosOperation(FileSystemOperation.OPEN, this) {
        var flags = O_WRONLY
        if (FileOpenOption.APPEND in options) flags = flags or O_APPEND
        if (FileOpenOption.TRUNCATE_EXISTING in options) flags = flags or O_TRUNC
        if (FileOpenOption.CREATE in options || FileOpenOption.CREATE_NEW in options) flags = flags or O_CREAT
        if (FileOpenOption.CREATE_NEW in options) flags = flags or O_EXCL
        val descriptor = open(toString(), flags, defaultPosixFilePermissions.toMode().toUInt())
        if (descriptor < 0) throw posixFileSystemException(errno, FileSystemOperation.OPEN, this)
        PosixIosFileSink(
            path = this,
            descriptor = descriptor,
            deleteOnClose = FileOpenOption.DELETE_ON_CLOSE in options,
            synchronizeWrites = synchronizeWrites,
        )
    }

@Throws(IOException::class)
public actual fun Path.reader(
    charset: Charset,
    vararg options: FileOpenOption,
): FileTextReader = newTextReader(inputStream(*options), charset)

@Throws(IOException::class)
public actual fun Path.bufferedReader(
    charset: Charset,
    bufferSize: Int,
    vararg options: FileOpenOption,
): FileTextReader {
    require(bufferSize > 0) { "Buffer size must be positive" }
    return reader(charset, *options)
}

@Throws(IOException::class)
public actual fun Path.writer(
    charset: Charset,
    vararg options: FileOpenOption,
): FileTextWriter = newTextWriter(outputStream(*options), charset)

@Throws(IOException::class)
public actual fun Path.bufferedWriter(
    charset: Charset,
    bufferSize: Int,
    vararg options: FileOpenOption,
): FileTextWriter {
    require(bufferSize > 0) { "Buffer size must be positive" }
    return writer(charset, *options)
}

private class IosFileSource(
    private val path: Path,
    private val source: BufferedSource,
    private val deleteOnClose: Boolean,
) : FileSource {
    override var isClosed: Boolean = false
        private set

    override fun read(
        buffer: ByteArray,
        offset: Int,
        byteCount: Int,
    ): Int {
        check(!isClosed) { "Source is closed" }
        validateBufferRange(buffer, offset, byteCount)
        if (byteCount == 0) return 0
        return iosOperation(FileSystemOperation.READ, path) { source.read(buffer, offset, byteCount) }
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        closeIosResource(path, deleteOnClose, partialResult = deleteOnClose) { source.close() }
    }
}

private class IosFileSink(
    private val path: Path,
    private val sink: BufferedSink,
    private val handle: FileHandle?,
    private val deleteOnClose: Boolean,
) : FileSink {
    override var isClosed: Boolean = false
        private set

    override fun write(
        buffer: ByteArray,
        offset: Int,
        byteCount: Int,
    ) {
        check(!isClosed) { "Sink is closed" }
        validateBufferRange(buffer, offset, byteCount)
        if (byteCount == 0) return
        iosOperation(FileSystemOperation.WRITE, path, partialResult = true) { sink.write(buffer, offset, byteCount) }
    }

    override fun flush() {
        check(!isClosed) { "Sink is closed" }
        iosOperation(FileSystemOperation.WRITE, path, partialResult = true) {
            sink.flush()
            handle?.flush()
        }
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        closeIosResource(path, deleteOnClose, partialResult = true) {
            var failure: Throwable? = null
            try {
                sink.close()
            } catch (exception: Throwable) {
                failure = exception
            }
            try {
                handle?.close()
            } catch (exception: Throwable) {
                if (failure == null) failure = exception else failure.addSuppressed(exception)
            }
            failure?.let { throw it }
        }
    }
}

private class PosixIosFileSink(
    private val path: Path,
    private val descriptor: Int,
    private val deleteOnClose: Boolean,
    private val synchronizeWrites: Boolean,
) : FileSink {
    override var isClosed: Boolean = false
        private set

    override fun write(
        buffer: ByteArray,
        offset: Int,
        byteCount: Int,
    ) {
        check(!isClosed) { "Sink is closed" }
        validateBufferRange(buffer, offset, byteCount)
        var written = 0
        while (written < byteCount) {
            val count =
                buffer.usePinned { pinned ->
                    write(descriptor, pinned.addressOf(offset + written), (byteCount - written).convert()).toInt()
                }
            if (count < 0) {
                throw posixFileSystemException(
                    errno,
                    FileSystemOperation.WRITE,
                    path,
                    partialResult = written > 0,
                )
            }
            if (count == 0) {
                throw FileSystemException(
                    "POSIX write made no progress",
                    FileSystemOperation.WRITE,
                    path,
                    reason = FileSystemErrorReason.IO_FAILURE,
                    partialResult = written > 0,
                )
            }
            written += count
        }
        if (synchronizeWrites && byteCount > 0) synchronize()
    }

    override fun flush() {
        check(!isClosed) { "Sink is closed" }
        if (synchronizeWrites) synchronize()
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        closeIosResource(path, deleteOnClose, partialResult = true) {
            if (close(descriptor) != 0) {
                throw posixFileSystemException(errno, FileSystemOperation.CLOSE, path, partialResult = true)
            }
        }
    }

    private fun synchronize() {
        if (fsync(descriptor) != 0) {
            throw posixFileSystemException(errno, FileSystemOperation.WRITE, path, partialResult = true)
        }
    }
}

private fun Path.openOkioSink(options: Set<FileOpenOption>): Pair<BufferedSink, FileHandle?> {
    if (FileOpenOption.APPEND in options) {
        return systemFileSystem
            .appendingSink(
                toOkioPath(),
                mustExist = FileOpenOption.CREATE !in options && FileOpenOption.CREATE_NEW !in options,
            ).buffer() to null
    }
    if (FileOpenOption.CREATE_NEW in options) {
        return systemFileSystem.sink(toOkioPath(), mustCreate = true).buffer() to null
    }
    val handle =
        systemFileSystem.openReadWrite(
            toOkioPath(),
            mustCreate = false,
            mustExist = FileOpenOption.CREATE !in options,
        )
    if (FileOpenOption.TRUNCATE_EXISTING in options) handle.resize(0L)
    return handle.sink().buffer() to handle
}

private fun Path.rejectIosDirectory(operation: FileSystemOperation) {
    if (isDirectory()) {
        throw FileSystemException("Path is a directory", operation, this, reason = FileSystemErrorReason.IS_A_DIRECTORY)
    }
}

private fun Path.preflightIosWrite(options: Set<FileOpenOption>) {
    rejectIosDirectory(FileSystemOperation.OPEN)
    val existsWithoutFollowingLinks = exists(LinkOption.NOFOLLOW_LINKS)
    if (FileOpenOption.CREATE_NEW in options && existsWithoutFollowingLinks) {
        throw FileSystemException(
            "Path already exists",
            FileSystemOperation.OPEN,
            this,
            reason = FileSystemErrorReason.ALREADY_EXISTS,
        )
    }
    if (FileOpenOption.CREATE !in options && FileOpenOption.CREATE_NEW !in options && !existsWithoutFollowingLinks) {
        throw FileSystemException(
            "Path does not exist",
            FileSystemOperation.OPEN,
            this,
            reason = FileSystemErrorReason.NOT_FOUND,
        )
    }
    if (isSymbolicLink()) {
        if (FileOpenOption.CREATE_NEW in options) {
            throw FileSystemException(
                "Path already exists",
                FileSystemOperation.OPEN,
                this,
                reason = FileSystemErrorReason.ALREADY_EXISTS,
            )
        }
        if (!exists()) {
            throw FileSystemException(
                "Symbolic link target does not exist",
                FileSystemOperation.OPEN,
                this,
                reason = FileSystemErrorReason.NOT_FOUND,
            )
        }
    }
}

private inline fun closeIosResource(
    path: Path,
    deleteOnClose: Boolean,
    partialResult: Boolean,
    close: () -> Unit,
) {
    var failure: Throwable? = null
    try {
        iosOperation(FileSystemOperation.CLOSE, path, partialResult = partialResult, block = close)
    } catch (exception: Throwable) {
        failure = exception
    }
    if (deleteOnClose) {
        try {
            path.deleteExisting()
        } catch (exception: FileSystemException) {
            val closeFailure = exception.withOperation(FileSystemOperation.CLOSE, path)
            if (failure == null) failure = closeFailure else failure.addSuppressed(closeFailure)
        }
    }
    failure?.let { throw it }
}
