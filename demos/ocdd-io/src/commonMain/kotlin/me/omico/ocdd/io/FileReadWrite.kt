package me.omico.ocdd.io

public const val DEFAULT_BUFFER_SIZE: Int = 8192

public enum class FileOpenOption {
    READ,
    WRITE,
    APPEND,
    TRUNCATE_EXISTING,
    CREATE,
    CREATE_NEW,
    DELETE_ON_CLOSE,
    SYNC,
    DSYNC,
}

public interface FileSource {
    public val isClosed: Boolean

    @Throws(IOException::class)
    public fun read(
        buffer: ByteArray,
        offset: Int = 0,
        byteCount: Int = buffer.size - offset,
    ): Int

    @Throws(IOException::class)
    public fun close()
}

public interface FileSink {
    public val isClosed: Boolean

    @Throws(IOException::class)
    public fun write(
        buffer: ByteArray,
        offset: Int = 0,
        byteCount: Int = buffer.size - offset,
    )

    @Throws(IOException::class)
    public fun flush()

    @Throws(IOException::class)
    public fun close()
}

public interface FileTextReader {
    public val isClosed: Boolean

    @Throws(IOException::class)
    public fun readLine(): String?

    @Throws(IOException::class)
    public fun readText(): String

    @Throws(IOException::class)
    public fun close()
}

public interface FileTextWriter {
    public val isClosed: Boolean

    @Throws(IOException::class)
    public fun write(text: CharSequence)

    @Throws(IOException::class)
    public fun newLine()

    @Throws(IOException::class)
    public fun flush()

    @Throws(IOException::class)
    public fun close()
}

@Throws(IOException::class)
public expect fun Path.readBytes(): ByteArray

@Throws(IOException::class)
public expect fun Path.readText(charset: Charset = Charsets.UTF_8): String

@Throws(IOException::class)
public expect fun Path.readLines(charset: Charset = Charsets.UTF_8): List<String>

@Throws(IOException::class)
public expect fun Path.forEachLine(
    charset: Charset = Charsets.UTF_8,
    action: (String) -> Unit,
)

@Throws(IOException::class)
public expect fun <T> Path.useLines(
    charset: Charset = Charsets.UTF_8,
    block: (Sequence<String>) -> T,
): T

@Throws(IOException::class)
public expect fun Path.writeBytes(
    array: ByteArray,
    vararg options: FileOpenOption,
)

@Throws(IOException::class)
public expect fun Path.appendBytes(array: ByteArray)

@Throws(IOException::class)
public expect fun Path.writeText(
    text: CharSequence,
    charset: Charset = Charsets.UTF_8,
    vararg options: FileOpenOption,
)

@Throws(IOException::class)
public expect fun Path.appendText(
    text: CharSequence,
    charset: Charset = Charsets.UTF_8,
)

@Throws(IOException::class)
public expect fun Path.writeLines(
    lines: Iterable<CharSequence>,
    charset: Charset = Charsets.UTF_8,
    vararg options: FileOpenOption,
): Path

@Throws(IOException::class)
public expect fun Path.writeLines(
    lines: Sequence<CharSequence>,
    charset: Charset = Charsets.UTF_8,
    vararg options: FileOpenOption,
): Path

@Throws(IOException::class)
public expect fun Path.appendLines(
    lines: Iterable<CharSequence>,
    charset: Charset = Charsets.UTF_8,
): Path

@Throws(IOException::class)
public expect fun Path.appendLines(
    lines: Sequence<CharSequence>,
    charset: Charset = Charsets.UTF_8,
): Path

@Throws(IOException::class)
public expect fun Path.inputStream(vararg options: FileOpenOption): FileSource

@Throws(IOException::class)
public expect fun Path.outputStream(vararg options: FileOpenOption): FileSink

@Throws(IOException::class)
public expect fun Path.reader(
    charset: Charset = Charsets.UTF_8,
    vararg options: FileOpenOption,
): FileTextReader

@Throws(IOException::class)
public expect fun Path.bufferedReader(
    charset: Charset = Charsets.UTF_8,
    bufferSize: Int = DEFAULT_BUFFER_SIZE,
    vararg options: FileOpenOption,
): FileTextReader

@Throws(IOException::class)
public expect fun Path.writer(
    charset: Charset = Charsets.UTF_8,
    vararg options: FileOpenOption,
): FileTextWriter

@Throws(IOException::class)
public expect fun Path.bufferedWriter(
    charset: Charset = Charsets.UTF_8,
    bufferSize: Int = DEFAULT_BUFFER_SIZE,
    vararg options: FileOpenOption,
): FileTextWriter

internal fun Array<out FileOpenOption>.validatedReadOptions(): Set<FileOpenOption> {
    val result = toSet() + FileOpenOption.READ
    val invalid = result - setOf(FileOpenOption.READ, FileOpenOption.DELETE_ON_CLOSE)
    require(invalid.isEmpty()) { "Options are not valid for reading: ${invalid.joinToString()}" }
    return result
}

internal fun Array<out FileOpenOption>.validatedWriteOptions(): Set<FileOpenOption> {
    val result = (
        if (isEmpty()) {
            setOf(FileOpenOption.WRITE, FileOpenOption.CREATE, FileOpenOption.TRUNCATE_EXISTING)
        } else {
            toSet() + FileOpenOption.WRITE
        }
    )
    require(FileOpenOption.READ !in result) { "READ is not valid for writing" }
    require(FileOpenOption.APPEND !in result || FileOpenOption.TRUNCATE_EXISTING !in result) {
        "APPEND and TRUNCATE_EXISTING cannot be combined"
    }
    return result
}

