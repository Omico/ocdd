package me.omico.ocdd.io

public expect class Path internal constructor(
    value: String,
) : Comparable<Path>,
    Iterable<Path> {
    public val root: Path?
    public val fileName: Path?
    public val parent: Path?
    public val nameCount: Int
    public val segments: List<String>
    public val isAbsolute: Boolean
    public val isRelative: Boolean
    public val isRoot: Boolean

    public operator fun get(index: Int): Path

    public fun subpath(
        beginIndex: Int,
        endIndex: Int,
    ): Path

    @Throws(IllegalArgumentException::class)
    public operator fun div(other: String): Path

    public operator fun div(other: Path): Path

    @Throws(IllegalArgumentException::class)
    public fun resolve(other: String): Path

    public fun resolve(other: Path): Path

    @Throws(IllegalArgumentException::class)
    public fun resolveSibling(other: String): Path

    public fun resolveSibling(other: Path): Path

    @Throws(IllegalArgumentException::class)
    public fun relativize(other: Path): Path

    @Throws(IllegalArgumentException::class)
    public fun startsWith(other: String): Boolean

    public fun startsWith(other: Path): Boolean

    @Throws(IllegalArgumentException::class)
    public fun endsWith(other: String): Boolean

    public fun endsWith(other: Path): Boolean

    public fun normalize(): Path

    public override fun iterator(): Iterator<Path>

    public override fun compareTo(other: Path): Int

    public override fun equals(other: Any?): Boolean

    public override fun hashCode(): Int

    public override fun toString(): String

    public companion object {
        public val DIRECTORY_SEPARATOR: String
    }
}

public expect class FileUri(
    value: String,
) {
    public val value: String

    public override fun equals(other: Any?): Boolean

    public override fun hashCode(): Int

    public override fun toString(): String
}

public expect val Path.name: String
public expect val Path.extension: String
public expect val Path.nameWithoutExtension: String
public expect val Path.pathString: String
public expect val Path.invariantSeparatorsPathString: String

@Throws(IllegalArgumentException::class)
public expect fun String.toPath(): Path

@Throws(IllegalArgumentException::class)
public expect fun pathOf(
    first: String,
    vararg more: String,
): Path

@Throws(IllegalArgumentException::class)
public expect fun FileUri.toPath(): Path

@Throws(IllegalArgumentException::class)
public expect fun Path.relativeTo(base: Path): Path

public expect fun Path.relativeToOrNull(base: Path): Path?

public expect fun Path.relativeToOrSelf(base: Path): Path
