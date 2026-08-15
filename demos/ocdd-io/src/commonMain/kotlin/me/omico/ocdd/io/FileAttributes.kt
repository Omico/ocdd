package me.omico.ocdd.io

public enum class FileType {
    REGULAR_FILE,
    DIRECTORY,
    SYMBOLIC_LINK,
    OTHER,
}

public enum class PosixFilePermission {
    OWNER_READ,
    OWNER_WRITE,
    OWNER_EXECUTE,
    GROUP_READ,
    GROUP_WRITE,
    GROUP_EXECUTE,
    OTHERS_READ,
    OTHERS_WRITE,
    OTHERS_EXECUTE,
}

public expect class FileTime(
    epochSeconds: Long,
    nanosecondsOfSecond: Int = 0,
) : Comparable<FileTime> {
    public val epochSeconds: Long
    public val nanosecondsOfSecond: Int

    public override fun compareTo(other: FileTime): Int

    public override fun equals(other: Any?): Boolean

    public override fun hashCode(): Int

    public override fun toString(): String
}

public expect class FileAttribute(
    name: String,
    value: Any?,
) {
    public val name: String
    public val value: Any?
}

public expect class FileAttributes internal constructor() {
    public val type: FileType
    public val size: Long
    public val creationTime: FileTime?
    public val lastModifiedTime: FileTime
    public val lastAccessTime: FileTime?
    public val owner: String?
    public val permissions: Set<PosixFilePermission>?
}

public interface FileAttributeView {
    @Throws(IOException::class)
    public fun read(): FileAttributes

    @Throws(IOException::class)
    public operator fun get(name: String): Any?

    @Throws(IOException::class)
    public operator fun set(
        name: String,
        value: Any?,
    )
}

public expect class FileStore internal constructor() {
    public val name: String
    public val type: String
    public val isReadOnly: Boolean
    public val totalSpace: Long?
    public val usableSpace: Long?
    public val unallocatedSpace: Long?
}

@Throws(IOException::class)
public expect fun Path.fileAttributesView(
    view: String = "basic",
    vararg options: LinkOption,
): FileAttributeView

public expect fun Path.fileAttributesViewOrNull(
    view: String = "basic",
    vararg options: LinkOption,
): FileAttributeView?

@Throws(IOException::class)
public expect fun Path.readAttributes(vararg options: LinkOption): FileAttributes

@Throws(IOException::class)
public expect fun Path.readAttributes(
    attributes: String,
    vararg options: LinkOption,
): Map<String, Any?>

@Throws(IOException::class)
public expect fun Path.getAttribute(
    attribute: String,
    vararg options: LinkOption,
): Any?

@Throws(IOException::class)
public expect fun Path.setAttribute(
    attribute: String,
    value: Any?,
    vararg options: LinkOption,
): Path

@Throws(IOException::class)
public expect fun Path.getLastModifiedTime(vararg options: LinkOption): FileTime

@Throws(IOException::class)
public expect fun Path.setLastModifiedTime(value: FileTime): Path

@Throws(IOException::class)
public expect fun Path.getOwner(vararg options: LinkOption): String?

@Throws(IOException::class)
public expect fun Path.setOwner(value: String): Path

@Throws(IOException::class)
public expect fun Path.getPosixFilePermissions(vararg options: LinkOption): Set<PosixFilePermission>

@Throws(IOException::class)
public expect fun Path.setPosixFilePermissions(value: Set<PosixFilePermission>): Path

@Throws(IOException::class)
public expect fun Path.fileStore(): FileStore

internal data class FileAttributesData(
    val type: FileType,
    val size: Long,
    val creationTime: FileTime?,
    val lastModifiedTime: FileTime,
    val lastAccessTime: FileTime?,
    val owner: String?,
    val permissions: Set<PosixFilePermission>?,
)

internal data class FileStoreData(
    val name: String,
    val type: String,
    val isReadOnly: Boolean,
    val totalSpace: Long?,
    val usableSpace: Long?,
    val unallocatedSpace: Long?,
)

internal enum class AttributeViewName(
    val text: String,
) {
    BASIC("basic"),
    OWNER("owner"),
    POSIX("posix"),
}

internal data class AttributeName(
    val view: AttributeViewName,
    val name: String,
) {
    val qualified: String
        get() = "${view.text}:$name"
}

