package me.omico.ocdd.io

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FileReadWriteTest {
    @Test
    fun completeByteAndTextOperationsPreserveContent(): Unit =
        withTemporaryDirectory { directory ->
            val file = directory.resolve("content")
            val original = byteArrayOf(0, 1, 2, 127, -1)

            file.writeBytes(original)
            original.fill(9)
            assertContentEquals(byteArrayOf(0, 1, 2, 127, -1), file.readBytes())

            file.writeText("A文")
            file.appendText("B")
            assertEquals("A文B", file.readText())

            file.writeText("one\r\ntwo\rthree\nfour")
            assertEquals(listOf("one", "two", "three", "four"), file.readLines())
            val visited = mutableListOf<String>()
            file.forEachLine(action = visited::add)
            assertEquals(listOf("one", "two", "three", "four"), visited)
        }

    @Test
    fun writeOptionsControlCreationTruncationAppendAndDeletion(): Unit =
        withTemporaryDirectory { directory ->
            val file = directory.resolve("options")
            file.writeText("abc")

            file.writeBytes(byteArrayOf('X'.code.toByte()), FileOpenOption.WRITE)
            assertEquals("Xbc", file.readText())
            file.appendBytes("!".encodeToByteArray())
            assertEquals("Xbc!", file.readText())

            val existing =
                assertFailsWith<FileSystemException> {
                    file.writeBytes(byteArrayOf(), FileOpenOption.CREATE_NEW)
                }
            assertEquals(FileSystemErrorReason.ALREADY_EXISTS, existing.reason)
            val appendExisting =
                assertFailsWith<FileSystemException> {
                    file.writeBytes(byteArrayOf(), FileOpenOption.APPEND, FileOpenOption.CREATE_NEW)
                }
            assertEquals(FileSystemErrorReason.ALREADY_EXISTS, appendExisting.reason)
            val exclusiveAppend = directory.resolve("exclusive-append")
            exclusiveAppend.writeText(
                "created",
                options = arrayOf(FileOpenOption.APPEND, FileOpenOption.CREATE_NEW),
            )
            assertEquals("created", exclusiveAppend.readText())
            assertFailsWith<IllegalArgumentException> {
                file.outputStream(FileOpenOption.APPEND, FileOpenOption.TRUNCATE_EXISTING)
            }

            val deleteOnClose = directory.resolve("ephemeral")
            val sink = deleteOnClose.outputStream(FileOpenOption.CREATE, FileOpenOption.DELETE_ON_CLOSE)
            sink.write("data".encodeToByteArray())
            assertTrue(deleteOnClose.exists())
            sink.close()
            assertTrue(deleteOnClose.notExists(LinkOption.NOFOLLOW_LINKS))
        }

    @Test
    fun resourcesValidateRangesAndCloseIdempotently(): Unit =
        withTemporaryDirectory { directory ->
            val file = directory.resolve("resource")
            file.writeText("abc")
            val source = file.inputStream()
            val buffer = ByteArray(4)

            assertEquals(0, source.read(buffer, offset = 4, byteCount = 0))
            assertEquals(3, source.read(buffer))
            assertEquals(-1, source.read(buffer))
            assertFailsWith<IllegalArgumentException> { source.read(buffer, offset = 3, byteCount = 2) }
            source.close()
            source.close()
            assertTrue(source.isClosed)
            assertFailsWith<IllegalStateException> { source.read(buffer) }

            val sink = file.outputStream(FileOpenOption.WRITE)
            sink.write(byteArrayOf('Z'.code.toByte()))
            sink.flush()
            sink.close()
            sink.close()
            assertTrue(sink.isClosed)
            assertFailsWith<IllegalStateException> { sink.write(byteArrayOf()) }
            assertEquals("Zbc", file.readText())
        }

    @Test
    fun writerFailureInjectionPreservesFlushAndCloseLifecycle() {
        // Injected failures make the first-close state transition and flush propagation independently observable.
        val path = "faulting-writer".toPath()
        val flushFailure = fileSystemFailure("flush", FileSystemOperation.WRITE, path)
        val flushWriter = path.newTextWriter(FaultingFileSink(flushFailure = flushFailure), Charsets.UTF_8)

        assertSame(flushFailure, assertFailsWith<FileSystemException> { flushWriter.flush() })
        assertFalse(flushWriter.isClosed)
        flushWriter.close()
        assertTrue(flushWriter.isClosed)

        val closeFailure = fileSystemFailure("close", FileSystemOperation.CLOSE, path)
        val closeSink = FaultingFileSink(closeFailure = closeFailure)
        val closeWriter = path.newTextWriter(closeSink, Charsets.UTF_8)

        assertSame(closeFailure, assertFailsWith<FileSystemException> { closeWriter.close() })
        assertTrue(closeWriter.isClosed)
        closeWriter.close()
        assertEquals(1, closeSink.closeCalls)
    }

    @Test
    fun fileResourceFailureInjectionSuppressesCloseAfterPrimaryFailure() {
        // Resource helpers retain a write or callback failure while attaching the independently failing close.
        val path = "faulting-resource".toPath()
        val writeFailure = fileSystemFailure("write", FileSystemOperation.WRITE, path)
        val writeCloseFailure = fileSystemFailure("write close", FileSystemOperation.CLOSE, path)
        val writer =
            path.newTextWriter(
                FaultingFileSink(writeFailure = writeFailure, closeFailure = writeCloseFailure),
                Charsets.UTF_8,
            )

        val reportedWrite = assertFailsWith<FileSystemException> { path.writeTextTo("value", writer) }
        assertSame(writeFailure, reportedWrite)
        assertEquals(listOf(writeCloseFailure), reportedWrite.suppressedExceptions)
        assertTrue(writer.isClosed)

        val callbackFailure = IllegalStateException("callback")
        val readerCloseFailure = fileSystemFailure("reader close", FileSystemOperation.CLOSE, path)
        val reader = CloseFailingTextReader(readerCloseFailure)
        val reportedCallback =
            assertFailsWith<IllegalStateException> {
                path.useLinesFrom(reader) { throw callbackFailure }
            }
        assertSame(callbackFailure, reportedCallback)
        assertEquals(listOf(readerCloseFailure), reportedCallback.suppressedExceptions)
        assertTrue(reader.isClosed)
    }

    @Test
    fun encodingAndLinkFailuresAreStable(): Unit =
        withTemporaryDirectory { directory ->
            val invalid = directory.resolve("invalid")
            invalid.writeBytes(byteArrayOf(0xc3.toByte(), 0x28))
            val encoding = assertFailsWith<FileSystemException> { invalid.readText() }
            assertEquals(FileSystemErrorReason.INVALID_ENCODING, encoding.reason)

            val dangling = directory.resolve("dangling").createSymbolicLinkPointingTo("missing".toPath())
            val missing =
                assertFailsWith<FileSystemException> {
                    dangling.writeText("value", options = arrayOf(FileOpenOption.CREATE))
                }
            assertEquals(FileSystemErrorReason.NOT_FOUND, missing.reason)
            assertTrue(dangling.isSymbolicLink())
            assertFalse(directory.resolve("missing").exists())

            assertFailsWith<IllegalArgumentException> { invalid.bufferedReader(bufferSize = 0) }
            assertFailsWith<IllegalArgumentException> { invalid.bufferedWriter(bufferSize = 0) }
        }

    @Test
    fun lineWritersAddOneLineFeedPerElement(): Unit =
        withTemporaryDirectory { directory ->
            val file = directory.resolve("lines")

            assertEquals(file, file.writeLines(listOf("a", "b")))
            assertEquals("a\nb\n", file.readText())
            assertEquals(file, file.appendLines(sequenceOf("c")))
            assertEquals("a\nb\nc\n", file.readText())
            assertEquals(3, file.useLines { it.count() })
        }

    @Test
    fun synchronizedWritesAndExplicitOptionValidationArePortable(): Unit =
        withTemporaryDirectory { directory ->
            val file = directory.resolve("synchronized")
            val sink =
                file.outputStream(
                    FileOpenOption.CREATE,
                    FileOpenOption.TRUNCATE_EXISTING,
                    FileOpenOption.SYNC,
                )
            sink.write("visible".encodeToByteArray())
            assertEquals("visible", file.readText())
            sink.close()

            file.writeText(
                "sync",
                options = arrayOf(FileOpenOption.CREATE, FileOpenOption.TRUNCATE_EXISTING, FileOpenOption.SYNC),
            )
            file.writeText("+data", options = arrayOf(FileOpenOption.APPEND, FileOpenOption.DSYNC))
            assertEquals("sync+data", file.readText())

            val missing = directory.resolve("missing")
            val openFailure =
                assertFailsWith<FileSystemException> {
                    missing.outputStream(FileOpenOption.WRITE)
                }
            assertEquals(FileSystemOperation.OPEN, openFailure.operation)
            assertEquals(FileSystemErrorReason.NOT_FOUND, openFailure.reason)
            val readFailure = assertFailsWith<FileSystemException> { missing.readBytes() }
            assertEquals(FileSystemOperation.OPEN, readFailure.operation)
            assertEquals(FileSystemErrorReason.NOT_FOUND, readFailure.reason)
            assertFailsWith<IllegalArgumentException> { file.inputStream(FileOpenOption.WRITE) }
            assertFailsWith<IllegalArgumentException> { file.outputStream(FileOpenOption.READ) }
            val directoryFailure = assertFailsWith<FileSystemException> { directory.readBytes() }
            assertEquals(FileSystemErrorReason.IS_A_DIRECTORY, directoryFailure.reason)
        }

    @Test
    fun textReaderKeepsDeleteOnCloseEntryUntilExplicitClose(): Unit =
        withTemporaryDirectory { directory ->
            val file = directory.resolve("reader-delete").also { it.writeText("line\n") }
            val reader = file.reader(options = arrayOf(FileOpenOption.DELETE_ON_CLOSE))

            assertEquals("line", reader.readLine())
            assertTrue(file.exists(LinkOption.NOFOLLOW_LINKS))
            assertFalse(reader.isClosed)
            reader.close()
            assertTrue(reader.isClosed)
            assertTrue(file.notExists(LinkOption.NOFOLLOW_LINKS))
        }

    @Test
    fun unbufferedTextWriterExposesLifecycleAndLineSeparator(): Unit =
        withTemporaryDirectory { directory ->
            val file = directory.resolve("writer")
            val writer = file.writer()

            writer.write("first")
            writer.newLine()
            writer.write("second")
            writer.flush()
            assertFalse(writer.isClosed)
            writer.close()
            writer.close()

            assertTrue(writer.isClosed)
            assertEquals("first\nsecond", file.readText())
            assertFailsWith<IllegalStateException> { writer.write("closed") }
        }

    @Test
    fun writesFollowExistingLinksButNeverCreateDanglingTargets(): Unit =
        withTemporaryDirectory { directory ->
            val target = directory.resolve("target").also { it.writeText("old") }
            val link = directory.resolve("link").createSymbolicLinkPointingTo(target.fileName!!)

            link.writeText("new")
            assertTrue(link.isSymbolicLink())
            assertEquals("new", target.readText())
            val existing =
                assertFailsWith<FileSystemException> {
                    link.writeText("x", options = arrayOf(FileOpenOption.CREATE_NEW))
                }
            assertEquals(FileSystemErrorReason.ALREADY_EXISTS, existing.reason)
        }
}

private class FaultingFileSink(
    private val writeFailure: IOException? = null,
    private val flushFailure: IOException? = null,
    private val closeFailure: IOException? = null,
) : FileSink {
    override var isClosed: Boolean = false
        private set
    var closeCalls: Int = 0
        private set

    override fun write(
        buffer: ByteArray,
        offset: Int,
        byteCount: Int,
    ) {
        check(!isClosed) { "Sink is closed" }
        writeFailure?.let { throw it }
    }

    override fun flush() {
        check(!isClosed) { "Sink is closed" }
        flushFailure?.let { throw it }
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        closeCalls++
        closeFailure?.let { throw it }
    }
}

private class CloseFailingTextReader(
    private val closeFailure: IOException,
) : FileTextReader {
    override var isClosed: Boolean = false
        private set

    override fun readLine(): String? {
        check(!isClosed) { "Reader is closed" }
        return null
    }

    override fun readText(): String {
        check(!isClosed) { "Reader is closed" }
        return ""
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        throw closeFailure
    }
}

private fun fileSystemFailure(
    message: String,
    operation: FileSystemOperation,
    path: Path,
): FileSystemException =
    FileSystemException(
        message,
        operation,
        path,
        reason = FileSystemErrorReason.IO_FAILURE,
    )
