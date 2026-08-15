@file:OptIn(
    BetaInteropApi::class,
    ExperimentalForeignApi::class,
)

package me.omico.ocdd.io

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLContentModificationDateKey
import platform.Foundation.NSURLVolumeAvailableCapacityKey
import platform.Foundation.NSURLVolumeIsReadOnlyKey
import platform.Foundation.NSURLVolumeLocalizedFormatDescriptionKey
import platform.Foundation.NSURLVolumeNameKey
import platform.Foundation.NSURLVolumeTotalCapacityKey
import platform.posix.chmod
import platform.posix.chown
import platform.posix.errno
import platform.posix.getpwnam
import platform.posix.getpwuid

public actual class FileTime public actual constructor(
    public actual val epochSeconds: Long,
    public actual val nanosecondsOfSecond: Int,
) : Comparable<FileTime> {
    init {
        require(nanosecondsOfSecond in 0..999_999_999) { "Nanoseconds must be in 0..999999999" }
    }

    public actual override fun compareTo(other: FileTime): Int =
        epochSeconds.compareTo(other.epochSeconds).takeIf { it != 0 }
            ?: nanosecondsOfSecond.compareTo(other.nanosecondsOfSecond)

    public actual override fun equals(other: Any?): Boolean =
        other is FileTime && epochSeconds == other.epochSeconds && nanosecondsOfSecond == other.nanosecondsOfSecond

    public actual override fun hashCode(): Int = 31 * epochSeconds.hashCode() + nanosecondsOfSecond

    public actual override fun toString(): String = fileTimeText(epochSeconds, nanosecondsOfSecond)
}

public actual class FileAttribute public actual constructor(
    public actual val name: String,
    value: Any?,
) {
    public actual val value: Any? = validateCreationAttribute(name, value)
}

public actual class FileAttributes internal actual constructor() {
    private var data: FileAttributesData =
        FileAttributesData(
            FileType.OTHER,
            0L,
            null,
            FileTime(0L),
            null,
            null,
            null,
        )

    internal constructor(data: FileAttributesData) : this() {
        this.data = data
    }

    public actual val type: FileType get() = data.type
    public actual val size: Long get() = data.size
    public actual val creationTime: FileTime? get() = data.creationTime
    public actual val lastModifiedTime: FileTime get() = data.lastModifiedTime
    public actual val lastAccessTime: FileTime? get() = data.lastAccessTime
    public actual val owner: String? get() = data.owner
    public actual val permissions: Set<PosixFilePermission>? get() = data.permissions?.toSet()
}

public actual class FileStore internal actual constructor() {
    private var data: FileStoreData = FileStoreData("", "", false, null, null, null)

    internal constructor(data: FileStoreData) : this() {
        this.data = data
    }

    public actual val name: String get() = data.name
    public actual val type: String get() = data.type
    public actual val isReadOnly: Boolean get() = data.isReadOnly
    public actual val totalSpace: Long? get() = data.totalSpace
    public actual val usableSpace: Long? get() = data.usableSpace
    public actual val unallocatedSpace: Long? get() = data.unallocatedSpace
}

private class PosixFileAttributeView(
    private val path: Path,
    private val view: AttributeViewName,
    private val options: Array<out LinkOption>,
) : FileAttributeView {
    override fun read(): FileAttributes = path.readAttributes(*options)

    override fun get(name: String): Any? = path.getAttribute("${view.text}:$name", *options)

    override fun set(
        name: String,
        value: Any?,
    ) {
        path.setAttribute("${view.text}:$name", value, *options)
    }
}

@Throws(IOException::class)
public actual fun Path.fileAttributesView(
    view: String,
    vararg options: LinkOption,
): FileAttributeView = PosixFileAttributeView(this, parseView(view), options)

public actual fun Path.fileAttributesViewOrNull(
    view: String,
    vararg options: LinkOption,
): FileAttributeView? = PosixFileAttributeView(this, parseView(view), options)

@Throws(IOException::class)
public actual fun Path.readAttributes(vararg options: LinkOption): FileAttributes {
    val status = readIosFileMetadata(LinkOption.NOFOLLOW_LINKS !in options, FileSystemOperation.READ_ATTRIBUTES)
    val owner = getpwuid(status.userId)?.pointed?.pw_name?.toKString()
    return FileAttributes(
        FileAttributesData(
            type = status.type,
            size = if (status.type == FileType.REGULAR_FILE) status.size else 0L,
            creationTime =
                status.creationSeconds?.let { seconds ->
                    FileTime(seconds, status.creationNanoseconds ?: 0)
                },
            lastModifiedTime = FileTime(status.modifiedSeconds, status.modifiedNanoseconds),
            lastAccessTime =
                status.accessedSeconds?.let { seconds ->
                    FileTime(seconds, status.accessedNanoseconds ?: 0)
                },
            owner = owner,
            permissions = status.permissions(),
        ),
    )
}

@Throws(IOException::class)
public actual fun Path.readAttributes(
    attributes: String,
    vararg options: LinkOption,
): Map<String, Any?> {
    val names = parseAttributeExpression(attributes)
    val snapshot = readAttributes(*options)
    return names.associate { it.qualified to snapshot.attributeValue(it) }
}

