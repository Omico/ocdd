# `me.omico.ocdd.io.Charsets`：字符集常量

- 依赖契约：[`me.omico.ocdd.io.Charset`](Charset.md)
- 非规范性外部参照：[Kotlin `Charsets`](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.text/-charsets/)

## 规范性定义

### 目标

本契约定义跨平台文件 API 使用的字符集常量。

### 公共接口

`commonMain` 必须直接提供等价于以下声明的接口：

```kotlin
package me.omico.ocdd.io

public object Charsets {
    public val UTF_8: Charset = Charset("UTF-8")
}
```

## 可观察行为

每次读取 `UTF_8` 都必须得到不可变且彼此相等的 `Charset`，其 `name` 为 `UTF-8`。

`UTF_8` 必须按 UTF-8 在 Unicode 标量值和字节序列之间转换，编码结果从首个内容字节开始，不含自动添加的字节顺序标记。文本 API 解码无效或不完整的字节序列时必须失败。

## 边界与错误

### 不变量与违反条件

`Charsets.UTF_8.name` 必须始终为 `UTF-8`。常量缺失、名称或编解码行为不符，或无效输入被静默替换，即违反契约。

### 边界

- 公共字符集常量集合由 `UTF_8` 组成；其他字符集和名称查找使用平台 API。
- 文件打开方式、读取、写入、追加、异常类型和资源生命周期由 [`me.omico.ocdd.io.FileReadWrite`](FileReadWrite.md) 定义。

## 兼容性

`UTF_8` 的存在、名称和编解码行为属于兼容性承诺。

## 验证要求

验证必须覆盖名称、重复读取、空文本、ASCII、不同长度的 UTF-8 序列、补充平面字符、字节顺序标记作为文本内容以及无效和截断的字节序列。

### 规范示例

| 输入 | 操作 | 结果 |
| --------------- | ------------ | ------------ |
| `A` | UTF-8 编码 | `41` |
| `文` | UTF-8 编码 | `E6 96 87` |
| `F0 9F 98 80` | UTF-8 解码 | `😀` |
| `C0 AF` | UTF-8 解码 | 失败 |
