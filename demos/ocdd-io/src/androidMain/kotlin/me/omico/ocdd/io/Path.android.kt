package me.omico.ocdd.io

import java.net.URI
import java.nio.file.Paths

public actual class Path :
    Comparable<Path>,
    Iterable<Path> {
    internal val nio: java.nio.file.Path

    internal actual constructor(value: String) {
        requireValidUnicode(value, "path")
        nio = Paths.get(value)
    }

    internal constructor(nio: java.nio.file.Path) {
        this.nio = nio
    }

    public actual val root: Path?
        get() = nio.root?.let(::Path)
    public actual val fileName: Path?
        get() = nio.fileName?.let(::Path)
    public actual val parent: Path?
        get() = nio.parent?.let(::Path)
    public actual val nameCount: Int
        get() = nio.nameCount
    public actual val segments: List<String>
        get() = (0 until nio.nameCount).map { nio.getName(it).toString() }
    public actual val isAbsolute: Boolean
        get() = nio.isAbsolute
    public actual val isRelative: Boolean
        get() = !nio.isAbsolute
    public actual val isRoot: Boolean
        get() = nio.root == nio

    public actual operator fun get(index: Int): Path {
        require(index in 0 until nameCount) { "Path name index $index is outside 0..<$nameCount" }
        return Path(nio.getName(index))
    }

    public actual fun subpath(
        beginIndex: Int,
        endIndex: Int,
    ): Path {
        require(beginIndex >= 0 && endIndex <= nameCount && beginIndex < endIndex) {
            "Invalid path name range [$beginIndex, $endIndex) for $nameCount names"
        }
        return Path(nio.subpath(beginIndex, endIndex))
    }

    @Throws(IllegalArgumentException::class)
    public actual operator fun div(other: String): Path = resolve(other)

    public actual operator fun div(other: Path): Path = resolve(other)

    @Throws(IllegalArgumentException::class)
    public actual fun resolve(other: String): Path = resolve(other.toPath())

    public actual fun resolve(other: Path): Path {
        if (!usesAndroidPosixFallback) return Path(nio.resolve(other.nio))
        if (other.isAbsolute) return other
        if (other.pathString.isEmpty()) return this
        if (pathString.isEmpty()) return other
        return when {
            isRoot -> "/${other.pathString}"
            else -> "$pathString/${other.pathString}"
        }.toPath()
    }

    @Throws(IllegalArgumentException::class)
    public actual fun resolveSibling(other: String): Path = resolveSibling(other.toPath())

    public actual fun resolveSibling(other: Path): Path =
        if (usesAndroidPosixFallback) parent?.resolve(other) ?: other else Path(nio.resolveSibling(other.nio))

    @Throws(IllegalArgumentException::class)
    public actual fun relativize(other: Path): Path {
        require(isAbsolute == other.isAbsolute) { "Cannot relativize an absolute path against a relative path" }
        val containsDots = segments.any(::isDotName) || other.segments.any(::isDotName)
        val base = if (containsDots) normalize() else this
        val target = if (containsDots) other.normalize() else other
        if (base == target) return "".toPath()
        if (base.pathString.isEmpty()) return target
        val commonCount =
            base.segments
                .zip(target.segments)
                .takeWhile { (left, right) -> left == right }
                .size
        require(".." !in base.segments.drop(commonCount)) {
            "Base path cannot be relativized after an unmatched '..'"
        }
        if (!usesAndroidPosixFallback) return Path(base.nio.relativize(target.nio))
        return (List(base.segments.size - commonCount) { ".." } + target.segments.drop(commonCount))
            .joinToString("/")
            .toPath()
    }

    @Throws(IllegalArgumentException::class)
    public actual fun startsWith(other: String): Boolean = startsWith(other.toPath())

    public actual fun startsWith(other: Path): Boolean = nio.startsWith(other.nio)

    @Throws(IllegalArgumentException::class)
    public actual fun endsWith(other: String): Boolean = endsWith(other.toPath())

    public actual fun endsWith(other: Path): Boolean = nio.endsWith(other.nio)

    public actual fun normalize(): Path = if (usesAndroidPosixFallback) normalizeAndroidPath() else Path(nio.normalize())

    public actual override fun iterator(): Iterator<Path> =
        nio
            .iterator()
            .asSequence()
            .map(::Path)
            .iterator()

    public actual override fun compareTo(other: Path): Int = compareUtf8PathStrings(toString(), other.toString())

    public actual override fun equals(other: Any?): Boolean = other is Path && nio == other.nio

    public actual override fun hashCode(): Int = nio.hashCode()

    public actual override fun toString(): String = nio.toString()

    public actual companion object {
        public actual val DIRECTORY_SEPARATOR: String = "/"
    }
}

