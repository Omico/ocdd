package me.omico.ocdd.io

import okio.FileNotFoundException as OkioFileNotFoundException

internal inline fun <T> iosOperation(
    operation: FileSystemOperation,
    path: Path,
    otherPath: Path? = null,
    partialResult: Boolean = false,
    reasonOverride: FileSystemErrorReason? = null,
    block: () -> T,
): T =
    try {
        block()
    } catch (exception: FileSystemException) {
        throw exception
    } catch (exception: UnsupportedOperationException) {
        throw exception
    } catch (exception: IllegalArgumentException) {
        throw exception
    } catch (exception: OkioFileNotFoundException) {
        throw FileSystemException(
            message = exception.message ?: "$operation failed for $path",
            operation = operation,
            path = path,
            otherPath = otherPath,
            reason = FileSystemErrorReason.NOT_FOUND,
            partialResult = partialResult,
        )
    } catch (exception: Throwable) {
        throw FileSystemException(
            message = exception.message ?: "$operation failed for $path",
            operation = operation,
            path = path,
            otherPath = otherPath,
            reason = reasonOverride ?: FileSystemErrorReason.IO_FAILURE,
            partialResult = partialResult,
        )
    }
