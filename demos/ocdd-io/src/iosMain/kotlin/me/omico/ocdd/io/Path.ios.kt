package me.omico.ocdd.io

import platform.Foundation.NSURLComponents

public actual class Path :
    Comparable<Path>,
    Iterable<Path> {
    internal val value: String

    internal actual constructor(value: String) {
        requireValidUnicode(value, "path")
        this.value = canonicalPath(value)
    }

    internal constructor(value: String, canonical: Boolean) {
        this.value = if (canonical) value else canonicalPath(value)
    }

    public actual val root: Path?
        get() = if (isAbsolute) Path("/", canonical = true) else null
    public actual val fileName: Path?
        get() =
            when {
                isRoot -> null
                value.isEmpty() -> this
                else -> Path(segments.last(), canonical = true)
            }
    public actual val parent: Path?
        get() =
            when {
                isRoot || value.isEmpty() || (!isAbsolute && segments.size == 1) -> null
                segments.size == 1 -> root
                else -> fromParts(isAbsolute, segments.dropLast(1))
            }
    public actual val nameCount: Int
        get() = segments.size
    public actual val segments: List<String>
        get() = if (value.isEmpty()) listOf("") else value.split('/').filter(String::isNotEmpty)
    public actual val isAbsolute: Boolean
        get() = value.startsWith('/')
    public actual val isRelative: Boolean
        get() = !isAbsolute
    public actual val isRoot: Boolean
        get() = value == "/"

    public actual operator fun get(index: Int): Path {
        require(index in segments.indices) { "Path name index $index is outside 0..<$nameCount" }
        return Path(segments[index], canonical = true)
    }

    public actual fun subpath(
        beginIndex: Int,
        endIndex: Int,
    ): Path {
        require(beginIndex >= 0 && endIndex <= nameCount && beginIndex < endIndex) {
            "Invalid path name range [$beginIndex, $endIndex) for $nameCount names"
        }
        return fromParts(isAbsolute = false, segments.subList(beginIndex, endIndex))
    }

    @Throws(IllegalArgumentException::class)
    public actual operator fun div(other: String): Path = resolve(other)

    public actual operator fun div(other: Path): Path = resolve(other)

    @Throws(IllegalArgumentException::class)
    public actual fun resolve(other: String): Path = resolve(other.toPath())

    public actual fun resolve(other: Path): Path =
        when {
            other.isAbsolute -> other
            other.value.isEmpty() -> this
            value.isEmpty() -> other
            else -> fromParts(isAbsolute, segments + other.segments)
        }

    @Throws(IllegalArgumentException::class)
    public actual fun resolveSibling(other: String): Path = resolveSibling(other.toPath())

    public actual fun resolveSibling(other: Path): Path = parent?.resolve(other) ?: other

    @Throws(IllegalArgumentException::class)
    public actual fun relativize(other: Path): Path {
        require(isAbsolute == other.isAbsolute) { "Cannot relativize an absolute path against a relative path" }
        val containsDots = segments.any(::isDotName) || other.segments.any(::isDotName)
        val base = if (containsDots) normalize() else this
        val target = if (containsDots) other.normalize() else other
        if (base == target) return "".toPath()
        if (base.value.isEmpty()) return target
        val commonCount =
            base.segments
                .zip(target.segments)
                .takeWhile { (left, right) -> left == right }
                .size
        val remainingBase = base.segments.drop(commonCount)
        require(".." !in remainingBase) { "Base path cannot be relativized after an unmatched '..'" }
        return fromParts(false, List(remainingBase.size) { ".." } + target.segments.drop(commonCount))
    }

    @Throws(IllegalArgumentException::class)
    public actual fun startsWith(other: String): Boolean = startsWith(other.toPath())

    public actual fun startsWith(other: Path): Boolean =
        isAbsolute == other.isAbsolute && other.segments.size <= segments.size &&
            other.segments.indices.all { segments[it] == other.segments[it] }

    @Throws(IllegalArgumentException::class)
    public actual fun endsWith(other: String): Boolean = endsWith(other.toPath())

    public actual fun endsWith(other: Path): Boolean {
        if (other.isAbsolute) return this == other
        if (other.segments.size > segments.size) return false
        val offset = segments.size - other.segments.size
        return other.segments.indices.all { segments[offset + it] == other.segments[it] }
    }

    public actual fun normalize(): Path {
        if (value.isEmpty() || isRoot) return this
        val result = mutableListOf<String>()
        segments.forEach { segment ->
            when {
                segment == "." -> Unit
                segment == ".." && result.lastOrNull()?.let { it != ".." } == true -> result.removeAt(result.lastIndex)
                segment == ".." && isAbsolute -> Unit
                else -> result += segment
            }
        }
        return fromParts(isAbsolute, result)
    }

    public actual override fun iterator(): Iterator<Path> = segments.map(String::toPath).iterator()

    public actual override fun compareTo(other: Path): Int = compareUtf8PathStrings(value, other.value)

    public actual override fun equals(other: Any?): Boolean = other is Path && value == other.value

    public actual override fun hashCode(): Int = value.hashCode()

    public actual override fun toString(): String = value

    public actual companion object {
        public actual val DIRECTORY_SEPARATOR: String = "/"
    }
}

public actual class FileUri public actual constructor(
    public actual val value: String,
) {
    internal val encodedPath: String

    init {
        validatePercentEscapes(value)
        val components =
            NSURLComponents(string = value)
        require(components.scheme?.equals("file", ignoreCase = true) == true) { "File URI must use the file scheme" }
        require(components.user == null && components.password == null && components.port == null) {
            "File URI must not contain user info or a port"
        }
        require(components.host.isNullOrEmpty() || components.host.equals("localhost", ignoreCase = true)) {
            "File URI authority must be empty or localhost"
        }
        require(components.query == null && components.fragment == null) { "File URI must not contain a query or fragment" }
        encodedPath = requireNotNull(components.percentEncodedPath) { "File URI must contain a path" }
        validateEncodedUriPath(encodedPath)
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
    val parts = listOf(first) + more
    parts.forEach { requireValidUnicode(it, "path part") }
    val nonEmpty = parts.filter(String::isNotEmpty)
    return if (nonEmpty.isEmpty()) "".toPath() else nonEmpty.joinToString("/").toPath()
}

@Throws(IllegalArgumentException::class)
public actual fun FileUri.toPath(): Path = decodeFileUriPath(encodedPath).toPath()

@Throws(IllegalArgumentException::class)
public actual fun Path.relativeTo(base: Path): Path = base.relativize(this)

public actual fun Path.relativeToOrNull(base: Path): Path? =
    try {
        relativeTo(base)
    } catch (_: IllegalArgumentException) {
        null
    }

public actual fun Path.relativeToOrSelf(base: Path): Path = relativeToOrNull(base) ?: this

private fun canonicalPath(value: String): String {
    if (value.isEmpty()) return ""
    val absolute = value.startsWith('/')
    val names = value.split('/').filter(String::isNotEmpty)
    return when {
        absolute && names.isEmpty() -> "/"
        absolute -> "/" + names.joinToString("/")
        else -> names.joinToString("/")
    }
}

private fun fromParts(
    isAbsolute: Boolean,
    parts: List<String>,
): Path =
    when {
        parts.isEmpty() && isAbsolute -> Path("/", canonical = true)
        parts.isEmpty() -> Path("", canonical = true)
        isAbsolute -> Path("/" + parts.joinToString("/"), canonical = true)
        else -> Path(parts.joinToString("/"), canonical = true)
    }

private fun isDotName(value: String): Boolean = value == "." || value == ".."
