package me.omico.ocdd.io

import android.os.Build
import android.system.Os
import android.system.OsConstants
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileOwnerAttributeView
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFileAttributes
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.TimeUnit

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
            type = FileType.OTHER,
            size = 0L,
            creationTime = null,
            lastModifiedTime = FileTime(0L),
            lastAccessTime = null,
            owner = null,
            permissions = null,
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

private class NioFileAttributeView(
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
): FileAttributeView {
    val parsed = parseView(view)
    if (!supportsView(parsed, options)) throw UnsupportedOperationException("Attribute view is unavailable: $view")
    return NioFileAttributeView(this, parsed, options)
}

public actual fun Path.fileAttributesViewOrNull(
    view: String,
    vararg options: LinkOption,
): FileAttributeView? {
    val parsed = parseView(view)
    return if (supportsView(parsed, options)) NioFileAttributeView(this, parsed, options) else null
}

@Throws(IOException::class)
public actual fun Path.readAttributes(vararg options: LinkOption): FileAttributes =
    nioOperation(FileSystemOperation.READ_ATTRIBUTES, this) {
        if (usesAndroidPosixFallback) return@nioOperation readAndroidAttributes(options)
        val nio = toNioPath()
        val linkOptions = options.toNioLinkOptions()
        val posix =
            if (Files.getFileAttributeView(nio, PosixFileAttributeView::class.java, *linkOptions) != null) {
                Files.readAttributes(nio, PosixFileAttributes::class.java, *linkOptions)
            } else {
                null
            }
        val basic: BasicFileAttributes =
            posix ?: Files.readAttributes(nio, BasicFileAttributes::class.java, *linkOptions)
        val owner =
            posix?.owner()?.name ?: Files
                .getFileAttributeView(
                    nio,
                    FileOwnerAttributeView::class.java,
                    *linkOptions,
                )?.let { view -> runCatching { view.owner.name }.getOrNull() }
        val permissions = posix?.permissions()?.map(::fromNioPermission)?.toSet()
        FileAttributes(
            FileAttributesData(
                type =
                    when {
                        basic.isRegularFile -> FileType.REGULAR_FILE
                        basic.isDirectory -> FileType.DIRECTORY
                        basic.isSymbolicLink -> FileType.SYMBOLIC_LINK
                        else -> FileType.OTHER
                    },
                size = if (basic.isRegularFile) basic.size() else 0L,
                creationTime = basic.creationTime().toOcddFileTime(),
                lastModifiedTime = basic.lastModifiedTime().toOcddFileTime(),
                lastAccessTime = basic.lastAccessTime().toOcddFileTime(),
                owner = owner,
                permissions = permissions,
            ),
        )
    }

@Throws(IOException::class)
public actual fun Path.readAttributes(
    attributes: String,
    vararg options: LinkOption,
): Map<String, Any?> {
    val names = parseAttributeExpression(attributes)
    names.forEach { requireSupportedView(it.view, options) }
    val snapshot = readAttributes(*options)
    return names.associate { it.qualified to snapshot.attributeValue(it) }
}

@Throws(IOException::class)
public actual fun Path.getAttribute(
    attribute: String,
    vararg options: LinkOption,
): Any? {
    val name = parseSingleAttribute(attribute)
    requireSupportedView(name.view, options)
    return readAttributes(*options).attributeValue(name)
}