internal fun validateBufferRange(
    buffer: ByteArray,
    offset: Int,
    byteCount: Int,
) {
    require(offset >= 0 && byteCount >= 0 && offset <= buffer.size - byteCount) {
        "Buffer range [$offset, ${offset + byteCount}) is outside 0..${buffer.size}"
    }
}

internal fun Charset.encode(text: CharSequence): ByteArray {
    require(this == Charsets.UTF_8) { "Unsupported charset: $this" }
    return text.toString().encodeToByteArray()
}

internal fun Charset.decode(
    path: Path,
    bytes: ByteArray,
): String {
    require(this == Charsets.UTF_8) { "Unsupported charset: $this" }
    return try {
        bytes.decodeToString(throwOnInvalidSequence = true)
    } catch (exception: CharacterCodingException) {
        throw FileSystemException(
            exception.message ?: "Input is not valid UTF-8",
            FileSystemOperation.READ,
            path,
            reason = FileSystemErrorReason.INVALID_ENCODING,
        )
    }
}

internal fun Path.readBytesFrom(source: FileSource): ByteArray =
    source.useFileResource(FileSource::close) {
        readAllRemainingBytes()
    }

private fun FileSource.readAllRemainingBytes(): ByteArray {
    val chunks = mutableListOf<ByteArray>()
    var size = 0
    while (true) {
        val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
        val count = read(chunk)
        if (count == -1) break
        if (count > 0) {
            chunks += if (count == chunk.size) chunk else chunk.copyOf(count)
            size += count
        }
    }
    return ByteArray(size).also { result ->
        var offset = 0
        chunks.forEach { chunk ->
            chunk.copyInto(result, destinationOffset = offset)
            offset += chunk.size
        }
    }
}

internal fun Path.newTextReader(
    source: FileSource,
    charset: Charset,
): FileTextReader = DefaultFileTextReader(this, source, charset)

internal fun Path.newTextWriter(
    sink: FileSink,
    charset: Charset,
): FileTextWriter = DefaultFileTextWriter(sink, charset)

internal fun Path.readTextFrom(reader: FileTextReader): String = reader.useFileResource(FileTextReader::close) { readText() }

internal fun Path.readLinesFrom(reader: FileTextReader): List<String> = useLinesFrom(reader) { lines -> lines.toList() }

internal fun Path.forEachLineFrom(
    reader: FileTextReader,
    action: (String) -> Unit,
) {
    useLinesFrom(reader) { lines -> lines.forEach(action) }
}

internal fun <T> Path.useLinesFrom(
    reader: FileTextReader,
    block: (Sequence<String>) -> T,
): T =
    reader.useFileResource(FileTextReader::close) {
        block(generateSequence(::readLine).constrainOnce())
    }

internal fun Path.writeBytesTo(
    array: ByteArray,
    sink: FileSink,
) {
    sink.useFileResource(FileSink::close) { write(array) }
}

internal fun Path.writeTextTo(
    text: CharSequence,
    writer: FileTextWriter,
) {
    writer.useFileResource(FileTextWriter::close) { write(text) }
}

internal fun Path.writeLinesTo(
    lines: Sequence<CharSequence>,
    writer: FileTextWriter,
): Path {
    writer.useFileResource(FileTextWriter::close) {
        lines.forEach { line ->
            write(line)
            newLine()
        }
    }
    return this
}

private class DefaultFileTextReader(
    private val path: Path,
    private val source: FileSource,
    private val charset: Charset,
) : FileTextReader {
    override var isClosed: Boolean = false
        private set
    private var content: String? = null
    private var offset: Int = 0

    override fun readLine(): String? {
        checkOpen()
        val text = content()
        if (offset >= text.length) return null
        var end = offset
        while (end < text.length && text[end] != '\n' && text[end] != '\r') end++
        val line = text.substring(offset, end)
        offset =
            when {
                end == text.length -> end
                text[end] == '\r' && end + 1 < text.length && text[end + 1] == '\n' -> end + 2
                else -> end + 1
            }
        return line
    }

    override fun readText(): String {
        checkOpen()
        val text = content()
        return text.substring(offset).also { offset = text.length }
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        source.close()
    }

    private fun content(): String =
        content ?: source.readAllRemainingBytes().let { charset.decode(path, it) }.also {
            content = it
        }

    private fun checkOpen() {
        check(!isClosed) { "Reader is closed" }
    }
}

private class DefaultFileTextWriter(
    private val sink: FileSink,
    private val charset: Charset,
) : FileTextWriter {
    override var isClosed: Boolean = false
        private set

    override fun write(text: CharSequence) {
        checkOpen()
        sink.write(charset.encode(text))
    }

    override fun newLine(): Unit = write("\n")

    override fun flush() {
        checkOpen()
        sink.flush()
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        sink.close()
    }

    private fun checkOpen() {
        check(!isClosed) { "Writer is closed" }
    }
}

private inline fun <R, T> R.useFileResource(
    close: R.() -> Unit,
    block: R.() -> T,
): T = useResource(close = { close() }, block = { block() })
