# `me.omico.ocdd.io.FileTransfer`：文件复制与移动

- 依赖契约：[`me.omico.ocdd.io.Exceptions`](Exceptions.md)、[`me.omico.ocdd.io.FileAttributes`](FileAttributes.md)、[`me.omico.ocdd.io.FileSystemErrors`](FileSystemErrors.md)、[`me.omico.ocdd.io.Path`](Path.md)
- 非规范性外部参照：[Kotlin `kotlin.io.path`](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.io.path/)

## 规范性定义

### 目标

本契约定义文件和目录的复制、递归复制与移动。这些操作共享冲突和属性选项，作为一个契约单元评审。

### 公共接口

```kotlin
package me.omico.ocdd.io

public enum class FileCopyOption {
    REPLACE_EXISTING,
    COPY_ATTRIBUTES,
    ATOMIC_MOVE,
    NOFOLLOW_LINKS,
}

public enum class CopyActionResult {
    CONTINUE,
    SKIP_SUBTREE,
    TERMINATE,
}

public enum class OnErrorResult {
    SKIP_SUBTREE,
    TERMINATE,
}

public interface CopyActionContext {
    @Throws(IOException::class)
    public fun copyToIgnoringExistingDirectory(
        source: Path,
        target: Path,
        followLinks: Boolean,
    ): CopyActionResult
}

@Throws(IOException::class)
public expect fun Path.copyTo(
    target: Path,
    vararg options: FileCopyOption,
): Path

@Throws(IOException::class)
public expect fun Path.copyTo(
    target: Path,
    overwrite: Boolean = false,
): Path

@Throws(IOException::class)
public expect fun Path.copyToRecursively(
    target: Path,
    onError: (Path, Path, IOException) -> OnErrorResult = { _, _, exception -> throw exception },
    followLinks: Boolean,
    overwrite: Boolean,
): Path

@Throws(IOException::class)
public expect fun Path.copyToRecursively(
    target: Path,
    onError: (Path, Path, IOException) -> OnErrorResult = { _, _, exception -> throw exception },
    followLinks: Boolean,
    copyAction: CopyActionContext.(Path, Path) -> CopyActionResult = { source, target ->
        copyToIgnoringExistingDirectory(
            source,
            target,
            followLinks,
        )
    },
): Path

@Throws(IOException::class)
public expect fun Path.moveTo(
    target: Path,
    vararg options: FileCopyOption,
): Path

@Throws(IOException::class)
public expect fun Path.moveTo(
    target: Path,
    overwrite: Boolean = false,
): Path
```

## 可观察行为

### 单项复制与移动

- `copyTo()` 复制单个文件或目录项并返回 `target`；目录只复制自身。目标父目录须在调用前存在。
- 复制默认不覆盖；`REPLACE_EXISTING` 或 `overwrite = true` 可替换文件、符号链接或空目录。目标为非空目录时失败且不修改目标。
- 默认复制跟随源路径末尾的符号链接；`NOFOLLOW_LINKS` 复制链接本身。目标路径中的符号链接按替换目标处理。
- `COPY_ATTRIBUTES` 必须复制 [`me.omico.ocdd.io.FileAttributes`](FileAttributes.md) 中受支持且可写的属性；其他复制只保证内容结果。
- `moveTo()` 移动或重命名接收者并返回 `target`，默认不覆盖。移动符号链接只移动链接本身。
- `REPLACE_EXISTING` 或 `overwrite = true` 可在移动时替换文件、符号链接或空目录；目标为非空目录时失败且不修改源和目标。`ATOMIC_MOVE` 要求整个移动原子完成，不支持时抛出 `UnsupportedOperationException`。

### 递归复制

