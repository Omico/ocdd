# `me.omico.ocdd.io.FileReadWrite`：文件内容读写

- 依赖契约：[`me.omico.ocdd.io.Charset`](Charset.md)、[`me.omico.ocdd.io.Charsets`](Charsets.md)、[`me.omico.ocdd.io.Exceptions`](Exceptions.md)、[`me.omico.ocdd.io.FileSystemErrors`](FileSystemErrors.md)、[`me.omico.ocdd.io.Path`](Path.md)
- 非规范性外部参照：[Kotlin `kotlin.io.path`](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.io.path/)

## 规范性定义

### 目标

本契约定义文件的字节、文本、逐行和资源式读写。

### 公共接口

```kotlin
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
```

## 可观察行为

- 文本 API 必须直接使用传入的 `Charset`，默认为 `Charsets.UTF_8`。
- `DEFAULT_BUFFER_SIZE` 必须等于 `8192`；显式缓冲区大小必须大于零。
- 内容 API 必须跟随路径中间和末尾的符号链接，并操作链接目标，链接目录项保持原状。末尾链接悬空时以 `NOT_FOUND` 失败，`CREATE` 也采用该结果。使用 `CREATE_NEW` 时，只要末尾目录项存在（包括悬空链接），就以 `ALREADY_EXISTS` 失败。
- 末尾链接指向目录时以 `IS_A_DIRECTORY` 失败；链接循环以 `FILE_SYSTEM_LOOP` 失败。
- `readBytes()`、`readText()` 和 `readLines()` 必须读取完整文件。逐行 API 识别 `\n`、`\r\n` 和 `\r`，移除行终止符，并按顺序产生每行一次。`useLines()` 返回的单次序列仅在回调期间有效。
- 文本解码遇到无效输入时必须失败。
- 未传选项时，读资源使用 `READ`，写资源和 `write*` 使用 `WRITE + CREATE + TRUNCATE_EXISTING`，追加函数使用 `WRITE + CREATE + APPEND`。传入选项后，读写 API 仍分别隐含 `READ` 和 `WRITE`，但不再隐含创建或截断。
- `writeBytes()` 和 `writeText()` 必须写入全部输入。`writeLines()` 和 `appendLines()` 按迭代顺序写入各项，并在每项后写入一个 `\n`。
- 追加操作必须保留既有内容；目标缺失时必须创建文件。父目录须在调用前存在。
- 写入成功后，文件内容形成独立快照，后续输入变化不影响结果。
- `FileSource.read()` 从当前位置填充缓冲区并推进位置；文件末尾且 `byteCount > 0` 时返回 `-1`，`byteCount == 0` 时返回 `0`。`FileSink.write()` 按调用顺序从当前位置写入并推进位置；零长度写入不改变文件。`offset` 和 `byteCount` 必须落在缓冲区范围内。
- `FileTextReader.readLine()` 返回下一行并推进位置，文件末尾返回 `null`；`readText()` 返回剩余文本并推进到末尾。`FileTextWriter.write()` 按调用顺序编码并写入 `text.toString()`。
- `flush()` 必须把用户空间缓冲内容交给平台文件系统；物理持久化由 `SYNC` 或 `DSYNC` 约束。`newLine()` 必须写入 `\n`。
- 资源打开时 `isClosed` 为 `false`。首次调用 `close()` 后必须为 `true`，即使刷新或关闭失败；首次调用报告该失败，后续调用正常返回。关闭后的其他操作必须抛出 `IllegalStateException`。
- 回调型 `use*` 函数必须在回调正常返回或抛出后关闭其打开的全部资源。回调正常返回而关闭失败时必须抛出关闭异常；回调与关闭都失败时必须重新抛出回调异常，并将关闭异常作为 suppressed exception 附加。

每个 `FileOpenOption` 必须具有以下语义：

| 选项 | 语义 |
| --- | --- |
| `READ` | 允许读取；只适用于读资源 |
| `WRITE` | 允许写入；只适用于写资源和 `write*` |
| `APPEND` | 每次写入从当时的文件末尾开始 |
| `TRUNCATE_EXISTING` | 打开既有普通文件时先将长度截断为零 |
| `CREATE` | 目标缺失时创建普通文件，目标存在时继续打开 |
| `CREATE_NEW` | 原子创建新文件，目标存在时以 `ALREADY_EXISTS` 失败；与 `CREATE` 同时出现时仍采用此语义 |
| `DELETE_ON_CLOSE` | 首次关闭资源时删除接收者指定的目录项；该项是符号链接时删除链接本身而不是已访问的链接目标；用于 `write*` 时必须在函数内部关闭后、返回前删除 |
| `SYNC` | 每次内容或元数据更新返回前完成平台提供的持久化同步 |
| `DSYNC` | 每次内容更新返回前完成平台提供的内容持久化同步 |

