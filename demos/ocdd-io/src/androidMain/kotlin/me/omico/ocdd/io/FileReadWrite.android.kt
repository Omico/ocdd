@file:Suppress("NewApi") // Channels and open options are supplied on API 21-25 by desugar_jdk_libs_nio.

package me.omico.ocdd.io

import okio.BufferedSink
import okio.BufferedSource
import okio.buffer
import okio.sink
import okio.source
import java.nio.channels.Channels
import java.nio.file.Files
import java.nio.file.StandardOpenOption

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
    rejectDirectory(FileSystemOperation.OPEN)
    return nioOperation(FileSystemOperation.OPEN, this) {
        val channel = Files.newByteChannel(toNioPath(), validated.toNioOpenOptions())
        AndroidFileSource(
            path = this,
            source = Channels.newInputStream(channel).source().buffer(),
            deleteOnClose = FileOpenOption.DELETE_ON_CLOSE in validated,
        )
    }
}

@Throws(IOException::class)
public actual fun Path.outputStream(vararg options: FileOpenOption): FileSink {
    val validated = options.validatedWriteOptions()
    preflightWrite(validated)
    return nioOperation(FileSystemOperation.OPEN, this) {
        val channel = Files.newByteChannel(toNioPath(), validated.toNioOpenOptions())
        AndroidFileSink(
            path = this,
            sink = Channels.newOutputStream(channel).sink().buffer(),
            deleteOnClose = FileOpenOption.DELETE_ON_CLOSE in validated,
            synchronizeWrites = FileOpenOption.SYNC in validated || FileOpenOption.DSYNC in validated,
        )
    }
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

private class AndroidFileSource(
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
        return nioOperation(FileSystemOperation.READ, path) { source.read(buffer, offset, byteCount) }
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        closeAndroidResource(path, deleteOnClose, partialResult = deleteOnClose) { source.close() }
    }
}

private class AndroidFileSink(
    private val path: Path,
    private val sink: BufferedSink,
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
        if (byteCount == 0) return
        nioOperation(FileSystemOperation.WRITE, path, partialResult = true) {
            sink.write(buffer, offset, byteCount)
            if (synchronizeWrites) sink.flush()
        }
    }

    override fun flush() {
        check(!isClosed) { "Sink is closed" }
        nioOperation(FileSystemOperation.WRITE, path, partialResult = true) { sink.flush() }
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        closeAndroidResource(path, deleteOnClose, partialResult = true) { sink.close() }
    }
}

private fun Set<FileOpenOption>.toNioOpenOptions(): Set<java.nio.file.OpenOption> =
    mapNotNullTo(linkedSetOf()) { option ->
        when (option) {
            FileOpenOption.READ -> StandardOpenOption.READ
            FileOpenOption.WRITE -> StandardOpenOption.WRITE
            FileOpenOption.APPEND -> StandardOpenOption.APPEND
            FileOpenOption.TRUNCATE_EXISTING -> StandardOpenOption.TRUNCATE_EXISTING
            FileOpenOption.CREATE -> StandardOpenOption.CREATE
            FileOpenOption.CREATE_NEW -> StandardOpenOption.CREATE_NEW
            FileOpenOption.DELETE_ON_CLOSE -> null
            FileOpenOption.SYNC -> StandardOpenOption.SYNC
            FileOpenOption.DSYNC -> StandardOpenOption.DSYNC
        }
    }

private inline fun closeAndroidResource(
    path: Path,
    deleteOnClose: Boolean,
    partialResult: Boolean,
    close: () -> Unit,
) {
    var failure: Throwable? = null
    try {
        nioOperation(FileSystemOperation.CLOSE, path, partialResult = partialResult, block = close)
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

private fun Path.rejectDirectory(operation: FileSystemOperation) {
    if (isDirectory()) {
        throw FileSystemException("Path is a directory", operation, this, reason = FileSystemErrorReason.IS_A_DIRECTORY)
    }
}

private fun Path.preflightWrite(options: Set<FileOpenOption>) {
    rejectDirectory(FileSystemOperation.OPEN)
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
