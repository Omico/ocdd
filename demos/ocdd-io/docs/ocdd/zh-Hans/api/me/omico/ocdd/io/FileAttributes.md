# `me.omico.ocdd.io.FileAttributes`：文件属性

- 依赖契约：[`me.omico.ocdd.io.Exceptions`](Exceptions.md)、[`me.omico.ocdd.io.FileStatus`](FileStatus.md)、[`me.omico.ocdd.io.FileSystemErrors`](FileSystemErrors.md)、[`me.omico.ocdd.io.Path`](Path.md)
- 非规范性外部参照：[Kotlin `kotlin.io.path`](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.io.path/)

## 规范性定义

### 目标

本契约定义文件属性、属性视图、属性快照和文件存储信息。

### 公共接口

```kotlin
package me.omico.ocdd.io

public enum class FileType {
    REGULAR_FILE,
    DIRECTORY,
    SYMBOLIC_LINK,
    OTHER,
}

public enum class PosixFilePermission {
    OWNER_READ,
    OWNER_WRITE,
    OWNER_EXECUTE,
    GROUP_READ,
    GROUP_WRITE,
    GROUP_EXECUTE,
    OTHERS_READ,
    OTHERS_WRITE,
    OTHERS_EXECUTE,
}

public expect class FileTime(
    epochSeconds: Long,
    nanosecondsOfSecond: Int = 0,
) : Comparable<FileTime> {
    public val epochSeconds: Long
    public val nanosecondsOfSecond: Int

    public override fun compareTo(other: FileTime): Int

    public override fun equals(other: Any?): Boolean

    public override fun hashCode(): Int

    public override fun toString(): String
}

public expect class FileAttribute(
    name: String,
    value: Any?,
) {
    public val name: String
    public val value: Any?
}

public expect class FileAttributes internal constructor() {
    public val type: FileType
    public val size: Long
    public val creationTime: FileTime?
    public val lastModifiedTime: FileTime
    public val lastAccessTime: FileTime?
    public val owner: String?
    public val permissions: Set<PosixFilePermission>?
}

public interface FileAttributeView {
    @Throws(IOException::class)
    public fun read(): FileAttributes

    @Throws(IOException::class)
    public operator fun get(name: String): Any?

    @Throws(IOException::class)
    public operator fun set(
        name: String,
        value: Any?,
    )
}

public expect class FileStore internal constructor() {
    public val name: String
    public val type: String
    public val isReadOnly: Boolean
    public val totalSpace: Long?
    public val usableSpace: Long?
    public val unallocatedSpace: Long?
}

@Throws(IOException::class)
public expect fun Path.fileAttributesView(
    view: String = "basic",
    vararg options: LinkOption,
): FileAttributeView

public expect fun Path.fileAttributesViewOrNull(
    view: String = "basic",
    vararg options: LinkOption,
): FileAttributeView?

@Throws(IOException::class)
public expect fun Path.readAttributes(vararg options: LinkOption): FileAttributes

@Throws(IOException::class)
public expect fun Path.readAttributes(
    attributes: String,
    vararg options: LinkOption,
): Map<String, Any?>

@Throws(IOException::class)
public expect fun Path.getAttribute(
    attribute: String,
    vararg options: LinkOption,
): Any?

@Throws(IOException::class)
public expect fun Path.setAttribute(
    attribute: String,
    value: Any?,
    vararg options: LinkOption,
): Path

@Throws(IOException::class)
public expect fun Path.getLastModifiedTime(vararg options: LinkOption): FileTime

@Throws(IOException::class)
public expect fun Path.setLastModifiedTime(value: FileTime): Path

@Throws(IOException::class)
public expect fun Path.getOwner(vararg options: LinkOption): String?

@Throws(IOException::class)
public expect fun Path.setOwner(value: String): Path

@Throws(IOException::class)
public expect fun Path.getPosixFilePermissions(vararg options: LinkOption): Set<PosixFilePermission>

@Throws(IOException::class)
public expect fun Path.setPosixFilePermissions(value: Set<PosixFilePermission>): Path

@Throws(IOException::class)
public expect fun Path.fileStore(): FileStore
```

## 可观察行为

- 完整属性名称必须使用 `view:name` 格式。视图必须接受 `basic`、`owner` 和 `posix`；名称和视图按 ASCII 大小写敏感比较。
- 属性值、可空性和操作能力如下。“可写”表示可由属性视图、`setAttribute()` 或类型化函数修改；“创建”表示可通过 `FileAttribute` 传给创建函数。

| 属性 | 读取值 | 写入值 | 创建值 |
| --- | --- | --- | --- |
| `basic:size` | `Long` | 不可写 | 不可用 |
| `basic:creationTime` | `FileTime?` | 不可写 | 不可用 |
| `basic:lastModifiedTime` | `FileTime` | `FileTime` | 不可用 |
| `basic:lastAccessTime` | `FileTime?` | 不可写 | 不可用 |
| `basic:isRegularFile` | `Boolean` | 不可写 | 不可用 |
| `basic:isDirectory` | `Boolean` | 不可写 | 不可用 |
| `basic:isSymbolicLink` | `Boolean` | 不可写 | 不可用 |
| `basic:isOther` | `Boolean` | 不可写 | 不可用 |
| `owner:owner` | `String?` | `String` | 不可用 |
| `posix:permissions` | `Set<PosixFilePermission>?` | `Set<PosixFilePermission>` | `Set<PosixFilePermission>` |

