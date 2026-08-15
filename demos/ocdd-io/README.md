# ocdd-io

> **免责声明：** 本示例仅用于展示 OCDD 规范及其使用方式，不对依据这些文档生成或重新实现代码所取得的效果作出保证。即使所有测试通过，也不表示相关代码适合进入生产环境。

`ocdd-io` 是一个 Kotlin Multiplatform 文件系统 API 示例，用于展示如何以 OCDD 契约作为可观察行为的持久来源，再由契约实现 Android 与 iOS 行为并导出一致性验证。

本 README 介绍示例工程的使用方式，不是契约的规范性来源。

## OCDD 文档

- [项目入口与项目级契约](docs/ocdd/zh-Hans/README.md)
- [API 契约索引](docs/ocdd/zh-Hans/README.md#契约索引)
- [采用的 OCDD 1.0.0 简体中文规范](../../spec/1.0.0/zh-Hans.md)

发生歧义时，以项目入口和入口索引指向的契约单元为准。

## 支持平台

- Android，最低 API 21；
- iOS x64；
- iOS arm64；
- iOS simulator arm64。

公共 API 位于 `commonMain`。平台文件系统调用和错误转换分别位于 `androidMain` 与 `iosMain`。

## 工程结构

| 路径 | 内容 |
| --- | --- |
| `docs/ocdd/zh-Hans/` | 项目级契约和 API 契约 |
| `src/commonMain/` | 公共 API 与跨平台规则 |
| `src/androidMain/` | Android 实现与最小 POSIX 适配 |
| `src/iosMain/` | iOS 实现 |
| `src/commonTest/` | 公共契约测试 |
| `src/androidUnitTest/` | Android JVM 单元测试 |
| `src/androidInstrumentedTest/` | Android 设备测试 |

## 构建与验证

工程使用 JDK 17、Kotlin 2.0.21 和 Gradle Wrapper。Android 构建还需要可用的 Android SDK；iOS 目标需要 macOS 与 Xcode 工具链。

在本目录运行完整 Gradle 构建：

```shell
./gradlew build
```

连接 Android 设备或模拟器后运行设备测试：

```shell
./gradlew connectedDebugAndroidTest
```

项目契约要求分别取得 API 21–25 和 API 26 及以上设备的验证证据。具体接受条件以 [项目级契约](docs/ocdd/zh-Hans/README.md#一致性验证) 为准。
