# `me.omico.ocdd.io.DirectoryTraversal`：目录访问与遍历

- 依赖契约：[`me.omico.ocdd.io.Exceptions`](Exceptions.md)、[`me.omico.ocdd.io.FileAttributes`](FileAttributes.md)、[`me.omico.ocdd.io.FileSystemErrors`](FileSystemErrors.md)、[`me.omico.ocdd.io.Path`](Path.md)
- 非规范性外部参照：[Kotlin `kotlin.io.path`](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.io.path/)

## 规范性定义

### 目标

本契约定义目录条目访问、目录树遍历与遍历控制。

### 公共接口

```kotlin
package me.omico.ocdd.io

public enum class PathWalkOption {
    INCLUDE_DIRECTORIES,
    BREADTH_FIRST,
    FOLLOW_LINKS,
}

public enum class FileVisitResult {
    CONTINUE,
    SKIP_SUBTREE,
    SKIP_SIBLINGS,
    TERMINATE,
}

public interface FileVisitor {
    public fun preVisitDirectory(
        directory: Path,
        attributes: FileAttributes,
    ): FileVisitResult

    public fun visitFile(
        file: Path,
        attributes: FileAttributes,
    ): FileVisitResult

    public fun visitFileFailed(
        file: Path,
        exception: IOException,
    ): FileVisitResult

    public fun postVisitDirectory(
        directory: Path,
        exception: IOException?,
    ): FileVisitResult
}

public expect class FileVisitorBuilder internal constructor() {
    public fun onPreVisitDirectory(action: (Path, FileAttributes) -> FileVisitResult)

    public fun onVisitFile(action: (Path, FileAttributes) -> FileVisitResult)

    public fun onVisitFileFailed(action: (Path, IOException) -> FileVisitResult)

    public fun onPostVisitDirectory(action: (Path, IOException?) -> FileVisitResult)
}

@Throws(IOException::class)
public expect fun Path.listDirectoryEntries(glob: String = "*"): List<Path>

@Throws(IOException::class)
public expect fun Path.forEachDirectoryEntry(
    glob: String = "*",
    action: (Path) -> Unit,
)

@Throws(IOException::class)
public expect fun <T> Path.useDirectoryEntries(
    glob: String = "*",
    block: (Sequence<Path>) -> T,
): T

public expect fun fileVisitor(builderAction: FileVisitorBuilder.() -> Unit): FileVisitor

@Throws(IOException::class)
public expect fun Path.visitFileTree(
    visitor: FileVisitor,
    maxDepth: Int = Int.MAX_VALUE,
    followLinks: Boolean = false,
)

@Throws(IOException::class)
public expect fun Path.visitFileTree(
    maxDepth: Int = Int.MAX_VALUE,
    followLinks: Boolean = false,
    builderAction: FileVisitorBuilder.() -> Unit,
)

@Throws(IOException::class)
public expect fun Path.walk(vararg options: PathWalkOption): Sequence<Path>
```

## 可观察行为

### 目录条目与 glob

- glob 只匹配直接子项名称：`*` 匹配零个或多个 Unicode 标量值，`?` 匹配一个，其他字符按字面匹配。glob 必须是良构 Unicode 文本且不含 `U+0000` 或 `/`，否则在访问文件系统前抛出 `IllegalArgumentException`。
- `listDirectoryEntries()` 返回按 `Path.compareTo()` 排序的只读快照；`forEachDirectoryEntry()` 和 `useDirectoryEntries()` 使用相同顺序，每个匹配项恰好产生一次。
- `useDirectoryEntries()` 必须提供只在回调期间有效的单次序列，并在回调结束时关闭目录资源。回调正常返回而关闭失败时必须抛出关闭异常；回调与关闭都失败时必须重新抛出回调异常，并将关闭异常作为 suppressed exception 附加。

### 序列遍历

- `walk()` 的接收者必须是既有目录。结果不含起始目录；默认以父目录先于后代的深度优先顺序产生所有非目录后代。
- `INCLUDE_DIRECTORIES` 加入目录后代，`BREADTH_FIRST` 改为按深度递增的广度优先顺序，`FOLLOW_LINKS` 跟随指向目录的符号链接并遍历后代。
- 未使用 `FOLLOW_LINKS` 时，符号链接作为非目录项产生，不访问目标；使用后，指向目录的链接在有 `INCLUDE_DIRECTORIES` 时作为目录产生。
- 同一父目录的子项先按 `Path.compareTo()` 排序。深度优先时依次完整访问各子树；广度优先时，同一深度按父目录及其子项的访问顺序产生结果。
- `walk()` 返回的序列必须是单次惰性序列；文件系统失败可以在迭代时抛出。跟随链接检测到循环时必须抛出原因是 `FILE_SYSTEM_LOOP` 的 `FileSystemException`。

