package me.omico.ocdd.io

internal fun compareUtf8PathStrings(
    firstValue: String,
    secondValue: String,
): Int {
    val first = firstValue.encodeToByteArray()
    val second = secondValue.encodeToByteArray()
    val count = minOf(first.size, second.size)
    for (index in 0 until count) {
        val comparison = (first[index].toInt() and 0xff).compareTo(second[index].toInt() and 0xff)
        if (comparison != 0) return comparison
    }
    return first.size.compareTo(second.size)
}

internal fun decodeFileUriPath(encodedPath: String): String {
    validateEncodedUriPath(encodedPath)
    val bytes = mutableListOf<Byte>()
    var index = 0
    while (index < encodedPath.length) {
        if (encodedPath[index] == '%') {
            val high = encodedPath[index + 1].hexValue()
            val low = encodedPath[index + 2].hexValue()
            bytes += ((high shl 4) or low).toByte()
            index += 3
        } else {
            val start = index
            index += if (encodedPath[index].isHighSurrogateCodeUnit()) 2 else 1
            encodedPath.substring(start, index).encodeToByteArray().forEach(bytes::add)
        }
    }
    val byteArray = ByteArray(bytes.size) { bytes[it] }
    val decoded =
        try {
            byteArray.decodeToString(throwOnInvalidSequence = true)
        } catch (exception: CharacterCodingException) {
            throw IllegalArgumentException("File URI path is not valid UTF-8", exception)
        }
    requireValidUnicode(decoded, "file URI path")
    require(decoded.startsWith('/')) { "File URI path must decode to an absolute path" }
    return decoded
}

internal fun requireValidUnicode(
    value: String,
    label: String,
) {
    var index = 0
    while (index < value.length) {
        val character = value[index]
        require(character.code != 0) { "$label must not contain U+0000" }
        when {
            character.isHighSurrogateCodeUnit() -> {
                require(index + 1 < value.length && value[index + 1].isLowSurrogateCodeUnit()) {
                    "$label must be well-formed Unicode"
                }
                index += 2
            }

            character.isLowSurrogateCodeUnit() -> {
                throw IllegalArgumentException("$label must be well-formed Unicode")
            }

            else -> {
                index++
            }
        }
    }
}

internal fun validateEncodedUriPath(value: String) {
    requireValidUnicode(value, "file URI")
    require(value.startsWith('/')) { "File URI path must be absolute" }
    var index = 0
    while (index < value.length) {
        val character = value[index]
        require(character.code > 0x20 && character.code != 0x7f && character != '\\') {
            "File URI contains an invalid character"
        }
        if (character == '%') {
            require(index + 2 < value.length) { "File URI contains an incomplete percent escape" }
            require(value[index + 1].isHexDigit() && value[index + 2].isHexDigit()) {
                "File URI contains an invalid percent escape"
            }
            index += 3
        } else {
            index += if (character.isHighSurrogateCodeUnit()) 2 else 1
        }
    }
}

internal fun validatePercentEscapes(value: String) {
    var index = 0
    while (index < value.length) {
        if (value[index] == '%') {
            require(index + 2 < value.length && value[index + 1].isHexDigit() && value[index + 2].isHexDigit()) {
                "File URI contains an invalid percent escape"
            }
            index += 3
        } else {
            index++
        }
    }
}

private fun Char.isHighSurrogateCodeUnit(): Boolean = code in 0xd800..0xdbff

private fun Char.isLowSurrogateCodeUnit(): Boolean = code in 0xdc00..0xdfff

private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

private fun Char.hexValue(): Int =
    when (this) {
        in '0'..'9' -> code - '0'.code
        in 'a'..'f' -> code - 'a'.code + 10
        in 'A'..'F' -> code - 'A'.code + 10
        else -> throw IllegalArgumentException("Invalid hexadecimal digit")
    }