@Throws(IOException::class)
public actual fun Path.getAttribute(
    attribute: String,
    vararg options: LinkOption,
): Any? = readAttributes(*options).attributeValue(parseSingleAttribute(attribute))

@Throws(IOException::class)
public actual fun Path.setAttribute(
    attribute: String,
    value: Any?,
    vararg options: LinkOption,
): Path {
    val name = parseSingleAttribute(attribute)
    val validated = validateAttributeWrite(name, value)
    if (LinkOption.NOFOLLOW_LINKS in options && isSymbolicLink()) {
        throw UnsupportedOperationException("Writing attributes without following a symbolic link is unavailable on iOS")
    }
    return when (name.qualified) {
        "basic:lastModifiedTime" -> {
            setLastModifiedTime(validated as FileTime)
        }

        "owner:owner" -> {
            setOwner(validated as String)
        }

        "posix:permissions" -> {
            @Suppress("UNCHECKED_CAST")
            setPosixFilePermissions(validated as Set<PosixFilePermission>)
        }

        else -> {
            error("Attribute write was already validated")
        }
    }
}

@Throws(IOException::class)
public actual fun Path.getLastModifiedTime(vararg options: LinkOption): FileTime = readAttributes(*options).lastModifiedTime

@Throws(IOException::class)
public actual fun Path.setLastModifiedTime(value: FileTime): Path =
    memScoped {
        readIosFileMetadata(followLinks = true, operation = FileSystemOperation.WRITE_ATTRIBUTES)
        val epochSeconds = value.epochSeconds + (value.nanosecondsOfSecond / 1_000_000) / 1_000.0
        val date = NSDate(timeIntervalSinceReferenceDate = epochSeconds - 978_307_200.0)
        val error = alloc<ObjCObjectVar<NSError?>>()
        error.value = null
        val changed =
            NSURL.fileURLWithPath(absolute().toString()).setResourceValue(
                date,
                forKey = NSURLContentModificationDateKey,
                error = error.ptr,
            )
        if (!changed) {
            throw error.value.toFoundationFileSystemException(
                FileSystemOperation.WRITE_ATTRIBUTES,
                this@setLastModifiedTime,
                otherPath = null,
                partialResult = true,
            )
        }
        this@setLastModifiedTime
    }

@Throws(IOException::class)
public actual fun Path.getOwner(vararg options: LinkOption): String? =
    readAttributes(*options).owner ?: throw UnsupportedOperationException("Owner lookup is unavailable")

@Throws(IOException::class)
public actual fun Path.setOwner(value: String): Path {
    require(value.isNotEmpty()) { "Owner must not be empty" }
    val user = getpwnam(value) ?: throw IllegalArgumentException("Unknown owner: $value")
    if (chown(toString(), user.pointed.pw_uid, UInt.MAX_VALUE) != 0) {
        throw posixFileSystemException(errno, FileSystemOperation.WRITE_ATTRIBUTES, this, partialResult = true)
    }
    return this
}

@Throws(IOException::class)
public actual fun Path.getPosixFilePermissions(vararg options: LinkOption): Set<PosixFilePermission> =
    readAttributes(*options).permissions ?: throw UnsupportedOperationException("POSIX permissions are unavailable")

@Throws(IOException::class)
public actual fun Path.setPosixFilePermissions(value: Set<PosixFilePermission>): Path {
    val permissions = immutablePermissions(value)
    if (chmod(toString(), permissions.toMode().convert()) != 0) {
        throw posixFileSystemException(errno, FileSystemOperation.WRITE_ATTRIBUTES, this, partialResult = true)
    }
    return this
}

@Throws(IOException::class)
public actual fun Path.fileStore(): FileStore =
    iosOperation(FileSystemOperation.FILE_STORE, this) {
        readIosFileMetadata(followLinks = true, operation = FileSystemOperation.FILE_STORE)
        val values =
            NSURL.fileURLWithPath(absolute().toString()).resourceValuesForKeys(
                listOf(
                    NSURLVolumeNameKey,
                    NSURLVolumeLocalizedFormatDescriptionKey,
                    NSURLVolumeIsReadOnlyKey,
                    NSURLVolumeTotalCapacityKey,
                    NSURLVolumeAvailableCapacityKey,
                ),
                error = null,
            ) ?: throw IOException("Volume information is unavailable")
        val total = (values[NSURLVolumeTotalCapacityKey] as? NSNumber)?.longLongValue?.takeIf { it >= 0 }
        val available = (values[NSURLVolumeAvailableCapacityKey] as? NSNumber)?.longLongValue?.takeIf { it >= 0 }
        FileStore(
            FileStoreData(
                name = values[NSURLVolumeNameKey] as? String ?: absolute().root?.toString().orEmpty(),
                type = values[NSURLVolumeLocalizedFormatDescriptionKey] as? String ?: "unknown",
                isReadOnly = (values[NSURLVolumeIsReadOnlyKey] as? NSNumber)?.boolValue ?: false,
                totalSpace = total,
                usableSpace = available?.coerceAtMost(total ?: Long.MAX_VALUE),
                unallocatedSpace = available?.coerceAtMost(total ?: Long.MAX_VALUE),
            ),
        )
    }
