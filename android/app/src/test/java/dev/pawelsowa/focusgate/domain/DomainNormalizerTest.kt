package dev.pawelsowa.focusgate.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainNormalizerTest {
    private val normalizer = DomainNormalizer()

    @Test
    fun `normalizes whitespace case trailing dot and internationalized names`() {
        assertEquals(
            "xn--bcher-kva.example",
            normalizer.normalize("  BÜCHER.example. ").getOrThrow(),
        )
    }

    @Test
    fun `rejects URL protocol path and port`() {
        listOf(
            "https://example.com",
            "example.com/path",
            "example.com:443",
        ).forEach { input ->
            assertTrue(input, normalizer.normalize(input).isFailure)
        }
    }

    @Test
    fun `rejects malformed hostnames`() {
        listOf(
            "localhost",
            "-example.com",
            "example-.com",
            "example..com",
        ).forEach { input ->
            assertTrue(input, normalizer.normalize(input).isFailure)
        }
    }
}
