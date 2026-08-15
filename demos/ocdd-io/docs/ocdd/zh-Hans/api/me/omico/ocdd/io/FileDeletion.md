# `me.omico.ocdd.io.FileDeletion`：文件系统对象删除

- 依赖契约：[`me.omico.ocdd.io.Exceptions`](Exceptions.md)、[`me.omico.ocdd.io.FileSystemErrors`](FileSystemErrors.md)、[`me.omico.ocdd.io.Path`](Path.md)
- 非规范性外部参照：[Kotlin `kotlin.io.path`](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.io.path/)

## 规范性定义

### 目标

本契约定义单个文件系统对象和目录树的删除能力。

### 公共接口

```kotlin
package me.omico.ocdd.io

@Throws(IOException::class)
public expect fun Path.deleteExisting()

@Throws(IOException::class)
public expect fun Path.deleteIfExists(): Boolean

@Throws(IOException::class)
public expect fun Path.deleteRecursively()
```

## 可观察行为

- `deleteExisting()` 必须删除既有文件、符号链接或空目录；目标不存在或目录非空时失败。删除符号链接只删除链接本身。
- `deleteIfExists()` 删除成功时返回 `true`，目标不存在时返回 `false`；其他失败直接抛出。
- `deleteRecursively()` 必须删除单个目标或完整目录树；目标不存在时正常返回。失败前完成的删除可以保留，异常必须标识失败路径。
- `deleteExisting()` 的目标不存在时必须抛出 `FileSystemException`，其中 `operation` 为 `DELETE`、`reason` 为 `NOT_FOUND`；删除非空目录和访问被拒绝时必须分别使用 `DIRECTORY_NOT_EMPTY` 和 `ACCESS_DENIED`，其他 I/O 失败必须使用 `IO_FAILURE`。
- 删除异常的 `path` 必须标识失败路径，`otherPath` 为 `null`。递归删除在移除任何目标后失败时，`partialResult` 必须为 `true`；只有确认未改变持久状态时才可以为 `false`。

## 边界与错误

### 不变量与违反条件

成功返回后，应删除的目标必须不存在。文件系统失败必须按本文映射为 `FileSystemException`。掩盖失败、错误分类不符、跟随符号链接或删除不完整即违反契约。

### 边界

- `partialResult` 描述失败前已经完成的删除；恢复、事务和安全擦除由调用方另行处理。
- 并发创建或修改目标时，每次调用按当时观察到的文件系统状态执行。

## 兼容性

目标处理、符号链接规则、递归行为、部分结果和错误分类属于兼容性承诺。

## 验证要求

验证必须覆盖目标不存在、普通文件、符号链接、空目录、非空目录、目录树中途失败、权限拒绝、错误分类和部分结果。

### 规范示例

| 初始条件 | 操作 | 结果 |
| --- | --- | --- |
| 普通文件 | `deleteExisting()` | 删除目标 |
| 目标不存在 | `deleteIfExists()` | 返回 `false` |
| 非空目录 | `deleteExisting()` | 抛出原因是 `DIRECTORY_NOT_EMPTY` 的 `FileSystemException` |
| 目录树 | `deleteRecursively()` | 删除完整目录树 |