@Throws(IOException::class)
public actual fun Path.setAttribute(
    attribute: String,
    value: Any?,
    vararg options: LinkOption,
): Path {
    val name = parseSingleAttribute(attribute)
    requireSupportedView(name.view, options)
    val validated = validateAttributeWrite(name, value)
    return when (name.qualified) {
        "basic:lastModifiedTime" -> {
            setLastModifiedTime(validated as FileTime, options)
        }

        "owner:owner" -> {
            setOwner(validated as String, options)
        }

        "posix:permissions" -> {
            @Suppress("UNCHECKED_CAST")
            setPosixFilePermissions(validated as Set<PosixFilePermission>, options)
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
    nioOperation(
        FileSystemOperation.WRITE_ATTRIBUTES,
        this,
        partialResult = true,
    ) {
        val milliseconds = value.epochSeconds * 1_000L + value.nanosecondsOfSecond / 1_000_000L
        if (usesAndroidPosixFallback) {
            setAndroidLastModifiedTime(value)
        } else {
            Files.setLastModifiedTime(
                toNioPath(),
                java.nio.file.attribute.FileTime
                    .fromMillis(milliseconds),
            )
        }
        this
    }

@Throws(IOException::class)
public actual fun Path.getOwner(vararg options: LinkOption): String? {
    requireSupportedView(AttributeViewName.OWNER, options)
    return readAttributes(*options).owner
}

@Throws(IOException::class)
public actual fun Path.setOwner(value: String): Path =
    nioOperation(
        FileSystemOperation.WRITE_ATTRIBUTES,
        this,
        partialResult = true,
    ) {
        require(value.isNotEmpty()) { "Owner must not be empty" }
        if (usesAndroidPosixFallback) {
            throw UnsupportedOperationException("Owner lookup is unavailable on Android API 21-25")
        } else {
            val service = toNioPath().fileSystem.userPrincipalLookupService
            Files.setOwner(toNioPath(), service.lookupPrincipalByName(value))
        }
        this
    }

@Throws(IOException::class)
public actual fun Path.getPosixFilePermissions(vararg options: LinkOption): Set<PosixFilePermission> {
    requireSupportedView(AttributeViewName.POSIX, options)
    return readAttributes(*options).permissions
        ?: throw UnsupportedOperationException("POSIX permissions are unavailable")
}

@Throws(IOException::class)
public actual fun Path.setPosixFilePermissions(value: Set<PosixFilePermission>): Path =
    immutablePermissions(value).let { permissions ->
        nioOperation(FileSystemOperation.WRITE_ATTRIBUTES, this, partialResult = true) {
            if (usesAndroidPosixFallback) {
                Os.chmod(androidPathString(), permissions.toAndroidMode())
            } else {
                Files.setPosixFilePermissions(toNioPath(), permissions.map(::toNioPermission).toSet())
            }
            this
        }
    }

@Throws(IOException::class)
public actual fun Path.fileStore(): FileStore =
    nioOperation(FileSystemOperation.FILE_STORE, this) {
        if (Build.VERSION.SDK_INT != 0) return@nioOperation androidFileStore()
        val store = Files.getFileStore(toNioPath())
        FileStore(
            FileStoreData(
                store.name(),
                store.type(),
                store.isReadOnly,
                store.totalSpace,
                store.usableSpace,
                store.unallocatedSpace,
            ),
        )
    }

private fun Path.androidFileStore(): FileStore {
    androidStat(noFollowLinks = false)
    val status = Os.statvfs(androidPathString())
    val mount = androidMountInfo()
    val blockSize = status.f_frsize.takeIf { it > 0L } ?: status.f_bsize
    return FileStore(
        FileStoreData(
            name = mount?.source?.takeIf(String::isNotEmpty) ?: status.f_fsid.toULong().toString(16),
            type = mount?.type?.takeIf(String::isNotEmpty) ?: "unknown",
            isReadOnly = mount?.readOnly ?: false,
            totalSpace = multipliedSpace(status.f_blocks, blockSize),
            usableSpace = multipliedSpace(status.f_bavail, blockSize),
            unallocatedSpace = multipliedSpace(status.f_bfree, blockSize),
        ),
    )
}

private data class AndroidMountInfo(
    val mountPoint: String,
    val type: String,
    val source: String,
    val readOnly: Boolean,
)

private fun Path.androidMountInfo(): AndroidMountInfo? {
    val location = absolute().normalize().toString()
    return runCatching {
        java.io.File("/proc/self/mountinfo").useLines { lines ->
            lines
                .mapNotNull(String::toAndroidMountInfo)
                .filter { mount ->
                    mount.mountPoint == "/" ||
                        location == mount.mountPoint ||
                        location.startsWith(mount.mountPoint.trimEnd('/') + "/")
                }.maxByOrNull { it.mountPoint.length }
        }
    }.getOrNull()
}

private fun String.toAndroidMountInfo(): AndroidMountInfo? {
    val halves = split(" - ", limit = 2)
    if (halves.size != 2) return null
    val mountFields = halves[0].split(' ')
    val fileSystemFields = halves[1].split(' ')
    if (mountFields.size < 6 || fileSystemFields.size < 2) return null
    return AndroidMountInfo(
        mountPoint = mountFields[4].decodeMountInfoField(),
        type = fileSystemFields[0].decodeMountInfoField(),
        source = fileSystemFields[1].decodeMountInfoField(),
        readOnly = "ro" in mountFields[5].split(','),
    )
}

private fun String.decodeMountInfoField(): String =
    replace("\\040", " ")
        .replace("\\011", "\t")
        .replace("\\012", "\n")
        .replace("\\134", "\\")

private fun multipliedSpace(
    blocks: Long,
    blockSize: Long,
): Long? = if (blocks < 0L || blockSize <= 0L || blocks > Long.MAX_VALUE / blockSize) null else blocks * blockSize

private fun Path.supportsView(
    view: AttributeViewName,
    options: Array<out LinkOption>,
): Boolean {
    if (usesAndroidPosixFallback) return view != AttributeViewName.OWNER
    val nio = toNioPath()
    val linkOptions = options.toNioLinkOptions()
    return when (view) {
        AttributeViewName.BASIC -> {
            true
        }

        AttributeViewName.OWNER -> {
            Files.getFileAttributeView(
                nio,
                FileOwnerAttributeView::class.java,
                *linkOptions,
            ) != null
        }

        AttributeViewName.POSIX -> {
            Files.getFileAttributeView(
                nio,
                PosixFileAttributeView::class.java,
                *linkOptions,
            ) != null
        }
    }
}

private fun Path.requireSupportedView(
    view: AttributeViewName,
    options: Array<out LinkOption>,
) {
    if (!supportsView(view, options)) throw UnsupportedOperationException("Attribute view is unavailable: ${view.text}")
}

private fun java.nio.file.attribute.FileTime.toOcddFileTime(): FileTime {
    val seconds = to(TimeUnit.SECONDS)
    val nanoseconds = ((toMillis() - seconds * 1_000L) * 1_000_000L).toInt()
    return if (nanoseconds >= 0) FileTime(seconds, nanoseconds) else FileTime(seconds - 1, nanoseconds + 1_000_000_000)
}

private fun fromNioPermission(value: java.nio.file.attribute.PosixFilePermission): PosixFilePermission =
    PosixFilePermission.valueOf(value.name)

private fun toNioPermission(value: PosixFilePermission): java.nio.file.attribute.PosixFilePermission =
    java.nio.file.attribute.PosixFilePermission
        .valueOf(value.name)

private fun Path.setLastModifiedTime(
    value: FileTime,
    options: Array<out LinkOption>,
): Path =
    nioOperation(FileSystemOperation.WRITE_ATTRIBUTES, this, partialResult = true) {
        val milliseconds = value.epochSeconds * 1_000L + value.nanosecondsOfSecond / 1_000_000L
        if (usesAndroidPosixFallback) {
            rejectUnsupportedNoFollowWrite(options)
            setAndroidLastModifiedTime(value)
        } else {
            Files.setAttribute(
                toNioPath(),
                "basic:lastModifiedTime",
                java.nio.file.attribute.FileTime
                    .fromMillis(milliseconds),
                *options.toNioLinkOptions(),
            )
        }
        this
    }

private fun Path.setOwner(
    value: String,
    options: Array<out LinkOption>,
): Path =
    nioOperation(FileSystemOperation.WRITE_ATTRIBUTES, this, partialResult = true) {
        require(value.isNotEmpty()) { "Owner must not be empty" }
        if (usesAndroidPosixFallback) {
            throw UnsupportedOperationException("Owner lookup is unavailable on Android API 21-25")
        } else {
            val service = toNioPath().fileSystem.userPrincipalLookupService
            val view =
                Files.getFileAttributeView(
                    toNioPath(),
                    FileOwnerAttributeView::class.java,
                    *options.toNioLinkOptions(),
                ) ?: throw UnsupportedOperationException("Owner view is unavailable")
            view.owner = service.lookupPrincipalByName(value)
        }
        this
    }

private fun Path.setPosixFilePermissions(
    value: Set<PosixFilePermission>,
    options: Array<out LinkOption>,
): Path =
    nioOperation(FileSystemOperation.WRITE_ATTRIBUTES, this, partialResult = true) {
        if (usesAndroidPosixFallback) {
            rejectUnsupportedNoFollowWrite(options)
            Os.chmod(androidPathString(), value.toAndroidMode())
        } else {
            val view =
                Files.getFileAttributeView(
                    toNioPath(),
                    PosixFileAttributeView::class.java,
                    *options.toNioLinkOptions(),
                ) ?: throw UnsupportedOperationException("POSIX view is unavailable")
            view.setPermissions(value.map(::toNioPermission).toSet())
        }
        this
    }

private fun Path.readAndroidAttributes(options: Array<out LinkOption>): FileAttributes {
    val status = androidAttributes(LinkOption.NOFOLLOW_LINKS in options)
    val regularFile = OsConstants.S_ISREG(status.mode)
    return FileAttributes(
        FileAttributesData(
            type =
                when {
                    regularFile -> FileType.REGULAR_FILE
                    OsConstants.S_ISDIR(status.mode) -> FileType.DIRECTORY
                    OsConstants.S_ISLNK(status.mode) -> FileType.SYMBOLIC_LINK
                    else -> FileType.OTHER
                },
            size = if (regularFile) status.size else 0L,
            creationTime = null,
            lastModifiedTime = status.lastModifiedTime,
            lastAccessTime = status.lastAccessTime,
            owner = null,
            permissions = status.mode.toOcddPermissions(),
        ),
    )
}

private fun Path.rejectUnsupportedNoFollowWrite(options: Array<out LinkOption>) {
    if (LinkOption.NOFOLLOW_LINKS in options && isSymbolicLink()) {
        throw UnsupportedOperationException("Writing this symbolic-link attribute is unavailable on Android API 21-25")
    }
}

internal fun Array<out FileAttribute>.toNioFileAttributes(): Array<java.nio.file.attribute.FileAttribute<*>> =
    map { attribute ->
        when (attribute.name) {
            "posix:permissions" -> {
                PosixFilePermissions.asFileAttribute(
                    immutablePermissions(attribute.value).map(::toNioPermission).toSet(),
                )
            }

            else -> {
                error("Creation attributes were already validated")
            }
        }
    }.toTypedArray()
