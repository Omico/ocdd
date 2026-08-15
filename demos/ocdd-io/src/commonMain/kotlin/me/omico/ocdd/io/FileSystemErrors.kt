package me.omico.ocdd.io

public enum class FileSystemOperation {
    ABSOLUTE_PATH,
    REAL_PATH,
    STATUS,
    CREATE,
    DELETE,
    OPEN,
    READ,
    WRITE,
    CLOSE,
    LIST,
    TRAVERSE,
    COPY,
    MOVE,
    READ_ATTRIBUTES,
    WRITE_ATTRIBUTES,
    FILE_STORE,
}

public enum class FileSystemErrorReason {
    NOT_FOUND,
    ALREADY_EXISTS,
    NOT_A_DIRECTORY,
    IS_A_DIRECTORY,
    NOT_A_SYMBOLIC_LINK,
    DIRECTORY_NOT_EMPTY,
    ACCESS_DENIED,
    FILE_SYSTEM_LOOP,
    INVALID_ENCODING,
    IO_FAILURE,
}

public class FileSystemException internal constructor(
    message: String,
    public val operation: FileSystemOperation,
    public val path: Path,
    public val otherPath: Path? = null,
    public val reason: FileSystemErrorReason,
    public val partialResult: Boolean = false,
) : IOException(message)
