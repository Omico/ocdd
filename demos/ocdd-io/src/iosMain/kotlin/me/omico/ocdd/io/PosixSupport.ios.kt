package me.omico.ocdd.io

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.EACCES
import platform.posix.EEXIST
import platform.posix.EISDIR
import platform.posix.ELOOP
import platform.posix.ENOENT
import platform.posix.ENOTDIR
import platform.posix.ENOTEMPTY
import platform.posix.EPERM
import platform.posix.S_IRGRP
import platform.posix.S_IROTH
import platform.posix.S_IRUSR
import platform.posix.S_IWGRP
import platform.posix.S_IWOTH
import platform.posix.S_IWUSR
import platform.posix.S_IXGRP
import platform.posix.S_IXOTH
import platform.posix.S_IXUSR
import platform.posix.strerror

internal fun posixReason(errorCode: Int): FileSystemErrorReason =
    when (errorCode) {
        ENOENT -> FileSystemErrorReason.NOT_FOUND
        EEXIST -> FileSystemErrorReason.ALREADY_EXISTS
        ENOTDIR -> FileSystemErrorReason.NOT_A_DIRECTORY
        EISDIR -> FileSystemErrorReason.IS_A_DIRECTORY
        ENOTEMPTY -> FileSystemErrorReason.DIRECTORY_NOT_EMPTY
        EACCES, EPERM -> FileSystemErrorReason.ACCESS_DENIED
        ELOOP -> FileSystemErrorReason.FILE_SYSTEM_LOOP
        else -> FileSystemErrorReason.IO_FAILURE
    }

@OptIn(ExperimentalForeignApi::class)
internal fun posixFileSystemException(
    errorCode: Int,
    operation: FileSystemOperation,
    path: Path,
    otherPath: Path? = null,
    partialResult: Boolean = false,
    reasonOverride: FileSystemErrorReason? = null,
): FileSystemException =
    FileSystemException(
        message = strerror(errorCode)?.toKString() ?: "$operation failed for $path",
        operation = operation,
        path = path,
        otherPath = otherPath,
        reason = reasonOverride ?: posixReason(errorCode),
        partialResult = partialResult,
    )

internal fun IosFileMetadata.permissions(): Set<PosixFilePermission> =
    buildSet {
        if (mode and S_IRUSR != 0) add(PosixFilePermission.OWNER_READ)
        if (mode and S_IWUSR != 0) add(PosixFilePermission.OWNER_WRITE)
        if (mode and S_IXUSR != 0) add(PosixFilePermission.OWNER_EXECUTE)
        if (mode and S_IRGRP != 0) add(PosixFilePermission.GROUP_READ)
        if (mode and S_IWGRP != 0) add(PosixFilePermission.GROUP_WRITE)
        if (mode and S_IXGRP != 0) add(PosixFilePermission.GROUP_EXECUTE)
        if (mode and S_IROTH != 0) add(PosixFilePermission.OTHERS_READ)
        if (mode and S_IWOTH != 0) add(PosixFilePermission.OTHERS_WRITE)
        if (mode and S_IXOTH != 0) add(PosixFilePermission.OTHERS_EXECUTE)
    }

internal fun Set<PosixFilePermission>.toMode(): Int =
    fold(0) { mode, permission ->
        mode or
            when (permission) {
                PosixFilePermission.OWNER_READ -> S_IRUSR
                PosixFilePermission.OWNER_WRITE -> S_IWUSR
                PosixFilePermission.OWNER_EXECUTE -> S_IXUSR
                PosixFilePermission.GROUP_READ -> S_IRGRP
                PosixFilePermission.GROUP_WRITE -> S_IWGRP
                PosixFilePermission.GROUP_EXECUTE -> S_IXGRP
                PosixFilePermission.OTHERS_READ -> S_IROTH
                PosixFilePermission.OTHERS_WRITE -> S_IWOTH
                PosixFilePermission.OTHERS_EXECUTE -> S_IXOTH
            }
    }