### Visitor 遍历

- `visitFileTree()` 使用深度优先顺序，起始路径深度为 `0`，每层加一。`maxDepth < 0` 时在访问文件系统前抛出 `IllegalArgumentException`。
- 深度小于 `maxDepth` 的目录必须先完成直接子项枚举并关闭目录资源，再传给 `preVisitDirectory()`。枚举成功且前置回调返回 `CONTINUE` 时按序访问已枚举的子项，再以 `null` 异常调用 `postVisitDirectory()`。
- 深度等于 `maxDepth` 的目录和全部非目录项必须传给 `visitFile()`；该深度的目录只触发这一个回调。未跟随的符号链接视为非目录项。
- 读取节点属性或枚举目录直接子项失败时，必须在调用该目录的 `preVisitDirectory()` 前调用 `visitFileFailed()`，且不得为该目录调用 `postVisitDirectory()`。回调返回控制结果即视为已处理；回调抛出时立即终止遍历。
- 前置回调返回 `SKIP_SUBTREE` 时跳过后代和对应后置回调；返回 `SKIP_SIBLINGS` 时还跳过同级剩余项。其他回调的 `SKIP_SUBTREE` 等同 `CONTINUE`，`SKIP_SIBLINGS` 跳过当前父目录的剩余项。任一回调返回 `TERMINATE` 时正常结束，不再调用回调。
- `fileVisitor()` 未设置的前置和文件回调返回 `CONTINUE`；失败回调重新抛出收到的 `IOException`；后置回调在无异常时返回 `CONTINUE`，收到非空异常时重新抛出。本文规定的遍历流程只以 `null` 调用后置回调，保留可空异常参数用于公共接口兼容。每种回调最多设置一次，重复设置使 `fileVisitor()` 抛出 `IllegalArgumentException`。
- 两个 `visitFileTree()` 重载必须具有相同语义；builder 重载必须先通过 `fileVisitor()` 构造 visitor，再执行遍历。

### 资源与错误

- `walk()` 和 `visitFileTree()` 必须先完成当前目录直接子项的枚举并关闭该目录资源，再将这些子项产生为序列结果或传给 visitor。`useDirectoryEntries()` 的目录资源必须按“目录条目与 glob”一节规定的回调生命周期管理。
- 目录入口缺失、入口不是目录、访问被拒绝、跟随链接形成循环以及其他 I/O 失败必须表示为 `FileSystemException`，分别使用 `NOT_FOUND`、`NOT_A_DIRECTORY`、`ACCESS_DENIED`、`FILE_SYSTEM_LOOP` 和 `IO_FAILURE`。
- 目录条目打开或读取失败时 `operation` 为 `LIST`；`listDirectoryEntries()`、`forEachDirectoryEntry()` 或 `useDirectoryEntries()` 关闭目录资源失败时为 `CLOSE`；遍历时目录打开、列举或循环检测失败为 `TRAVERSE`；读取 visitor 属性失败为 `READ_ATTRIBUTES`。`path` 标识失败节点，`otherPath` 为 `null`，`partialResult` 为 `false`。
- `visitFileTree()` 按前述规则把文件系统失败传给 `visitFileFailed()`。回调未处理或重新抛出已有 `FileSystemException` 时保留全部公共字段；其他目录 API 直接抛出。调用方回调抛出的其他异常保持原类型。

## 边界与错误

### 不变量与违反条件

相同目录状态、选项和回调结果必须产生相同顺序。文件系统失败映射为 `FileSystemException`；无效 glob 或负 `maxDepth` 抛出 `IllegalArgumentException`。遗漏、重复、乱序、资源泄漏、分类或回调控制错误即违反契约。

### 边界

- 遍历逐项观察文件系统，并发变更可能出现在后续结果中。
- 文件属性值和目录项内容操作由其他契约定义。

## 兼容性

目录项顺序、glob 语义、遍历选项、回调控制、链接处理、资源生命周期和错误分类属于兼容性承诺。

## 验证要求

验证必须覆盖空与非空目录、嵌套目录、普通文件、符号链接和链接循环，默认和自定义 glob，深度与广度优先，包含与排除目录，全部回调控制结果、中途失败、错误分类和资源生命周期。

### 规范示例

| 条件 | 操作 | 结果 |
| --- | --- | --- |
| 含文件和子目录的目录 | `listDirectoryEntries()` | 返回排序后的直接子项快照 |
| 嵌套目录树 | `walk(INCLUDE_DIRECTORIES)` | 按深度优先顺序返回完整树 |
| 前置回调返回 `SKIP_SUBTREE` | `visitFileTree()` | 不访问该目录后代 |
| 跟随链接形成循环 | `walk(FOLLOW_LINKS)` | 抛出原因是 `FILE_SYSTEM_LOOP` 的 `FileSystemException` |
