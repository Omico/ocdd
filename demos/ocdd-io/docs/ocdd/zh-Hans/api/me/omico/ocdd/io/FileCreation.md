# `me.omico.ocdd.io.FileCreation`：文件系统对象创建

- 依赖契约：[`me.omico.ocdd.io.Exceptions`](Exceptions.md)、[`me.omico.ocdd.io.FileAttributes`](FileAttributes.md)、[`me.omico.ocdd.io.FileSystemErrors`](FileSystemErrors.md)、[`me.omico.ocdd.io.Path`](Path.md)
- 非规范性外部参照：[Kotlin `kotlin.io.path`](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.io.path/)、[Android libcore `System`](https://android.googlesource.com/platform/libcore/+/master/ojluni/src/main/java/java/lang/System.java)、[Android libcore `File.createTempFile`](https://android.googlesource.com/platform/libcore/+/master/ojluni/src/main/java/java/io/File.java)

## 规范性定义

### 目标

本契约定义文件、目录、链接和临时对象的创建能力。

### 公共接口

```kotlin
package me.omico.ocdd.io

@Throws(IOException::class)
public expect fun Path.createFile(vararg attributes: FileAttribute): Path

@Throws(IOException::class)
public expect fun Path.createDirectory(vararg attributes: FileAttribute): Path

@Throws(IOException::class)
public expect fun Path.createDirectories(vararg attributes: FileAttribute): Path

@Throws(IOException::class)
public expect fun Path.createParentDirectories(vararg attributes: FileAttribute): Path

@Throws(IOException::class)
public expect fun Path.createLinkPointingTo(target: Path): Path

@Throws(IOException::class)
public expect fun Path.createSymbolicLinkPointingTo(
    target: Path,
    vararg attributes: FileAttribute,
): Path

@Throws(IOException::class)
public expect fun createTempDirectory(
    prefix: String? = null,
    vararg attributes: FileAttribute,
): Path

@Throws(IOException::class)
public expect fun createTempDirectory(
    directory: Path?,
    prefix: String? = null,
    vararg attributes: FileAttribute,
): Path

@Throws(IOException::class)
public expect fun createTempFile(
    prefix: String? = null,
    suffix: String? = null,
    vararg attributes: FileAttribute,
): Path

@Throws(IOException::class)
public expect fun createTempFile(
    directory: Path?,
    prefix: String? = null,
    suffix: String? = null,
    vararg attributes: FileAttribute,
): Path
```

## 可观察行为

- `createFile()` 和 `createDirectory()` 必须创建空目标并返回接收者；目标已存在时失败且不修改目标。
- `createDirectories()` 必须从最近的既有父目录开始，依次创建接收者及其缺失父目录；既有目录不变，接收者或任一必要父路径不是目录时以 `NOT_A_DIRECTORY` 失败。
- `createParentDirectories()` 只按相同顺序创建接收者的缺失父目录，接收者不变，并返回接收者。
- `createLinkPointingTo()` 必须创建指向 `target` 的硬链接；`createSymbolicLinkPointingTo()` 必须创建保存 `target` 路径值的符号链接。
- `directory` 为 `null` 时使用平台临时目录；`prefix` 为 `null` 时使用空前缀；临时文件的 `suffix` 为 `null` 时使用 `.tmp`，临时目录没有后缀。
- Android 的平台临时目录必须取自运行时 `java.io.tmpdir`；应用进程中该值指向应用私有缓存目录。属性不可用时抛出 `FileSystemException`，其中 `operation` 为 `CREATE`、`reason` 为 `IO_FAILURE`，且 `path` 为 `.`。
- 前后缀必须是良构 Unicode 文本，不含 `U+0000` 或 `/`；空字符串有效。无效参数必须在访问文件系统前抛出 `IllegalArgumentException`。
- 临时对象必须在选定目录中排他创建，并返回唯一且已存在的路径。名称依次由前缀、非空生成部分和后缀组成；生成文本和重试次数不是稳定接口。
- 创建属性必须符合 [`me.omico.ocdd.io.FileAttributes`](FileAttributes.md) 的创建属性矩阵。同一调用包含重复属性名时必须在访问文件系统前抛出 `IllegalArgumentException`。
- 单目标创建必须将全部属性与创建动作原子应用。多级目录创建对每个新目录分别原子应用属性，既有目录保持原状。
- 原子创建能力缺失时，必须在创建目标前抛出 `UnsupportedOperationException`。多级目录逐层完成；后续失败时保留已创建的父目录，并将 `FileSystemException.partialResult` 设为 `true`。
- `createDirectories()` 和 `createParentDirectories()` 负责补齐父目录；其他函数要求父目录已经存在。
- 创建目标已存在时必须抛出 `FileSystemException`，其中 `operation` 为 `CREATE`、`reason` 为 `ALREADY_EXISTS`。其他创建失败仍使用 `FileSystemException`：目标或必要父路径不存在、父路径不是目录、访问被拒绝和其他 I/O 失败的原因分别为 `NOT_FOUND`、`NOT_A_DIRECTORY`、`ACCESS_DENIED` 和 `IO_FAILURE`。
- 创建链接时，异常的 `path` 必须标识失败路径，`otherPath` 标识配对的链接或目标；其他创建操作的 `otherPath` 为 `null`。除多级目录部分完成外，只有确认未留下持久状态变化时，`partialResult` 才可以为 `false`。

## 边界与错误

### 不变量与违反条件

并发排他创建同一缺失目标时，最多一个调用成功。错误必须按本文映射为 `FileSystemException`、`UnsupportedOperationException` 或 `IllegalArgumentException`。原子性、错误分类、冲突保护或返回路径不符即违反契约。

### 边界

- 本契约范围是创建动作；内容写入、删除、传输和后续属性修改由各自契约定义。
- 临时对象的名称与创建顺序不是稳定接口。

## 兼容性

创建结果、冲突处理、父目录行为、属性原子性、链接和临时对象规则属于兼容性承诺。

## 验证要求

验证必须覆盖目标缺失与已存在，父路径存在、缺失、非目录和不可写，空与非空属性，链接受支持与不受支持，默认与指定临时目录，以及原子性、返回路径和错误分类。

### 规范示例

| 初始条件 | 操作 | 结果 |
| --- | --- | --- |
| 目标缺失且父目录存在 | `createFile()` | 创建空文件并返回接收者 |
| 目标已存在 | `createFile()` | 抛出原因是 `ALREADY_EXISTS` 的 `FileSystemException` 且目标不变 |
| 多级目录缺失 | `createDirectories()` | 创建完整目录链 |
| 指定临时目录可写 | `createTempFile(directory)` | 返回该目录中的既有唯一文件 |
| `prefix` 为 `null` 且 `suffix` 为 `null` | `createTempFile()` | 创建名称由生成部分和 `.tmp` 组成的唯一文件 |
| `prefix` 包含 `/` | `createTempDirectory(prefix)` | 抛出 `IllegalArgumentException` 且不访问文件系统 |