- `copyToRecursively()` 将源根映射到 `target`，后代映射到目标下的相同相对路径。非目录只处理源根；目录按 `Path.compareTo()` 排序同级项，并以父目录先于后代的深度优先顺序处理完整树。
- 目标与源相同或位于本次递归访问的源树内时，必须在产生持久变化前抛出 `IllegalArgumentException`；直接路径和符号链接解析后的路径采用同一判断。目标不存在时，必须解析最近的既有祖先，再按顺序拼回缺失名称，因此多层缺失父目录之前的符号链接仍参与重叠判断。
- `target` 的父目录须在调用前存在。源目录对应的目标缺失时创建目录；两者都是目录时保留既有目标并继续处理后代。
- `followLinks = false` 时复制源树中的链接本身；为 `true` 时复制链接目标，目标为目录则递归。目标树中的链接按覆盖策略替换或失败。源根和目标根之前的路径按普通规则跟随。
- `overwrite` 重载对目录合并外的每个目标使用同一策略：`false` 时既有目标失败；`true` 时可替换文件、链接或空目录，非空目录以 `DIRECTORY_NOT_EMPTY` 失败。该重载只复制内容。
- 自定义重载按遍历顺序为每个源项调用一次 `copyAction(sourceEntry, targetEntry)`。`copyToIgnoringExistingDirectory()` 在源和目标都是目录时保留目标并返回 `CONTINUE`；否则按 `followLinks` 复制单项，目标已存在时失败。
- `copyAction` 返回 `CONTINUE` 时继续；对目录返回 `SKIP_SUBTREE` 时跳过后代，对非目录返回时等同 `CONTINUE`；返回 `TERMINATE` 时正常返回 `target`，且不再调用任何回调。
- 读取、打开或复制源项失败，或 `copyAction` 抛出 `IOException` 时，调用 `onError(sourceEntry, targetEntry, exception)`。`SKIP_SUBTREE` 跳过失败目录的后代；失败项不是目录时只跳过该项。`TERMINATE` 正常返回 `target`；回调抛出时立即失败。回调主动抛出的非 `IOException` 直接传播，不转换或再次交给 `onError`。
- 未跳过或提前终止时，目标必须包含完整结果。因 `SKIP_SUBTREE` 或 `TERMINATE` 返回时保留已完成结果，不复制未处理项。
- 递归复制抛出或提前终止、非原子跨文件系统移动失败时，可以保留部分结果；异常必须标识失败阶段和相关路径。

`FileCopyOption` 的适用范围必须遵守下表：

| 选项 | `copyTo()` | `moveTo()` |
| --- | --- | --- |
| `REPLACE_EXISTING` | 允许替换既有目标 | 允许替换既有目标 |
| `COPY_ATTRIBUTES` | 复制全部受支持且可写的属性 | 无效 |
| `ATOMIC_MOVE` | 无效 | 要求整个移动原子完成 |
| `NOFOLLOW_LINKS` | 复制符号链接本身 | 无效 |

无效选项抛出 `IllegalArgumentException`，重复选项不改变结果。`moveTo()` 同时使用 `ATOMIC_MOVE` 和 `REPLACE_EXISTING` 时必须原子替换；无法保证时抛出 `UnsupportedOperationException`，且源和目标不变。布尔重载的 `false` 和 `true` 分别等价于不传选项和只传 `REPLACE_EXISTING`。

源或必要父路径不存在、目标已存在且替换未启用、父路径不是目录、替换目标是非空目录、访问被拒绝和其他 I/O 失败，必须抛出 `FileSystemException`，原因分别为 `NOT_FOUND`、`ALREADY_EXISTS`、`NOT_A_DIRECTORY`、`DIRECTORY_NOT_EMPTY`、`ACCESS_DENIED` 和 `IO_FAILURE`。`operation` 标识实际失败的 `COPY`、`MOVE`、`CREATE`、`DELETE`、`READ_ATTRIBUTES` 或 `WRITE_ATTRIBUTES` 阶段；`path` 标识失败路径，`otherPath` 标识对应源或目标。目标已变化或源已移除时，`partialResult` 为 `true`。递归复制将异常交给 `onError`；未处理或重新抛出时保留全部字段。其他 API 直接抛出该异常。

## 边界与错误

### 不变量与违反条件

复制成功后源不变，移动成功后源不存在，返回值等于 `target`。未跳过或提前终止的递归复制形成完整目标树。错误必须按本文映射为 `FileSystemException`、`UnsupportedOperationException` 或 `IllegalArgumentException`。错误分类、覆盖或链接策略、回调或原子性不符即违反契约。

### 边界

- `partialResult` 描述失败前已经完成的变化；回滚、清理和物理持久化由调用方决定。
- 文件内容和属性值的语义由相应契约定义。

## 兼容性

复制和移动结果、冲突与链接策略、递归控制、属性、原子性、部分结果和错误分类属于兼容性承诺。

## 验证要求

验证必须覆盖文件、空目录、非空目录和符号链接，目标缺失与已存在，同一与不同文件存储，覆盖、完整选项矩阵、属性复制、链接跟随、原子移动、递归回调的全部结果、源目标重叠、中途失败、错误分类和部分结果。

### 规范示例

| 条件 | 操作 | 结果 |
| --- | --- | --- |
| 目标缺失 | `copyTo(target)` | 复制对象并返回目标路径 |
| 目标已存在 | `copyTo(target)` | 抛出原因是 `ALREADY_EXISTS` 的 `FileSystemException` 且目标不变 |
| 目标已存在 | `copyTo(target, overwrite = true)` | 替换目标 |
| 支持原子移动 | `moveTo(target, ATOMIC_MOVE)` | 原子移动并返回目标路径 |
| 任意源和目标 | `copyTo(target, ATOMIC_MOVE)` | 抛出 `IllegalArgumentException` |
| 目标已存在且不覆盖 | `moveTo(target)` | 抛出原因是 `ALREADY_EXISTS` 的 `FileSystemException` |