- 字符串属性 API 必须返回表中的读取值类型。权限集合是只读快照，且只能包含 `PosixFilePermission`。
- `FileAttribute` 仅表示创建属性，并原样保存 `name` 和 `value`。名称必须是表中可创建的完整名称，值须符合对应类型；否则构造时抛出 `IllegalArgumentException`。
- `FileAttributes.type` 和 `size` 必须描述读取时的类型与大小；时间、所有者和权限必须来自同一次读取。无法提供可选属性时返回 `null`；显式请求不可用的视图或类型化属性时抛出 `UnsupportedOperationException`。
- 两个属性视图函数只创建绑定到接收者、视图名和链接选项的视图，首次读取由视图操作触发。视图不可用时，`fileAttributesView()` 抛出 `UnsupportedOperationException`，`fileAttributesViewOrNull()` 返回 `null`。
- `readAttributes()` 返回一次读取形成的只读快照。字符串重载接受 `[view:]name[,name...]`：默认视图为 `basic`，前缀作用于全部名称，单独的 `*` 选择该视图的所有受支持属性，重复名称只返回一次。映射键使用完整的 `view:name`。
- `getAttribute()` 和 `setAttribute()` 只接受一个 `[view:]name`，默认视图为 `basic`，不接受逗号或 `*`。`FileAttributeView` 的名称不含前缀，且必须属于该视图。设置成功时返回接收者。
- 未知视图或属性、格式错误、值类型错误，以及含 `null` 或其他类型元素的权限集合，必须抛出 `IllegalArgumentException`。不可用的视图或属性，以及写入只读属性，必须抛出 `UnsupportedOperationException`。
- `LinkOption.NOFOLLOW_LINKS` 必须使全部属性读取和写入操作作用于路径本身；未提供时必须跟随符号链接。
- 普通文件的 `size` 为字节数，其他类型为 `0`。
- `FileTime.nanosecondsOfSecond` 必须在 `0..999_999_999` 内。比较依次使用秒和纳秒；两个字段相等时，实例相等且哈希相同。`toString()` 返回 `<epochSeconds>:<nanosecondsOfSecond>`，纳秒固定为九位 ASCII 十进制数并以零补齐。
- 属性写入精度为毫秒，低于一毫秒的部分向零舍弃。
- `FileStore.name` 是平台提供的稳定显示名称，`type` 是平台文件系统类型；仅当存储拒绝文件系统写入时，`isReadOnly` 为 `true`。名称和类型是平台本地值。
- `fileStore()` 返回目标所在存储的不可变快照。无法可靠取得的空间值为 `null`；其他值非负，且 `usableSpace`、`unallocatedSpace` 不大于 `totalSpace`。
- 目标不存在、访问被拒绝和其他 I/O 失败必须抛出 `FileSystemException`，原因分别为 `NOT_FOUND`、`ACCESS_DENIED` 和 `IO_FAILURE`。读取、写入和存储查询的 `operation` 分别为 `READ_ATTRIBUTES`、`WRITE_ATTRIBUTES` 和 `FILE_STORE`；`path` 为接收者，`otherPath` 为 `null`。写入失败且可能已修改属性时，`partialResult` 为 `true`；其他属性错误为 `false`。

## 边界与错误

### 不变量与违反条件

属性快照和权限集合必须只读，并保持读取时的值。错误必须按本文映射为 `FileSystemException`、`UnsupportedOperationException` 或 `IllegalArgumentException`。错误分类、值类型、精度、快照或接口不符即违反契约。

### 边界

- 本契约范围是公共接口中声明的 basic、owner 和 POSIX 属性；平台专属视图与访问控制列表采用平台 API。
- 空间值描述查询时容量，后续查询可以随文件系统状态变化。

## 兼容性

属性名称和值类型、视图能力、快照语义、时间精度、权限、owner、文件存储结果和错误分类属于兼容性承诺。

## 验证要求

验证必须覆盖普通文件、目录、符号链接和其他类型，跟随与不跟随链接，可用与不可用的时间、所有者和 POSIX 权限，有效与无效属性名和值，可写与只读存储，以及类型、快照和错误分类。

### 规范示例

| 条件 | 操作 | 结果 |
| --- | --- | --- |
| 三字节普通文件 | `readAttributes()` | `type` 为普通文件且 `size` 为 `3` |
| 支持 POSIX 权限 | 设置后读取权限 | 返回相同权限集合 |
| 不支持 owner 视图 | `fileAttributesViewOrNull("owner")` | 返回 `null` |
| 纳秒值超出范围 | 构造 `FileTime` | 抛出 `IllegalArgumentException` |
| 时间字段为 `1` 秒和 `2` 纳秒 | `FileTime.toString()` | `1:000000002` |
| 属性表达式为 `basic:size,lastModifiedTime` | `readAttributes()` | 返回两个使用完整名称的映射项 |
