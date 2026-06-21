package dev.pawelsowa.focusgate.domain

import java.net.IDN

class DomainNormalizer {
    fun normalize(input: String): String {
        val trimmed = input.trim().lowercase()
        val withoutDot = if (trimmed.endsWith('.')) trimmed.dropLast(1) else trimmed

        require(withoutDot.isNotEmpty()) { "INVALID_DOMAIN" }
        require(!withoutDot.contains("://")) { "INVALID_DOMAIN" }
        require(!withoutDot.contains('/')) { "INVALID_DOMAIN" }
        require(!withoutDot.contains(':')) { "INVALID_DOMAIN" }

        val ascii = IDN.toASCII(withoutDot)
        val parts = ascii.split('.')

        require(parts.size >= 2) { "INVALID_DOMAIN" }
        require(parts.all(::isValidLabel)) { "INVALID_DOMAIN" }

        return ascii.lowercase()
    }

    private fun isValidLabel(label: String): Boolean {
        if (label.isEmpty() || label.length > 63) {
            return false
        }

        if (label.first() == '-' || label.last() == '-') {
            return false
        }

        return label.all { it.isLowerCase() || it.isDigit() || it == '-' }
    }
}
