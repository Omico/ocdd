# `me.omico.ocdd.io.Charset`：字符集值

## 规范性定义

### 目标

本契约定义跨平台文本编码标识 `Charset`。文件 API 使用该类型接收字符集，不暴露平台字符集类型。

### 公共接口

`commonMain` 必须直接提供等价于以下声明的接口：

```kotlin
package me.omico.ocdd.io

import kotlin.jvm.JvmInline

@JvmInline
public value class Charset internal constructor(
    public val name: String,
) {
    public override fun toString(): String = name
}
```

## 可观察行为

本契约适用于 `commonMain` 创建并由公共 API 暴露的每个 `Charset`。

- `name` 必须是创建时提供的非空 ASCII 名称，并在实例生命周期内保持不变。
- `name` 是唯一底层值。两个实例当且仅当 `name` 完全相同时相等，且相等实例的哈希值相同；`toString()` 必须返回 `name`。
- `Charset` 由项目契约公开的实例提供，构造函数保持内部可见。

空名称或非 ASCII 名称只能由项目内部错误产生，并构成契约违反。

## 边界与错误

### 不变量与违反条件

`Charset` 必须不可变、没有独立对象身份，并始终满足 `charset.toString() == charset.name`。底层值、名称、相等性、哈希或可见性不符合本文即违反契约。

### 边界

- 公共字符集由项目契约中的命名实例组成；查找、名称解析、自定义注册和平台转换采用平台 API。
- 具体字符集常量及其编码和解码行为由公开该实例的契约定义。
- 文件读取、写入、错误和资源生命周期由 [`me.omico.ocdd.io.FileReadWrite`](FileReadWrite.md) 定义。

## 兼容性

`Charset` 的名称、值语义、字符串表示和构造函数可见性属于兼容性承诺。

## 验证要求

验证必须覆盖底层表示、同一与不同名称、ASCII 大小写差异、重复读取属性、相等性、哈希、字符串表示、构造函数可见性和不可变性。

### 规范示例

| 条件 | 结果 |
| ----------------------------- | -------------------- |
| 两个实例名称相同 | 实例相等且哈希相同 |
| 两个实例名称大小写不同 | 实例不相等 |
| 读取 `name` 和 `toString()` | 返回相同文本 |
