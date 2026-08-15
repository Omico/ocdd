package me.omico.ocdd.io

import android.os.Build
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructStat

/** Android's NIO desugaring delegates these missing API 21-25 capabilities to this thin POSIX adapter. */
internal val usesAndroidPosixFallback: Boolean
    get() = Build.VERSION.SDK_INT in 1..25

internal fun Path.androidPathString(): String = toNioPath().toString()

internal fun Path.androidStat(noFollowLinks: Boolean): StructStat =
    if (noFollowLinks) Os.lstat(androidPathString()) else Os.stat(androidPathString())

internal fun Set<PosixFilePermission>.toAndroidMode(): Int =
    fold(0) { mode, permission ->
        mode or
            when (permission) {
                PosixFilePermission.OWNER_READ -> OsConstants.S_IRUSR
                PosixFilePermission.OWNER_WRITE -> OsConstants.S_IWUSR
                PosixFilePermission.OWNER_EXECUTE -> OsConstants.S_IXUSR
                PosixFilePermission.GROUP_READ -> OsConstants.S_IRGRP
                PosixFilePermission.GROUP_WRITE -> OsConstants.S_IWGRP
                PosixFilePermission.GROUP_EXECUTE -> OsConstants.S_IXGRP
                PosixFilePermission.OTHERS_READ -> OsConstants.S_IROTH
                PosixFilePermission.OTHERS_WRITE -> OsConstants.S_IWOTH
                PosixFilePermission.OTHERS_EXECUTE -> OsConstants.S_IXOTH
            }
    }

internal fun Int.toOcddPermissions(): Set<PosixFilePermission> =
    buildSet {
        fun addWhen(
            mask: Int,
            permission: PosixFilePermission,
        ) {
            if (this@toOcddPermissions and mask != 0) add(permission)
        }
        addWhen(OsConstants.S_IRUSR, PosixFilePermission.OWNER_READ)
        addWhen(OsConstants.S_IWUSR, PosixFilePermission.OWNER_WRITE)
        addWhen(OsConstants.S_IXUSR, PosixFilePermission.OWNER_EXECUTE)
        addWhen(OsConstants.S_IRGRP, PosixFilePermission.GROUP_READ)
        addWhen(OsConstants.S_IWGRP, PosixFilePermission.GROUP_WRITE)
        addWhen(OsConstants.S_IXGRP, PosixFilePermission.GROUP_EXECUTE)
        addWhen(OsConstants.S_IROTH, PosixFilePermission.OTHERS_READ)
        addWhen(OsConstants.S_IWOTH, PosixFilePermission.OTHERS_WRITE)
        addWhen(OsConstants.S_IXOTH, PosixFilePermission.OTHERS_EXECUTE)
    }

internal fun Array<out FileAttribute>.androidCreationMode(defaultMode: Int): Int =
    firstOrNull { it.name == "posix:permissions" }
        ?.let { immutablePermissions(it.value).toAndroidMode() }
        ?: defaultMode

internal fun ErrnoException.toFileSystemReason(): FileSystemErrorReason =
    when (errno) {
        OsConstants.ENOENT -> FileSystemErrorReason.NOT_FOUND
        OsConstants.EEXIST -> FileSystemErrorReason.ALREADY_EXISTS
        OsConstants.ENOTDIR -> FileSystemErrorReason.NOT_A_DIRECTORY
        OsConstants.ENOTEMPTY -> FileSystemErrorReason.DIRECTORY_NOT_EMPTY
        OsConstants.EACCES, OsConstants.EPERM -> FileSystemErrorReason.ACCESS_DENIED
        OsConstants.ELOOP -> FileSystemErrorReason.FILE_SYSTEM_LOOP
        OsConstants.EISDIR -> FileSystemErrorReason.IS_A_DIRECTORY
        else -> FileSystemErrorReason.IO_FAILURE
    }
