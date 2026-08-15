package me.omico.ocdd.io

public actual open class IOException actual constructor(
    message: String?,
    cause: Throwable?,
) : Exception(message, cause) {
    public actual constructor(message: String?) : this(message, null)

    public actual constructor() : this(null, null)
}

public actual class ProtocolException actual constructor(
    message: String,
) : IOException(message)

public actual open class EOFException actual constructor(
    message: String?,
) : IOException(message) {
    public actual constructor() : this(null)
}

public actual open class FileNotFoundException actual constructor(
    message: String?,
) : IOException(message)
