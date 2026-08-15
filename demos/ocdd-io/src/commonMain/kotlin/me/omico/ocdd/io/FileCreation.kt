package me.omico.ocdd.io

@Throws(IOException::class)
public expect fun Path.createFile(vararg attributes: FileAttribute): Path

@Throws(IOException::class)
public expect fun Path.createDirectory(vararg attributes: FileAttribute): Path

@Throws(IOException::class)
public expect fun Path.createDirectories(vararg attributes: FileAttribute): Path

@Throws(IOException::class)
public expect fun Path.createParentDirectories(vararg attributes: FileAttribute): Path

@Throws(IOException::class)
public expect fun Path.createLinkPointingTo(target: Path): Path

@Throws(IOException::class)
public expect fun Path.createSymbolicLinkPointingTo(
    target: Path,
    vararg attributes: FileAttribute,
): Path

@Throws(IOException::class)
public expect fun createTempDirectory(
    prefix: String? = null,
    vararg attributes: FileAttribute,
): Path

@Throws(IOException::class)
public expect fun createTempDirectory(
    directory: Path?,
    prefix: String? = null,
    vararg attributes: FileAttribute,
): Path

@Throws(IOException::class)
public expect fun createTempFile(
    prefix: String? = null,
    suffix: String? = null,
    vararg attributes: FileAttribute,
): Path

@Throws(IOException::class)
public expect fun createTempFile(
    directory: Path?,
    prefix: String? = null,
    suffix: String? = null,
    vararg attributes: FileAttribute,
): Path

internal fun validateTemporaryPart(
    value: String?,
    label: String,
): String {
    val result = value.orEmpty()
    requireValidUnicode(result, label)
    require('/' !in result) { "$label must not contain '/'" }
    return result
}

internal fun validateCreationAttributes(attributes: Array<out FileAttribute>) {
    require(attributes.map(FileAttribute::name).distinct().size == attributes.size) {
        "Creation attributes must not contain duplicate names"
    }
}
