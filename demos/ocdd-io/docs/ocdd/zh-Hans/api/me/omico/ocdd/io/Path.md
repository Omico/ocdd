# `me.omico.ocdd.io.Path`：路径值与词法运算

- 非规范性外部参照：[Java SE 17 `java.nio.file.Path`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Path.html)、[Kotlin `kotlin.io.path`](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.io.path/)

## 规范性定义

### 目标

本契约定义项目路径值及其纯词法运算。`Path` 表示文件系统位置，本文操作只处理传入的文本，不读取文件系统或当前工作目录。目标可以不存在。

Java NIO 和 Kotlin 标准库仅说明 API 来源；公共声明和行为以本契约为准。

### 公共接口

`commonMain` 必须提供以下等价接口，并遵守项目级 `expect`/`actual` 约束。

```kotlin
package me.omico.ocdd.io

public expect class Path internal constructor(
    value: String,
) : Comparable<Path>,
    Iterable<Path> {
    public val root: Path?
    public val fileName: Path?
    public val parent: Path?
    public val nameCount: Int
    public val segments: List<String>
    public val isAbsolute: Boolean
    public val isRelative: Boolean
    public val isRoot: Boolean

    public operator fun get(index: Int): Path

    public fun subpath(
        beginIndex: Int,
        endIndex: Int,
    ): Path

    @Throws(IllegalArgumentException::class)
    public operator fun div(other: String): Path

    public operator fun div(other: Path): Path

    @Throws(IllegalArgumentException::class)
    public fun resolve(other: String): Path

    public fun resolve(other: Path): Path

    @Throws(IllegalArgumentException::class)
    public fun resolveSibling(other: String): Path

    public fun resolveSibling(other: Path): Path

    @Throws(IllegalArgumentException::class)
    public fun relativize(other: Path): Path

    @Throws(IllegalArgumentException::class)
    public fun startsWith(other: String): Boolean

    public fun startsWith(other: Path): Boolean

    @Throws(IllegalArgumentException::class)
    public fun endsWith(other: String): Boolean

    public fun endsWith(other: Path): Boolean

    public fun normalize(): Path

    public override fun iterator(): Iterator<Path>

    public override fun compareTo(other: Path): Int

    public override fun equals(other: Any?): Boolean

    public override fun hashCode(): Int

    public override fun toString(): String

    public companion object {
        public val DIRECTORY_SEPARATOR: String
    }
}

public expect class FileUri(
    value: String,
) {
    public val value: String

    public override fun equals(other: Any?): Boolean

    public override fun hashCode(): Int

    public override fun toString(): String
}

public expect val Path.name: String
public expect val Path.extension: String
public expect val Path.nameWithoutExtension: String
public expect val Path.pathString: String
public expect val Path.invariantSeparatorsPathString: String

@Throws(IllegalArgumentException::class)
public expect fun String.toPath(): Path

@Throws(IllegalArgumentException::class)
public expect fun pathOf(
    first: String,
    vararg more: String,
): Path

@Throws(IllegalArgumentException::class)
public expect fun FileUri.toPath(): Path

@Throws(IllegalArgumentException::class)
public expect fun Path.relativeTo(base: Path): Path

public expect fun Path.relativeToOrNull(base: Path): Path?

public expect fun Path.relativeToOrSelf(base: Path): Path
```

构造器对库外不可见；调用方通过转换、工厂函数或返回 `Path` 的公共操作获得实例。`segments` 返回只读快照；`iterator()` 按相同顺序返回各名称元素组成的相对 `Path`。

`String.toPath()`、`pathOf()` 和 `FileUri.toPath()` 分别对应 Kotlin 的单路径构造、多段构造和 `URI.toPath()` 能力。

## 可观察行为

### 适用条件

本契约适用于 Android 与 iOS 公共接口形成的全部 `Path` 和 `FileUri`，包括：

1. 空路径、根路径、绝对路径和相对路径；
2. 普通名称、`.`、`..`、反斜杠、ASCII 大小写差异、Unicode 名称和无效文本；
3. 单个、重复、前导和尾随 `/`；
4. 名称索引、子路径、前后缀判断、解析、同级解析、相对化、相等和排序；
5. 有效与无效的文件 URI；
6. Android 与每个受支持的 iOS target。

多个类别共同影响结果时，必须覆盖其组合。

### 路径模型

#### 表示与解析

`Path` 由可选根和有序名称元素组成：

