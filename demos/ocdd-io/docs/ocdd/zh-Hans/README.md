# ocdd-io OCDD 契约

**项目契约版本：** 1.0.0

**项目级契约标识符：** `me.omico.ocdd.io`

**采用规范：** [OCDD 1.0.0 草案](../../../../../spec/1.0.0/zh-Hans.md)

## 项目级契约

### 产品范围

- `ocdd-io` 必须允许 Android 与 iOS 的公共代码使用同一组路径和文件系统能力。
- 支持范围必须覆盖路径计算、状态查询、创建、删除、读取、写入、目录遍历、复制、移动、链接、临时对象和文件属性。
- 等价的输入、文件系统状态和使用参数必须在两个平台产生等价且可理解的结果；平台固有差异必须以调用方能够识别和处理的方式表达。
- 公共调用方必须能够从共享代码使用全部受支持能力，不依赖平台专属路径类型、文件类型、文件系统知识或平台分支。
- 支持平台限于 Android 与 iOS。不影响公共能力和可观察结果的平台内部实现方式不属于契约。

### 参与方与接受

- 使用方包括共享 API 调用方，以及生成、实现、评审、验证或维护 `ocdd-io` 的项目维护者。
- 本契约约束项目构建逻辑、公共实现、一致性验证，以及入口索引所列契约单元的 Android 与 iOS 实现；窄契约可以声明更小的适用范围。
- 有合并权限的项目维护者负责接受项目级契约、入口索引列出的契约单元和实现结果。只有在能够从本目录的契约和构建逻辑恢复目标、公共接口、平台兼容性边界、依赖与验证入口，并取得规定的验证结果后，才能接受实现。

### 持久来源与再实现

- `docs/ocdd/zh-Hans/` 是项目约束、接口、可观察行为、平台适配边界和接受标准的唯一规范性来源。长期契约事实必须先写入相应的契约文档，再形成实现结果。
- Gradle 构建逻辑实现本文声明的目标平台、工具链约束、依赖边界、source set 关系、发布形态和验证入口。构建逻辑中的具体连接和任务图属于可替换实现，禁止增加、缩小或覆盖本文及各契约单元的规范性要求。
- `src/` 与测试是可以重新生成的实现结果，不是契约的规范性来源。测试必须从契约条件类别导出，禁止以既有测试或源码反向定义行为。
- 重新实现必须先读取项目入口与受影响的全部契约，再读取构建逻辑。具体源码组织和实现顺序可以替换，不得改变契约规定的公共结果和兼容性承诺。

### 平台兼容性边界

- Android 与 iOS 可以采用不同的内部实现，只要相同公共调用在等价条件下满足同一契约要求。
- 平台缺少直接满足契约的能力时，后备实现必须保持相同的公共结果、错误分类、原子性和资源生命周期。能力缺口及其可观察影响必须由受影响的窄契约或 [`me.omico.ocdd.io.Posix`](api/me/omico/ocdd/io/Posix.md) 定义。
- 新增或替换平台依赖不得改变公共接口、受支持平台、发布产物或已声明的可观察行为。
- 源文件划分、内部接口、辅助类型、函数形态和平台调用组合不属于契约，可以在保持上述结果的前提下重构。

### Kotlin 版本

项目使用的 Kotlin 版本必须是 2.0.21。

### Kotlin Multiplatform 互操作

- 全部公共 API 必须能够由 Kotlin Multiplatform 共享代码直接使用，不要求调用方提供 Android 或 iOS 专属分支。
- Android 与 iOS 必须向共享调用方提供相同的公共声明、默认参数和可见性。
- 公共 API 的编译元数据必须启用 explicit API，并保持 Kotlin Multiplatform 消费兼容性。

### 目标与构建

- Kotlin Multiplatform 目标必须包含 Android、iOS x64、iOS arm64 和 iOS simulator arm64。Android namespace 必须是 `me.omico.ocdd.io`，最低 API 必须是 21。
- Android API 21–25 与 API 26 及以上必须满足相同的公共契约；低版本能力边界由 [`me.omico.ocdd.io.Posix`](api/me/omico/ocdd/io/Posix.md) 定义。
- release AAR 必须包含 armeabi-v7a、arm64-v8a、x86 与 x86_64 的 POSIX 适配库。

