# `me.omico.ocdd.io.Exceptions`：I/O 异常类型

- 非规范性外部参照：[Okio `CommonPlatform.kt`](https://github.com/lysine-dev/okio/blob/main/okio/src/commonMain/kotlin/okio/CommonPlatform.kt#L35-L46)

## 规范性定义

### 目标

本契约定义共享代码可使用的 I/O 异常类型。四个类型的公共声明和行为以本文为准。

### 公共接口

`commonMain` 必须提供以下声明：

```kotlin
package me.omico.ocdd.io

public expect open class IOException(
    message: String?,
    cause: Throwable?,
) : Exception {
    public constructor(message: String?)
    public constructor()
}

public expect class ProtocolException(
    message: String,
) : IOException

public expect open class EOFException(
    message: String?,
) : IOException {
    public constructor()
}

public expect class FileNotFoundException(
    message: String?,
) : IOException
```

Android 必须提供以下声明：

```kotlin
package me.omico.ocdd.io

public actual typealias IOException = java.io.IOException

public actual typealias ProtocolException = java.net.ProtocolException

public actual typealias EOFException = java.io.EOFException

public actual typealias FileNotFoundException = java.io.FileNotFoundException
```

iOS 必须提供以下声明：

```kotlin
package me.omico.ocdd.io

public actual open class IOException actual constructor(
    message: String?,
    cause: Throwable?,
) : Exception(message, cause) {
    public actual constructor(message: String?) : this(message, null)
    public actual constructor() : this(null, null)
}

public actual class ProtocolException actual constructor(
    message: String,
) : IOException(message)

public actual open class EOFException actual constructor(
    message: String?,
) : IOException(message) {
    public actual constructor() : this(null)
}

public actual open class FileNotFoundException actual constructor(
    message: String?,
) : IOException(message)
```

## 可观察行为

本契约适用于共享代码对这四个类型的构造、抛出、捕获和类型检查。

- `IOException` 必须是 `Exception` 的公共、开放子类，并原样保留双参数构造的 `message` 和 `cause`。单参数构造的 `cause` 必须为 `null`；无参数构造的 `message` 和 `cause` 必须均为 `null`。
- `ProtocolException` 必须继承 `IOException`，并原样保留非空 `message`。
- `EOFException` 必须是 `IOException` 的开放子类。单参数构造必须原样保留可空 `message`；无参数构造的 `message` 必须为 `null`。
- `FileNotFoundException` 必须继承 `IOException`，并原样保留可空 `message`。
- `ProtocolException`、`EOFException` 和 `FileNotFoundException` 的 `cause` 必须为 `null`。
- Android 类型必须分别映射到声明中的 Java 类型；iOS 类型必须由本文的 `actual` 类提供。

## 边界与错误

### 不变量与违反条件

四个类型必须保持本文声明的构造入口、继承关系、开放性和平台映射。声明不匹配、构造值丢失、Android 未映射到对应 Java 类型或平台类型层次不同即违反契约。

### 边界

- 各能力契约定义具体抛出条件。
- 稳定接口限于异常类型和继承关系；消息格式与平台错误码由平台实现决定。
- 四个异常均为顶层类型，直接通过包名访问。

## 兼容性

异常类型、构造函数、继承关系、开放性和平台映射属于兼容性承诺。

## 验证要求

验证必须覆盖全部构造入口、类型层次、开放性和 Android 类型别名。

### 规范示例

| 条件 | 结果 |
| --------------------------------------- | ----------------------------------------------------- |
| `IOException("read failed", cause)` | `message` 为 `read failed`，`cause` 保持不变 |
| `IOException()` | `message` 和 `cause` 均为 `null` |
| `ProtocolException("unexpected frame")` | `message` 为 `unexpected frame`，且属于 `IOException` |
| `EOFException()` | `message` 和 `cause` 均为 `null` |
| `FileNotFoundException("missing")` | `message` 为 `missing`，且属于 `IOException` |

### 接受标准

1. `commonMain`、Android 和全部受支持的 iOS target 在 Kotlin 2.0.21 下编译通过，`expect` 与 `actual` 精确匹配。
2. 每个构造入口的 `message`、`cause`、继承关系和开放性均有跨平台验证证据。
3. Android 验证证据确认四个公共类型分别与声明中的 Java 类型相同。
