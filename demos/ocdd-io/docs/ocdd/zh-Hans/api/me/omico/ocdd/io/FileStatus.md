# `me.omico.ocdd.io.FileStatus`：路径与文件状态

- 依赖契约：[`me.omico.ocdd.io.Exceptions`](Exceptions.md)、[`me.omico.ocdd.io.FileSystemErrors`](FileSystemErrors.md)、[`me.omico.ocdd.io.Path`](Path.md)
- 非规范性外部参照：[Kotlin `kotlin.io.path`](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.io.path/)

## 规范性定义

### 目标

本契约定义路径定位、文件状态查询和符号链接读取。

### 公共接口

`commonMain` 必须提供等价于以下声明的接口：

```kotlin
package me.omico.ocdd.io

public enum class LinkOption {
    NOFOLLOW_LINKS,
}

@Throws(IOException::class)
public expect fun Path.absolute(): Path

@Throws(IOException::class)
public expect fun Path.absolutePathString(): String

@Throws(IOException::class)
public expect fun Path.toRealPath(vararg options: LinkOption): Path

public expect fun Path.exists(vararg options: LinkOption): Boolean

public expect fun Path.notExists(vararg options: LinkOption): Boolean

public expect fun Path.isDirectory(vararg options: LinkOption): Boolean

public expect fun Path.isRegularFile(vararg options: LinkOption): Boolean

public expect fun Path.isSymbolicLink(): Boolean

public expect fun Path.isReadable(): Boolean

public expect fun Path.isWritable(): Boolean

public expect fun Path.isExecutable(): Boolean

public fun Path.isHidden(): Boolean = name.startsWith(".") && name != "." && name != ".."

@Throws(IOException::class)
public expect fun Path.isSameFileAs(other: Path): Boolean

@Throws(IOException::class)
public expect fun Path.fileSize(): Long

@Throws(IOException::class)
public expect fun Path.readSymbolicLink(): Path
```

## 可观察行为

- 相对路径必须在操作开始时按进程当前工作目录定位。
- `absolute()` 必须返回当前工作目录与接收者解析所得的绝对路径且不隐式规范化；`absolutePathString()` 必须等于 `absolute().toString()`。
- `toRealPath()` 要求目标存在并返回绝对规范路径。默认解析全部符号链接；使用 `NOFOLLOW_LINKS` 时不解析任何链接，且只有不改变文件身份时，才可消除链接前的名称与后续 `..`。
- `exists()` 只在能够确认目标存在时返回 `true`；`notExists()` 只在能够确认目标不存在时返回 `true`。状态无法确定时两者都返回 `false`。
- 其他布尔状态查询也只在确认条件成立时返回 `true`；目标不存在或因访问拒绝、链接循环等失败而无法确认时返回 `false`。
- `NOFOLLOW_LINKS` 按前述规则改变 `toRealPath()`，并使存在性、类型和属性查询检查路径本身。`isSymbolicLink()` 与 `readSymbolicLink()` 始终操作链接本身。
- `isHidden()` 是不访问文件系统的纯词法查询，当且仅当最后一个名称以 `.` 开头且不是 `.` 或 `..` 时返回 `true`。
- `isSameFileAs()` 必须比较文件身份；路径相等时返回 `true`，其他情况要求两个目标存在。
- `fileSize()` 必须返回普通文件的字节数；目标不存在、为目录或无法读取大小时必须失败。
- `readSymbolicLink()` 必须原样返回链接保存的目标路径值。
- `absolute()` 取得当前工作目录失败时必须抛出 `FileSystemException`，其中 `operation` 为 `ABSOLUTE_PATH`、`reason` 为 `IO_FAILURE`。
- `toRealPath()` 遇到目标不存在、访问被拒绝、链接循环或其他 I/O 失败时必须抛出 `FileSystemException`，分别使用 `NOT_FOUND`、`ACCESS_DENIED`、`FILE_SYSTEM_LOOP` 和 `IO_FAILURE`，`operation` 必须为 `REAL_PATH`。
- `isSameFileAs()`、`fileSize()` 和 `readSymbolicLink()` 遇到目标不存在、访问拒绝、链接循环和其他 I/O 失败时，必须抛出 `FileSystemException`，原因分别为 `NOT_FOUND`、`ACCESS_DENIED`、`FILE_SYSTEM_LOOP` 和 `IO_FAILURE`，`operation` 为 `STATUS`。布尔查询遇到这些失败时返回 `false`。
- `fileSize()` 的目标为目录时使用 `IS_A_DIRECTORY`；`readSymbolicLink()` 的目标不是符号链接时使用 `NOT_A_SYMBOLIC_LINK`。`path` 标识失败路径；仅 `isSameFileAs()` 的 `otherPath` 标识另一个路径；所有状态异常的 `partialResult` 为 `false`。

## 边界与错误

### 不变量与违反条件

查询是只读操作。应报告的失败必须映射为 `FileSystemException`。缺少错误、分类不符、出现未声明的平台差异或接口不匹配即违反契约。

### 边界

- 本契约范围是文件系统状态查询；词法路径、完整属性、内容和变更操作由各自契约定义。
- `exists()` 与 `notExists()` 不是彼此的逻辑取反。

## 兼容性

路径定位、状态查询、链接策略、文件身份和错误分类属于兼容性承诺。

## 验证要求

验证必须覆盖绝对与相对路径，目标不存在、普通文件、目录、最终与中间符号链接、悬空符号链接，跟随与不跟随链接，权限允许、拒绝和状态无法确定，以及查询结果和错误分类。

### 规范示例

| 条件 | 操作 | 结果 |
| --- | --- | --- |
| 目标存在 | `exists()` | `true` |
| 目标不存在 | `notExists()` | `true` |
| 状态无法确定 | 两个存在性函数 | 均为 `false` |
| 普通文件内容为三个字节 | `fileSize()` | `3` |
| 符号链接 | `readSymbolicLink()` | 返回链接保存的路径值 |
| 含中间符号链接 | `toRealPath(NOFOLLOW_LINKS)` | 返回不解析任何符号链接的绝对规范路径 |
