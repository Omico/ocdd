package me.omico.ocdd.io

import okio.FileSystem
import okio.Path.Companion.toPath as toOkioPath

internal val systemFileSystem: FileSystem
    get() = FileSystem.SYSTEM

internal fun Path.toOkioPath(): okio.Path = toString().toOkioPath(normalize = false)
