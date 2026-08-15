package me.omico.ocdd.io

internal inline fun <T> useResource(
    close: () -> Unit,
    block: () -> T,
): T {
    var failure: Throwable? = null
    try {
        return block()
    } catch (exception: Throwable) {
        failure = exception
        throw exception
    } finally {
        try {
            close()
        } catch (closeFailure: Throwable) {
            if (failure == null) throw closeFailure
            failure.addSuppressed(closeFailure)
        }
    }
}
