package me.omico.ocdd.io

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PathTest {
    @Test
    fun parsingAndQueriesFollowTheAbstractPathModel() {
        // Parsing collapses separators without erasing dot names or backslashes.
        val empty = "".toPath()
        val root = "/".toPath()
        val absolute = "//a//b/".toPath()
        val backslash = "a\\b".toPath()

        assertPath(empty, "", null, listOf(""), "", null)
        assertPath(root, "/", "/", emptyList(), null, null)
        assertPath(absolute, "/a/b", "/", listOf("a", "b"), "b", "/a")
        assertPath(backslash, "a\\b", null, listOf("a\\b"), "a\\b", null)
        assertTrue(root.isRoot)
        assertFalse(empty.isRoot)
        assertEquals(absolute.isAbsolute, !absolute.isRelative)
    }

    @Test
    fun namesIndexesAndExtensionsUseCompleteElements() {
        // Name access returns relative paths and validates every range boundary.
        val path = "/a/archive.tar.gz".toPath()

        assertEquals("a", path[0].toString())
        assertEquals("archive.tar.gz", path[1].toString())
        assertEquals("a/archive.tar.gz", path.subpath(0, 2).toString())
        assertEquals("archive.tar.gz", path.name)
        assertEquals("gz", path.extension)
        assertEquals("archive.tar", path.nameWithoutExtension)
        assertEquals(path.toString(), path.pathString)
        assertEquals(path.toString(), path.invariantSeparatorsPathString)
        assertFailsWith<IllegalArgumentException> { path[-1] }
        assertFailsWith<IllegalArgumentException> { path[2] }
        assertFailsWith<IllegalArgumentException> { path.subpath(0, 0) }
        assertFailsWith<IllegalArgumentException> { path.subpath(2, 1) }
    }

    @Test
    fun normalizeAppliesDotRulesUntilStable() {
        // Dot removal preserves unmatched relative parents but never escapes the root.
        val cases =
            mapOf(
                "." to "",
                "/" to "/",
                "a/./b" to "a/b",
                "a/../../b" to "../b",
                "/../../a" to "/a",
                "a/b/../.." to "",
                "../../a" to "../../a",
            )

        cases.forEach { (input, expected) ->
            val normalized = input.toPath().normalize()
            assertEquals(expected, normalized.toString(), input)
            assertEquals(normalized, normalized.normalize(), input)
        }
    }

    @Test
    fun resolveMatchingAndRelativizeUseNamesRatherThanSubstrings() {
        // Composition, prefix/suffix checks, and relative paths honor root boundaries.
        assertEquals("/a/b/c", pathOf("/a", "/b", "c").toString())
        assertEquals("/b", "/a".toPath().resolve("/b").toString())
        assertEquals("a/c", "a/b".toPath().resolveSibling("c").toString())
        assertTrue("/a/b".toPath().startsWith("/a"))
        assertTrue("/a/b".toPath().endsWith("a/b"))
        assertFalse("/a/b".toPath().endsWith("/b"))
        assertFalse("alpha".toPath().startsWith("a"))
        assertEquals("../c", "/a/b".toPath().relativize("/a/c".toPath()).toString())
        assertEquals("", "a".toPath().relativize("a".toPath()).toString())
        assertEquals("a", "".toPath().relativize("./a".toPath()).toString())
        assertEquals("../c", "/a/c".toPath().relativeTo("/a/b".toPath()).toString())
        assertNull("a".toPath().relativeToOrNull("/a".toPath()))
        assertEquals("a", "a".toPath().relativeToOrSelf("/a".toPath()).toString())
        assertFailsWith<IllegalArgumentException> { "../a".toPath().relativize("b".toPath()) }
    }

    @Test
    fun iterationEqualityHashingAndOrderingAreStable() {
        // Iteration mirrors the immutable segment snapshot and ordering uses unsigned UTF-8 bytes.
        val path = "/a/文".toPath()

        assertContentEquals(listOf("a", "文"), path.map(Path::toString))
        assertEquals(path, "/a/文".toPath())
        assertEquals(path.hashCode(), "/a/文".toPath().hashCode())
        assertTrue("\u007f".toPath() < "é".toPath())
        assertEquals(0, path.compareTo("/a/文".toPath()))
    }

    @Test
    fun fileUrisValidateAndDecodeUtf8() {
        // File URIs preserve their source value while decoding only the path into Path.
        val uri = FileUri("FiLe://LOCALHOST/a%20b/%F0%9F%98%80")

        assertEquals("FiLe://LOCALHOST/a%20b/%F0%9F%98%80", uri.value)
        assertEquals(uri.value, uri.toString())
        assertEquals("/a b/😀", uri.toPath().toString())
        assertEquals(FileUri("file:///a"), FileUri("file:///a"))
        assertFailsWith<IllegalArgumentException> { FileUri("https:///a") }
        assertFailsWith<IllegalArgumentException> { FileUri("file://remote/a") }
        assertFailsWith<IllegalArgumentException> { FileUri("file:///a?query") }
        assertFailsWith<IllegalArgumentException> { FileUri("file:///a%2") }
        assertFailsWith<IllegalArgumentException> { FileUri("file:///").toPath().resolve("\u0000") }
        assertFailsWith<IllegalArgumentException> { FileUri("file:///%ff").toPath() }
    }

    @Test
    fun invalidPathTextFailsBeforeCreatingAValue() {
        // NUL and unpaired UTF-16 surrogate code units are never admitted as paths.
        assertFailsWith<IllegalArgumentException> { "a\u0000b".toPath() }
        assertFailsWith<IllegalArgumentException> { "\uD800".toPath() }
        assertFailsWith<IllegalArgumentException> { "\uDC00".toPath() }
    }

    @Test
    fun fixedSeedPropertiesProtectRoundTripsAndRelativeComposition() {
        // Seed 3293 covers reproducible mixes of roots, names, separators, and dot elements.
        val random = Random(0x0cdd)
        repeat(500) {
            val path = randomPath(random)
            val reparsed = path.toString().toPath()
            assertEquals(path, reparsed)
            assertEquals(path.normalize(), path.normalize().normalize())
            assertEquals(path.nameCount, path.segments.size)
            assertEquals(path.isAbsolute, !path.isRelative)
            assertEquals(0, path.compareTo(reparsed))
            assertEquals(path.hashCode(), reparsed.hashCode())
        }

        repeat(300) {
            val base = randomPath(random, absolute = it % 2 == 0)
            val target = randomPath(random, absolute = base.isAbsolute)
            val relative =
                try {
                    base.relativize(target)
                } catch (_: IllegalArgumentException) {
                    return@repeat
                }
            assertEquals(
                target.normalize(),
                base.normalize().resolve(relative).normalize(),
                "base=$base target=$target relative=$relative",
            )
        }
    }

    private fun assertPath(
        path: Path,
        text: String,
        root: String?,
        segments: List<String>,
        fileName: String?,
        parent: String?,
    ) {
        assertEquals(text, path.toString())
        assertEquals(root, path.root?.toString())
        assertContentEquals(segments, path.segments)
        assertEquals(segments.size, path.nameCount)
        assertEquals(fileName, path.fileName?.toString())
        assertEquals(parent, path.parent?.toString())
    }

    private fun randomPath(
        random: Random,
        absolute: Boolean = random.nextBoolean(),
    ): Path {
        val names = listOf("a", "b", ".", "..", "文", "a\\b")
        val count = random.nextInt(0, 7)
        if (count == 0) return if (absolute) "/".toPath() else "".toPath()
        val prefix = if (absolute) "/".repeat(random.nextInt(1, 4)) else ""
        val separator = "/".repeat(random.nextInt(1, 4))
        val suffix = if (random.nextBoolean()) "/".repeat(random.nextInt(1, 3)) else ""
        return (prefix + List(count) { names.random(random) }.joinToString(separator) + suffix).toPath()
    }
}
