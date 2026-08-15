package me.omico.ocdd.io

internal inline fun <T> withTemporaryDirectory(block: (Path) -> T): T {
    val directory = createTempDirectory(prefix = "ocdd-test-")
    return try {
        block(directory)
    } finally {
        directory.deleteRecursively()
    }
}