`APPEND` 与 `TRUNCATE_EXISTING` 组合时抛出 `IllegalArgumentException`。读 API 接受 `READ` 和 `DELETE_ON_CLOSE`，任何其他选项都以 `IllegalArgumentException` 失败；写 API 接受其余写入选项。重复选项不改变结果；同时使用 `SYNC` 和 `DSYNC` 时采用 `SYNC`。显式写选项不含 `APPEND` 或 `TRUNCATE_EXISTING` 时，从文件开头写入并保留未覆盖的尾部内容。平台缺少 `DELETE_ON_CLOSE`、`SYNC` 或 `DSYNC` 能力时抛出 `UnsupportedOperationException`。

目标或必要父路径不存在、创建新文件时目标已存在、父路径不是目录、目标是目录、访问被拒绝、链接循环、编码无效和其他 I/O 失败，必须抛出 `FileSystemException`，原因分别为 `NOT_FOUND`、`ALREADY_EXISTS`、`NOT_A_DIRECTORY`、`IS_A_DIRECTORY`、`ACCESS_DENIED`、`FILE_SYSTEM_LOOP`、`INVALID_ENCODING` 和 `IO_FAILURE`。打开、读取、写入和首次关闭失败时，`operation` 分别为 `OPEN`、`READ`、`WRITE` 和 `CLOSE`；`path` 为接收者，`otherPath` 为 `null`。写入、同步、关闭或关闭时删除失败后，只有确认未留下持久变化时，`partialResult` 才可以为 `false`。

## 边界与错误

### 不变量与违反条件

完整读写必须保持数据的数量和顺序。文件系统和编码失败必须映射为 `FileSystemException`；无效选项、缓冲区范围或大小必须抛出 `IllegalArgumentException`。资源泄漏、错误分类不符、行分隔行为出现平台差异、静默替换字符或接口不匹配即违反契约。

### 边界

- 本契约范围是声明的顺序读写 API。随机访问、文件锁、异步 I/O、进度、取消和事务由其他能力处理；物理持久化由显式 `SYNC` 或 `DSYNC` 控制。
- 跨平台稳定读写范围是普通文件；设备文件、套接字和其他特殊文件采用平台行为。

## 兼容性

字节、文本和行语义，打开选项，链接处理，资源生命周期，同步保证及错误分类属于兼容性承诺。

## 验证要求

验证必须覆盖目标缺失、普通文件、目录和符号链接，父路径存在与缺失，空与非空字节、文本和行，字符集、完整选项矩阵、显式同步，以及正常完成、操作失败、刷新失败、关闭失败、重复关闭、删除关闭和回调抛出时的资源生命周期与错误分类。

### 规范示例

| 初始条件 | 操作 | 结果 |
| --- | --- | --- |
| 内容为 `old` | `writeText("新")` | 内容为 UTF-8 编码的 `新` |
| 内容为 `A` | `appendText("文")` | 内容为 `A` 后接 UTF-8 编码的 `文` |
| 两行文本 | `readLines()` | 返回两项且不含行终止符 |
| 普通文件 | 使用空内容覆盖 | 文件长度为零 |
| 目标缺失 | 使用空内容追加 | 创建空文件 |
| 资源已关闭 | 再次 `close()` | 正常返回 |
| 内容为 `abc` | `writeBytes(byteArrayOf('X'.code.toByte()), WRITE)` | 内容为 `Xbc` |
| 目标已存在 | `writeBytes(array, CREATE_NEW)` | 抛出原因是 `ALREADY_EXISTS` 的 `FileSystemException` |
| 接收者是指向普通文件的符号链接 | `writeText("新")` | 修改链接目标内容并保留链接 |
| 接收者是悬空符号链接 | `writeText("新", Charsets.UTF_8, CREATE)` | 抛出原因是 `NOT_FOUND` 的 `FileSystemException` 且不创建链接目标 |
| 任意目标 | 同时使用 `APPEND` 与 `TRUNCATE_EXISTING` | 抛出 `IllegalArgumentException` |
| 写资源使用 `DELETE_ON_CLOSE` | 首次关闭 | 删除打开的目录项 |