public actual class FileUri public actual constructor(
    public actual val value: String,
) {
    internal val uri: URI =
        try {
            URI(value)
        } catch (exception: Exception) {
            throw IllegalArgumentException("Invalid file URI", exception)
        }.also { parsed ->
            require(parsed.scheme?.equals("file", ignoreCase = true) == true) { "File URI must use the file scheme" }
            require(parsed.userInfo == null && parsed.port == -1 && parsed.query == null && parsed.fragment == null) {
                "File URI must not contain user info, a port, query, or fragment"
            }
            require(parsed.authority.isNullOrEmpty() || parsed.authority.equals("localhost", ignoreCase = true)) {
                "File URI authority must be empty or localhost"
            }
            require(parsed.rawPath?.startsWith('/') == true) { "File URI path must be absolute" }
            validateEncodedUriPath(parsed.rawPath)
        }

    public actual override fun equals(other: Any?): Boolean = other is FileUri && value == other.value

    public actual override fun hashCode(): Int = value.hashCode()

    public actual override fun toString(): String = value
}

public actual val Path.name: String
    get() = fileName?.toString().orEmpty()
public actual val Path.extension: String
    get() = name.substringAfterLast('.', missingDelimiterValue = "")
public actual val Path.nameWithoutExtension: String
    get() = name.substringBeforeLast('.', missingDelimiterValue = name)
public actual val Path.pathString: String
    get() = toString()
public actual val Path.invariantSeparatorsPathString: String
    get() = toString()

@Throws(IllegalArgumentException::class)
public actual fun String.toPath(): Path = Path(this)

@Throws(IllegalArgumentException::class)
public actual fun pathOf(
    first: String,
    vararg more: String,
): Path {
    requireValidUnicode(first, "path part")
    more.forEach { requireValidUnicode(it, "path part") }
    return Path(Paths.get(first, *more))
}

@Throws(IllegalArgumentException::class)
public actual fun FileUri.toPath(): Path {
    val decoded = decodeFileUriPath(uri.rawPath)
    return if (uri.authority.isNullOrEmpty()) Path(Paths.get(uri)) else decoded.toPath()
}

@Throws(IllegalArgumentException::class)
public actual fun Path.relativeTo(base: Path): Path = base.relativize(this)

public actual fun Path.relativeToOrNull(base: Path): Path? =
    try {
        relativeTo(base)
    } catch (_: IllegalArgumentException) {
        null
    }

public actual fun Path.relativeToOrSelf(base: Path): Path = relativeToOrNull(base) ?: this

private fun isDotName(value: String): Boolean = value == "." || value == ".."

private fun Path.normalizeAndroidPath(): Path {
    if (pathString.isEmpty() || isRoot) return this
    val normalized = mutableListOf<String>()
    segments.forEach { segment ->
        when {
            segment == "." -> {
                Unit
            }

            segment == ".." && normalized.lastOrNull()?.let { it != ".." } == true -> {
                normalized.removeAt(normalized.lastIndex)
            }

            segment == ".." && isAbsolute -> {
                Unit
            }

            else -> {
                normalized += segment
            }
        }
    }
    val value =
        when {
            isAbsolute && normalized.isEmpty() -> "/"
            isAbsolute -> "/" + normalized.joinToString("/")
            else -> normalized.joinToString("/")
        }
    return value.toPath()
}
