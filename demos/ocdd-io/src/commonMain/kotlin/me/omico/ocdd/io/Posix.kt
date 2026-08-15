package me.omico.ocdd.io

internal val defaultPosixFilePermissions: Set<PosixFilePermission> =
    setOf(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE,
        PosixFilePermission.GROUP_READ,
        PosixFilePermission.GROUP_WRITE,
        PosixFilePermission.OTHERS_READ,
        PosixFilePermission.OTHERS_WRITE,
    )

internal val defaultPosixDirectoryPermissions: Set<PosixFilePermission> =
    defaultPosixFilePermissions +
        setOf(
            PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_EXECUTE,
            PosixFilePermission.OTHERS_EXECUTE,
        )
