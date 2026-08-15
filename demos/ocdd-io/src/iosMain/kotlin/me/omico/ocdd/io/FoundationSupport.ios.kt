@file:OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)

package me.omico.ocdd.io

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.NSFileCreationDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileNoSuchFileError
import platform.Foundation.NSFileOwnerAccountID
import platform.Foundation.NSFilePosixPermissions
import platform.Foundation.NSFileReadNoPermissionError
import platform.Foundation.NSFileReadNoSuchFileError
import platform.Foundation.NSFileSize
import platform.Foundation.NSFileSystemFileNumber
import platform.Foundation.NSFileSystemNumber
import platform.Foundation.NSFileType
import platform.Foundation.NSFileTypeDirectory
import platform.Foundation.NSFileTypeRegular
import platform.Foundation.NSFileTypeSymbolicLink
import platform.Foundation.NSFileWriteNoPermissionError
import platform.Foundation.NSNumber
import platform.posix.F_OK
import platform.posix.access
import platform.posix.errno
import kotlin.math.floor
import kotlin.math.roundToInt

internal data class IosFileMetadata(
    val type: FileType,
    val mode: Int,
    val size: Long,
    val device: Long,
    val inode: ULong,
    val userId: UInt,
    val creationSeconds: Long?,
    val creationNanoseconds: Int?,
    val modifiedSeconds: Long,
    val modifiedNanoseconds: Int,
    val accessedSeconds: Long?,
    val accessedNanoseconds: Int?,
)

internal fun Path.readIosFileMetadata(
    followLinks: Boolean,
    operation: FileSystemOperation,
    otherPath: Path? = null,
): IosFileMetadata {
    var attributes = readFoundationAttributes(operation, otherPath)
    if (followLinks && attributes[NSFileType] == NSFileTypeSymbolicLink) {
        if (access(toString(), F_OK) != 0) {
            throw posixFileSystemException(errno, operation, this, otherPath)
        }
        val resolved =
            iosOperation(operation, this, otherPath) {
                systemFileSystem.canonicalize(toOkioPath()).toString().toPath()
            }
        attributes = resolved.readFoundationAttributes(operation, otherPath)
    }
    val creation = (attributes[NSFileCreationDate] as? NSDate).toEpochParts()
    val modified =
        requireNotNull((attributes[NSFileModificationDate] as? NSDate).toEpochParts()) {
            "Last-modified time is unavailable"
        }
    return IosFileMetadata(
        type =
            when (attributes[NSFileType]) {
                NSFileTypeRegular -> FileType.REGULAR_FILE
                NSFileTypeDirectory -> FileType.DIRECTORY
                NSFileTypeSymbolicLink -> FileType.SYMBOLIC_LINK
                else -> FileType.OTHER
            },
        mode = (attributes[NSFilePosixPermissions] as? NSNumber)?.intValue ?: 0,
        size = (attributes[NSFileSize] as? NSNumber)?.longLongValue ?: 0L,
        device = (attributes[NSFileSystemNumber] as? NSNumber)?.longLongValue ?: 0L,
        inode = (attributes[NSFileSystemFileNumber] as? NSNumber)?.unsignedLongLongValue ?: 0uL,
        userId = (attributes[NSFileOwnerAccountID] as? NSNumber)?.unsignedIntValue ?: 0u,
        creationSeconds = creation?.first,
        creationNanoseconds = creation?.second,
        modifiedSeconds = modified.first,
        modifiedNanoseconds = modified.second,
        accessedSeconds = null,
        accessedNanoseconds = null,
    )
}

private fun Path.readFoundationAttributes(
    operation: FileSystemOperation,
    otherPath: Path?,
): Map<Any?, *> =
    memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        error.value = null
        NSFileManager.defaultManager.attributesOfItemAtPath(this@readFoundationAttributes.toString(), error = error.ptr)
            ?: throw error.value.toFoundationFileSystemException(operation, this@readFoundationAttributes, otherPath)
    }

internal fun NSError?.toFoundationFileSystemException(
    operation: FileSystemOperation,
    path: Path,
    otherPath: Path?,
    partialResult: Boolean = false,
): FileSystemException =
    FileSystemException(
        message = this?.localizedDescription ?: "$operation failed for $path",
        operation = operation,
        path = path,
        otherPath = otherPath,
        reason =
            when (this?.code) {
                NSFileNoSuchFileError, NSFileReadNoSuchFileError -> FileSystemErrorReason.NOT_FOUND
                NSFileReadNoPermissionError, NSFileWriteNoPermissionError -> FileSystemErrorReason.ACCESS_DENIED
                else -> FileSystemErrorReason.IO_FAILURE
            },
        partialResult = partialResult,
    )

private fun NSDate?.toEpochParts(): Pair<Long, Int>? =
    this?.let { date ->
        (date.timeIntervalSinceReferenceDate + APPLE_REFERENCE_DATE_EPOCH_SECONDS).toEpochParts()
    }

private fun Double.toEpochParts(): Pair<Long, Int> {
    var seconds = floor(this).toLong()
    var milliseconds = ((this - seconds) * 1_000.0).roundToInt()
    if (milliseconds == 1_000) {
        seconds++
        milliseconds = 0
    }
    return seconds to milliseconds * 1_000_000
}

private const val APPLE_REFERENCE_DATE_EPOCH_SECONDS: Double = 978_307_200.0