- `/` 是唯一的分隔符和根；`DIRECTORY_SEPARATOR` 等于 `/`。
- 一个或多个前导 `/` 表示同一根；重复 `/` 合并为一个；除根外，尾随 `/` 不形成名称元素。
- 空字符串表示空路径。空路径是包含一个空名称元素的相对路径。
- 解析时保留 `.` 和 `..`；`\` 是普通名称字符。
- 字符串表示使用 `/` 并反映上述解析结果。
- 输入必须是良构 Unicode 文本且不含 `U+0000`，否则抛出 `IllegalArgumentException`。

`pathOf(first, more)` 依次以 `/` 连接非空字符串，再按上述规则解析。全部为空时返回空路径；第一个非空部分决定绝对性，后续部分的前导 `/` 仅作为连接分隔符。

#### 查询与名称

- `root`：绝对路径返回根，相对路径返回 `null`。
- `fileName`：返回最后一个名称组成的相对路径；根返回 `null`，空路径返回自身。
- `parent`：返回根和除末项外的名称；根、空路径和单名称相对路径返回 `null`。
- `nameCount`：名称数量；根为 `0`，空路径为 `1`。
- `segments`：不含根的名称列表；根为空列表，空路径为 `[""]`。
- `isAbsolute`：当且仅当存在根；`isRelative` 必须等于 `!isAbsolute`；`isRoot` 当且仅当路径等于其根。
- `get(index)`：返回指定名称组成的相对路径；索引超出 `0..<nameCount` 时抛出 `IllegalArgumentException`。
- `subpath(beginIndex, endIndex)`：返回名称区间组成的相对路径；区间为空、越界或逆序时抛出 `IllegalArgumentException`。
- `name`：等于 `fileName?.toString().orEmpty()`。
- `extension`：`name` 最后一个 `.` 后的内容；无 `.` 时为空字符串。
- `nameWithoutExtension`：`name` 最后一个 `.` 前的内容；无 `.` 时为完整名称。
- `pathString` 与 `invariantSeparatorsPathString`：都等于 `toString()`。

所有查询只依据抽象路径状态。

#### 规范化

`normalize()` 重复应用以下规则直至稳定：

- 删除 `.` 名称元素。
- 普通名称后紧跟 `..` 时同时删除两者。
- 相对路径中无法配对的前导 `..` 保留。
- 绝对路径中超出根的 `..` 删除。
- 相对路径的全部名称被删除时返回空路径；绝对路径的全部名称被删除时返回根路径。

规范化保持根和绝对性，并满足 `path.normalize().normalize() == path.normalize()`。

#### 前后缀与组合

`startsWith` 和 `endsWith` 按根与完整名称比较，不按字符串子串比较。带根参数仅在根相同时匹配；无根的 `endsWith` 参数可匹配绝对路径末尾的完整名称序列。字符串参数先按本契约解析。

`base / other` 等价于 `base.resolve(other)`。`resolve` 遵守：

- `other` 是绝对路径时返回 `other`。
- `other` 是空路径时返回 `base`。
- `base` 是空路径时返回 `other`。
- 其他情况返回将 `other` 的名称元素追加到 `base` 后的路径。
- 解析不隐式执行 `normalize()`。

`resolveSibling(other)` 在 `parent` 存在时返回 `parent.resolve(other)`，否则返回 `other`。绝对 `other` 返回自身；空 `other` 在有父路径时返回父路径，否则返回空路径。

#### 相对化

`base.relativize(target)` 构造从 `base` 到 `target` 的相对路径：

- 一个绝对、另一个相对时抛出 `IllegalArgumentException`。
- 任一操作数包含 `.` 或 `..` 时，先对两个操作数执行 `normalize()`；后续规则中的 `base` 和 `target` 都指规范化后的值。
- 两者相等时返回空路径。
- `base` 是空路径时返回 `target`。
- 删除相同的前缀名称；规范化后 `base` 的剩余名称包含 `..` 时抛出 `IllegalArgumentException`。
- 为 `base` 的每个剩余名称生成一个 `..`，再追加 `target` 的剩余名称。

`path.relativeTo(base)` 等价于 `base.relativize(path)`。仅相对化失败时，`relativeToOrNull` 返回 `null`，`relativeToOrSelf` 返回接收者；其他异常继续抛出。

#### 文件 URI

`FileUri` 保存并返回构造时验证的 URI。有效值使用 `file` scheme 和绝对路径，不含用户信息、端口、查询或片段，authority 仅可为空或 `localhost`。scheme 与 `localhost` 按 ASCII 大小写不敏感比较。

`FileUri.toPath()` 以 UTF-8 百分号解码 URI path，再按本契约形成绝对 `Path`。URI 语法、scheme、authority、百分号编码或解码文本无效时抛出 `IllegalArgumentException`。

#### 相等、哈希与排序

`Path` 按根和解析后的名称比较，不比较文件身份。名称大小写以及保留的 `.`、`..` 均可区分。

相等的 `Path` 具有相同哈希值。`compareTo` 按 `toString()` 的 UTF-8 字节做无符号、区分大小写的字典序比较；结果为 `0` 当且仅当路径相等。

`FileUri` 相等性和哈希只依据其验证后的 `value`；`toString()` 必须返回 `value`。

## 边界与错误

### 不变量与违反条件

每个 `Path` 必须满足：

- 创建后不可变并可由并发调用方安全共享；
- `toString().toPath() == this`；
- `isAbsolute != isRelative`；
- `isRoot == (this == root)`；
- `nameCount == segments.size`；
- 所有纯词法操作对相同输入产生相同结果且不访问文件系统。

无效路径文本、索引、子路径区间、不可相对化路径和无效文件 URI 产生 `IllegalArgumentException`。有效输入的词法操作是纯计算，结果与平台 I/O 状态无关。

接口或结果不符、缺少必要异常或泄漏平台差异即违反契约。

### 边界

- 公共路径模型采用 `/` 根和分隔语义；Windows 盘符、UNC 根和文件系统 provider 使用平台路径 API。
- 文件存在性、权限、符号链接解析、文件身份、真实路径、当前工作目录解析和绝对路径转换由文件操作契约定义。
- 本契约范围限于路径值与词法运算；shell 展开、环境变量、文件监听和平台类型桥接由其他能力处理。

## 兼容性

路径模型、查询、词法运算、URI、比较、异常和公共声明属于兼容性承诺。

## 验证要求

### 规范示例

测试至少覆盖下表及全部适用查询属性：

| 输入 | 字符串表示 | 根 | `segments` | `fileName` | `parent` | `normalize()` |
| ----------- | ----------- | ------ | ---------------- | ---------- | --------- | ------------- |
| 空字符串 | 空字符串 | `null` | `[""]` | 空路径 | `null` | 空路径 |
| `.` | `.` | `null` | `[.]` | `.` | `null` | 空路径 |
| `/` | `/` | `/` | `[]` | `null` | `null` | `/` |
| `//a//b/` | `/a/b` | `/` | `[a, b]` | `b` | `/a` | `/a/b` |
| `a/./b` | `a/./b` | `null` | `[a, ., b]` | `b` | `a/.` | `a/b` |
| `a/../../b` | `a/../../b` | `null` | `[a, .., .., b]` | `b` | `a/../..` | `../b` |
| `a\b` | `a\b` | `null` | `[a\b]` | `a\b` | `null` | `a\b` |