internal fun parseView(value: String): AttributeViewName =
    AttributeViewName.entries.firstOrNull { it.text == value }
        ?: throw IllegalArgumentException("Unknown attribute view: $value")

internal fun parseSingleAttribute(
    value: String,
    defaultView: AttributeViewName = AttributeViewName.BASIC,
): AttributeName {
    require(value.isNotEmpty() && ',' !in value && value != "*") { "Expected one attribute name" }
    val separator = value.indexOf(':')
    val view = if (separator < 0) defaultView else parseView(value.substring(0, separator))
    val name = if (separator < 0) value else value.substring(separator + 1)
    require(name.isNotEmpty() && ':' !in name && name != "*") { "Invalid attribute name: $value" }
    require(name in supportedAttributeNames(view)) { "Unknown attribute: ${view.text}:$name" }
    return AttributeName(view, name)
}

internal fun parseAttributeExpression(value: String): List<AttributeName> {
    require(value.isNotEmpty()) { "Attribute expression must not be empty" }
    val separator = value.indexOf(':')
    val view = if (separator < 0) AttributeViewName.BASIC else parseView(value.substring(0, separator))
    val namesText = if (separator < 0) value else value.substring(separator + 1)
    require(namesText.isNotEmpty()) { "Attribute expression must contain names" }
    val names = namesText.split(',')
    require(names.none(String::isEmpty)) { "Attribute expression contains an empty name" }
    val expanded = if (names.size == 1 && names[0] == "*") supportedAttributeNames(view) else names
    require("*" !in expanded) { "Wildcard must be the only attribute name" }
    expanded.forEach { require(it in supportedAttributeNames(view)) { "Unknown attribute: ${view.text}:$it" } }
    return expanded.distinct().map { AttributeName(view, it) }
}

internal fun supportedAttributeNames(view: AttributeViewName): List<String> =
    when (view) {
        AttributeViewName.BASIC -> {
            listOf(
                "size",
                "creationTime",
                "lastModifiedTime",
                "lastAccessTime",
                "isRegularFile",
                "isDirectory",
                "isSymbolicLink",
                "isOther",
            )
        }

        AttributeViewName.OWNER -> {
            listOf("owner")
        }

        AttributeViewName.POSIX -> {
            listOf("permissions")
        }
    }

internal fun validateCreationAttribute(
    name: String,
    value: Any?,
): Any {
    require(name == "posix:permissions") { "Attribute is not available during creation: $name" }
    return immutablePermissions(value)
}

internal fun immutablePermissions(value: Any?): Set<PosixFilePermission> {
    require(value is Set<*>) { "POSIX permissions must be a set" }
    require(value.all { it is PosixFilePermission }) { "POSIX permissions contain an invalid value" }
    @Suppress("UNCHECKED_CAST")
    return (value as Set<PosixFilePermission>).toSet()
}

internal fun validateAttributeWrite(
    name: AttributeName,
    value: Any?,
): Any =
    when (name.qualified) {
        "basic:lastModifiedTime" -> {
            value as? FileTime ?: throw IllegalArgumentException("Expected FileTime")
        }

        "owner:owner" -> {
            (value as? String)?.also { require(it.isNotEmpty()) { "Owner must not be empty" } }
                ?: throw IllegalArgumentException("Expected owner name")
        }

        "posix:permissions" -> {
            immutablePermissions(value)
        }

        else -> {
            throw UnsupportedOperationException("Attribute is read-only: ${name.qualified}")
        }
    }

internal fun fileTimeText(
    seconds: Long,
    nanoseconds: Int,
): String = "$seconds:${nanoseconds.toString().padStart(9, '0')}"

internal fun FileAttributes.attributeValue(name: AttributeName): Any? =
    when (name.qualified) {
        "basic:size" -> size
        "basic:creationTime" -> creationTime
        "basic:lastModifiedTime" -> lastModifiedTime
        "basic:lastAccessTime" -> lastAccessTime
        "basic:isRegularFile" -> type == FileType.REGULAR_FILE
        "basic:isDirectory" -> type == FileType.DIRECTORY
        "basic:isSymbolicLink" -> type == FileType.SYMBOLIC_LINK
        "basic:isOther" -> type == FileType.OTHER
        "owner:owner" -> owner
        "posix:permissions" -> permissions
        else -> error("Unreachable attribute ${name.qualified}")
    }
