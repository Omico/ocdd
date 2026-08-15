package me.omico.ocdd.io

public expect open class IOException(
    message: String?,
    cause: Throwable?,
) : Exception {
    public constructor(message: String?)

    public constructor()
}

public expect class ProtocolException(
    message: String,
) : IOException

public expect open class EOFException(
    message: String?,
) : IOException {
    public constructor()
}

public expect class FileNotFoundException(
    message: String?,
) : IOException