组合、匹配、相对化和转换必须至少覆盖以下结果：

| 操作 | 结果 |
| --------------------------------------------- | -------- |
| `pathOf("/a", "/b", "c")` | `/a/b/c` |
| `"/a".toPath().resolve("/b")` | `/b` |
| `"a/b".toPath().resolveSibling("c")` | `a/c` |
| `"/a/b".toPath().startsWith("/a")` | `true` |
| `"/a/b".toPath().endsWith("a/b")` | `true` |
| `"/a/b".toPath().relativize("/a/c".toPath())` | `../c` |
| `"a".toPath().relativize("a".toPath())` | 空路径 |
| `"".toPath().relativize("./a".toPath())` | `a` |
| `FileUri("file:///a%20b").toPath()` | `/a b` |

测试还须覆盖名称索引与区间边界、文件名属性、迭代、相等、哈希、排序、良构 Unicode、`U+0000`、无效百分号编码和不可相对化路径。

### 性质与参照验证

生成式测试须验证规范化幂等、字符串往返、相等与哈希一致、比较与相等一致、`resolve` 与 `/` 等价、迭代与 `segments` 一致，以及成功相对化后可经解析和规范化还原目标。

随机输入覆盖各条件类别，使用固定 seed，并在失败证据中记录契约标识、输入、平台和 seed。随机测试仅补充确定性边界测试。

JVM 参照验证比较本文覆盖的 Java SE 17 默认 UNIX `Path` 行为。本契约明确列出的跨平台差异使用独立测试验证。

### 接受标准

1. 全部规范示例、边界、性质和条件类别通过验证。
2. JVM 参照验证通过，规定的跨平台差异具有独立证据。
