@file:JvmName("AndroidPosixAttributesNative")

package me.omico.ocdd.io

import android.system.ErrnoException

internal class AndroidPosixAttributes internal constructor(
    internal val mode: Int,
    internal val size: Long,
    internal val lastModifiedTime: FileTime,
    internal val lastAccessTime: FileTime,
)

private object AndroidPosixAttributesLibrary {
    init {
        System.loadLibrary("ocdd_io_posix")
    }

    fun load(): Unit = Unit
}

private external fun readAttributes(
    path: ByteArray,
    noFollowLinks: Boolean,
): LongArray

private external fun setLastModifiedTime(
    path: ByteArray,
    seconds: Long,
    nanoseconds: Int,
): Int

internal fun Path.androidAttributes(noFollowLinks: Boolean): AndroidPosixAttributes {
    AndroidPosixAttributesLibrary.load()
    val result = readAttributes(androidPathString().encodeToByteArray(), noFollowLinks)
    val error = result[0].toInt()
    if (error != 0) throw ErrnoException(if (noFollowLinks) "lstat" else "stat", error)
    return AndroidPosixAttributes(
        mode = result[1].toInt(),
        size = result[2],
        lastModifiedTime = FileTime(result[3], result[4].toMillisecondNanoseconds()),
        lastAccessTime = FileTime(result[5], result[6].toMillisecondNanoseconds()),
    )
}

private fun Long.toMillisecondNanoseconds(): Int = (this / 1_000_000L * 1_000_000L).toInt()

internal fun Path.setAndroidLastModifiedTime(value: FileTime) {
    AndroidPosixAttributesLibrary.load()
    val error =
        setLastModifiedTime(
            path = androidPathString().encodeToByteArray(),
            seconds = value.epochSeconds,
            nanoseconds = value.nanosecondsOfSecond,
        )
    if (error != 0) throw ErrnoException("utimensat", error)
}
