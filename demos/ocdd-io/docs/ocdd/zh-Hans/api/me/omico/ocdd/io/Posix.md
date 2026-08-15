# `me.omico.ocdd.io.Posix`：POSIX 平台兼容性

- 依赖契约：[`me.omico.ocdd.io.DirectoryTraversal`](DirectoryTraversal.md)、[`me.omico.ocdd.io.FileAttributes`](FileAttributes.md)、[`me.omico.ocdd.io.FileCreation`](FileCreation.md)、[`me.omico.ocdd.io.FileDeletion`](FileDeletion.md)、[`me.omico.ocdd.io.FileReadWrite`](FileReadWrite.md)、[`me.omico.ocdd.io.FileStatus`](FileStatus.md)、[`me.omico.ocdd.io.FileSystemErrors`](FileSystemErrors.md)、[`me.omico.ocdd.io.FileTransfer`](FileTransfer.md)、[`me.omico.ocdd.io.Path`](Path.md)
- 非规范性外部参照：[Android `android.system.Os`](https://developer.android.com/reference/android/system/Os)、[Android Java NIO desugaring 支持表](https://developer.android.com/studio/write/java11-nio-support-table)

## 规范性定义

### 目标

本契约定义 Android 与 iOS 在平台能力不足或行为不一致时必须保持的平台兼容性结果。公开接口和各项操作的完整语义由依赖契约定义；本契约不新增公共 API，也不规定内部接口、源文件划分或辅助函数形态。

### 公共接口

本契约不声明独立公共接口。调用方只使用依赖契约声明的 `ocdd-io` API。

## 可观察行为

### 跨平台结果

- 公共能力集合、支持平台和最低版本以项目级契约为唯一规范性来源；本契约规定这些能力在适用平台存在能力缺口或行为差异时的公共结果。
- 平台可以选择不同系统能力，但相同操作在等价输入和文件系统状态下必须产生依赖契约规定的相同返回值、状态变化、错误分类和资源生命周期。
- 平台能力无法满足某项可选操作时，必须按所属契约抛出 `UnsupportedOperationException`，并在产生持久状态变化前结束操作。

### Android 兼容性

- 用于满足项目级契约 Android 版本条件的证据必须来自设备或等价系统；Android JVM 测试不能替代该证据。
- API 21–25 的路径规范化、解析、相对化、文件身份、符号链接读取、删除、目录复制和链接状态查询必须满足对应公共契约，不得泄漏 desugared Java NIO 与高版本 Java NIO 的行为差异。
- API 21–25 的属性快照必须从同一次平台状态读取中取得文件类型、大小、修改时间和访问时间。修改时间的读写必须保持毫秒精度。
- API 21–25 的 owner 视图不可用时必须报告能力不支持；数字 UID 不得作为 owner 名称返回。API 26 及以上的 owner 结果必须遵守 [`me.omico.ocdd.io.FileAttributes`](FileAttributes.md)。
- 全部 Android API level 的文件存储查询必须返回所属契约规定的空间值。挂载名称、类型或只读标记无法取得时，可以使用稳定后备值，但不得使空间查询失败或产生越界值。
- 复制属性过程中发生后续失败时，必须按 [`me.omico.ocdd.io.FileTransfer`](FileTransfer.md) 报告已经产生的部分结果。

### iOS 兼容性

- iOS 的文件类型、大小、文件身份、owner、创建时间、修改时间和权限必须来自同一次平台属性快照；跟随符号链接时，快照必须描述最终解析的目标。
- `SYNC`、`DSYNC`、排他创建和追加必须满足 [`me.omico.ocdd.io.FileReadWrite`](FileReadWrite.md) 的持久化、冲突和写入顺序要求。
- 平台写入在仍有待写字节时没有取得进展，必须以 `IO_FAILURE` 失败，不得静默形成截断结果。
- 平台错误必须按本节“错误映射”转换；未能确定更具体原因时使用 `IO_FAILURE`，不得依赖错误消息文本分类。

### 错误映射

| 平台错误 | `FileSystemErrorReason` |
| --- | --- |
| `ENOENT` | `NOT_FOUND` |
| `EEXIST` | `ALREADY_EXISTS` |
| `ENOTDIR` | `NOT_A_DIRECTORY` |
| `EISDIR` | `IS_A_DIRECTORY` |
| `ENOTEMPTY` | `DIRECTORY_NOT_EMPTY` |
| `EACCES`、`EPERM` | `ACCESS_DENIED` |
| `ELOOP` | `FILE_SYSTEM_LOOP` |
| 其他或未分类错误 | `IO_FAILURE` |

参数错误和能力缺失分别保持 `IllegalArgumentException` 与 `UnsupportedOperationException`。已有 `FileSystemException` 必须保持其公共字段，除非所属操作契约明确要求重新绑定 `operation`、`path`、`otherPath` 或 `partialResult`。

### 原子性与部分结果

- 创建权限和排他创建必须满足 [`me.omico.ocdd.io.FileCreation`](FileCreation.md) 的原子性要求。
- 修改文件系统后发生的失败必须按所属公共契约设置 `partialResult`。
- 属性原子应用能力缺失时，必须在修改文件系统前抛出 `UnsupportedOperationException`。

## 边界与错误

### 不变量与违反条件

全部平台必须保持公共能力集合、属性快照、时间精度、错误映射、创建原子性和部分结果。低版本或平台差异导致公共返回值、状态变化或错误字段偏离依赖契约，即违反本契约。

### 边界

- 本契约只定义平台兼容性结果；公开属性、创建、读写、删除、遍历和传输行为由相应 API 契约定义。
- 内部接口、源码组织、平台库组合和具体系统调用可以替换，只要继续满足本文及依赖契约。
- 平台库内部算法不属于本契约。

## 兼容性

本文声明的公共能力一致性、错误映射、属性精度、原子性和部分结果属于面向契约使用方的兼容性承诺。支持平台和最低版本由项目级契约定义。内部接口、源文件名称、辅助类型和具体平台调用不是兼容性承诺。

## 验证要求

验证必须在项目级契约定义的全部适用平台和版本条件下执行受影响的公共契约测试。验证至少包括低版本能力分派、属性快照、毫秒级修改时间、owner 可用性、文件存储空间、错误映射、原子创建、同步写入和修改后的失败。

### 规范示例

| 条件 | 操作 | 结果 |
| --- | --- | --- |
| Android API 25 | 创建带权限文件 | 权限与创建原子完成，或在创建前报告不支持 |
| Android API 21–25 | 读取修改时间 | 保持毫秒精度 |
| Android 任一 API level | 查询文件存储空间 | 返回满足属性契约边界的空间值 |
| iOS 平台错误为 `ENOTEMPTY` | 删除非空目录 | 原因为 `DIRECTORY_NOT_EMPTY` |
| iOS 平台错误无法细分 | 执行文件操作 | 原因为 `IO_FAILURE` |
| 任一平台在修改后失败 | 执行创建、写入或传输 | 按所属契约报告 `partialResult` |

### 接受标准

属性快照、时间精度、owner 可用性、空间值、错误映射、原子性和部分结果必须具有在错误实现时会失败的检查。
