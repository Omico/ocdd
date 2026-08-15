package me.omico.ocdd.io

public enum class LinkOption {
    NOFOLLOW_LINKS,
}

@Throws(IOException::class)
public expect fun Path.absolute(): Path

@Throws(IOException::class)
public expect fun Path.absolutePathString(): String

@Throws(IOException::class)
public expect fun Path.toRealPath(vararg options: LinkOption): Path

public expect fun Path.exists(vararg options: LinkOption): Boolean

public expect fun Path.notExists(vararg options: LinkOption): Boolean

public expect fun Path.isDirectory(vararg options: LinkOption): Boolean

public expect fun Path.isRegularFile(vararg options: LinkOption): Boolean

public expect fun Path.isSymbolicLink(): Boolean

public expect fun Path.isReadable(): Boolean

public expect fun Path.isWritable(): Boolean

public expect fun Path.isExecutable(): Boolean

public fun Path.isHidden(): Boolean = name.startsWith(".") && name != "." && name != ".."

@Throws(IOException::class)
public expect fun Path.isSameFileAs(other: Path): Boolean

@Throws(IOException::class)
public expect fun Path.fileSize(): Long

@Throws(IOException::class)
public expect fun Path.readSymbolicLink(): Path
