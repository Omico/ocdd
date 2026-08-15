# `me.omico.ocdd.io.FileSystemErrors`：文件系统错误

- 依赖契约：[`me.omico.ocdd.io.Exceptions`](Exceptions.md)、[`me.omico.ocdd.io.Path`](Path.md)

## 规范性定义

### 目标

本契约定义文件系统错误的稳定分类和上下文。各能力契约规定具体映射，本文规定公共错误类型的共同语义。

### 公共接口

`commonMain` 必须直接提供等价于以下声明的接口：

```kotlin
package me.omico.ocdd.io

public enum class FileSystemOperation {
    ABSOLUTE_PATH,
    REAL_PATH,
    STATUS,
    CREATE,
    DELETE,
    OPEN,
    READ,
    WRITE,
    CLOSE,
    LIST,
    TRAVERSE,
    COPY,
    MOVE,
    READ_ATTRIBUTES,
    WRITE_ATTRIBUTES,
    FILE_STORE,
}

public enum class FileSystemErrorReason {
    NOT_FOUND,
    ALREADY_EXISTS,
    NOT_A_DIRECTORY,
    IS_A_DIRECTORY,
    NOT_A_SYMBOLIC_LINK,
    DIRECTORY_NOT_EMPTY,
    ACCESS_DENIED,
    FILE_SYSTEM_LOOP,
    INVALID_ENCODING,
    IO_FAILURE,
}

public class FileSystemException internal constructor(
    message: String,
    public val operation: FileSystemOperation,
    public val path: Path,
    public val otherPath: Path? = null,
    public val reason: FileSystemErrorReason,
    public val partialResult: Boolean = false,
) : IOException(message)
```

## 可观察行为

本契约适用于公共文件系统 API 报告的全部 I/O 失败。

- `operation` 必须标识实际失败阶段。复合操作必须报告内部复制、删除、关闭或属性阶段，而非外层函数名。
- `path` 必须标识发生失败的项目路径。双路径操作的 `otherPath` 必须标识与该阶段相关的另一路径；单路径操作必须为 `null`。
- `reason` 必须使用能力契约规定的稳定原因，是跨平台错误分类的唯一依据。
- `partialResult` 必须在失败前可能已经留下请求产生的持久状态变化时为 `true`；只有实现能够确认没有留下此类变化时才可以为 `false`。
- `message` 仅供诊断；调用方通过结构化字段识别操作、路径、原因和部分结果。
- 平台 I/O 失败必须转换为 `FileSystemException`。已有 `FileSystemException` 穿过能力边界时必须保留全部公共字段。
- 请求的平台能力不可用时必须抛出 `UnsupportedOperationException`，调用参数不满足契约时必须抛出 `IllegalArgumentException`；两类错误保持原类型。

## 边界与错误

### 不变量与违反条件

`FileSystemException` 必须继承 `me.omico.ocdd.io.IOException`，公共字段不可变。同一失败在 Android 与 iOS 上必须产生相同的 `operation`、`reason` 和部分结果分类。继承关系或字段错误、平台异常泄漏或分类依赖消息即违反契约。

### 边界

- 稳定接口是公共异常类型与结构化字段；消息、平台原因链、日志和平台错误码用于诊断。
- 各文件操作的失败条件和具体映射由抛出错误的能力契约定义。
- 失败后的状态由 `partialResult` 表达，后续清理由调用方决定。

## 兼容性

错误类型、操作和原因枚举、路径字段、部分结果及平台异常隔离属于兼容性承诺。

## 验证要求

验证必须覆盖能力契约声明的操作与原因组合、单路径与双路径操作、完整与部分结果、平台异常转换、公共字段不可变性、`IOException` 继承关系和消息非依赖性。

### 规范示例

| 条件 | 结果 |
| --- | --- |
| 对缺失文件调用 `readBytes()` 并在内部打开阶段失败 | `operation` 为 `OPEN`，`reason` 为 `NOT_FOUND`，`path` 为目标 |
| 创建既有目标 | `operation` 为 `CREATE`，`reason` 为 `ALREADY_EXISTS` |
| 递归复制中途失败并已创建目标 | `operation` 标识失败阶段，`otherPath` 标识配对路径，`partialResult` 为 `true` |
| 平台拒绝访问 | `reason` 为 `ACCESS_DENIED`，公共字段不包含平台类型 |