### Okio 版本

- Okio 在 Android 必须使用 2.2.2，在 iOS 必须使用 3.3.0。
- [Okio 2.2.2](https://github.com/square/okio/tree/2.2.2)不提供 `okio.Path`。

### 兼容性、不变量与违反条件

项目级兼容性从契约使用方的角度判断。移除受支持的公共能力或平台、要求公共调用方依赖平台专属知识，或者使等价条件产生不同的公共结果，均属于破坏性变更。

除窄契约另有声明外，入口索引中每个契约单元的公共接口、可观察行为、边界和错误都是兼容性承诺。改变这些承诺时，必须按采用规范的项目契约版本与修订规则处理；源码仍可编译或现有验证通过，不能单独证明兼容。

### 一致性验证

- 除契约明确限定平台或条件外，每个契约单元的验证要求必须在 Android 与全部受支持的 iOS target 执行；等价条件必须运行相同的公共检查并产生相同结果。
- `./gradlew build` 必须使用 Gradle 标准生命周期执行 Kotlin Multiplatform 与 Android 插件接入的编译、测试、lint、原生构建和发布产物构建；禁止增加仅用于转发这些既有任务的项目包装任务。
- `./gradlew connectedDebugAndroidTest` 必须在当前连接设备运行公共契约与 Android 平台测试；公共契约测试必须实际进入设备测试套件，接受实现前必须分别取得 API 21–25 和 API 26 及以上设备证据。
- 评审必须确认每项平台后备都能追溯到本文或窄契约声明的能力缺口。
- 验证检查必须通过契约标识符、要求位置、检查名称、检查元数据或验证输出，使要求与对应检查可以相互定位。仅位于相关测试文件或测试套件中，不足以证明要求已经覆盖。

规范示例：删除 `src/` 后，维护者从契约导出相同公共 API 与平台结果；删除仅存在于构建目录的缓存不影响再实现；新增 Android target、改变 Okio 版本或降低设备覆盖时，必须先修改本项目级契约。

## 契约索引

- [`me.omico.ocdd.io.Charset`](api/me/omico/ocdd/io/Charset.md)：字符集值
- [`me.omico.ocdd.io.Charsets`](api/me/omico/ocdd/io/Charsets.md)：字符集常量
- [`me.omico.ocdd.io.DirectoryTraversal`](api/me/omico/ocdd/io/DirectoryTraversal.md)：目录访问与遍历
- [`me.omico.ocdd.io.Exceptions`](api/me/omico/ocdd/io/Exceptions.md)：I/O 异常类型
- [`me.omico.ocdd.io.FileAttributes`](api/me/omico/ocdd/io/FileAttributes.md)：文件属性
- [`me.omico.ocdd.io.FileCreation`](api/me/omico/ocdd/io/FileCreation.md)：文件系统对象创建
- [`me.omico.ocdd.io.FileDeletion`](api/me/omico/ocdd/io/FileDeletion.md)：文件系统对象删除
- [`me.omico.ocdd.io.FileReadWrite`](api/me/omico/ocdd/io/FileReadWrite.md)：文件内容读写
- [`me.omico.ocdd.io.FileStatus`](api/me/omico/ocdd/io/FileStatus.md)：路径与文件状态
- [`me.omico.ocdd.io.FileSystemErrors`](api/me/omico/ocdd/io/FileSystemErrors.md)：文件系统错误
- [`me.omico.ocdd.io.FileTransfer`](api/me/omico/ocdd/io/FileTransfer.md)：文件复制与移动
- [`me.omico.ocdd.io.Path`](api/me/omico/ocdd/io/Path.md)：路径值与词法运算
- [`me.omico.ocdd.io.Posix`](api/me/omico/ocdd/io/Posix.md)：POSIX 平台兼容性
