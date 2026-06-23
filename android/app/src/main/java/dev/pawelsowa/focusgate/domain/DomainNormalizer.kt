package dev.pawelsowa.focusgate.domain

import java.net.IDN

class DomainNormalizer {
    fun normalize(input: String): Result<String> = runCatching {
        val candidate = input.trim().lowercase().removeSuffix(".")
        require(candidate.isNotEmpty()) { "Domain is required" }
        require("://" !in candidate) { "Protocols are not allowed" }
        require('/' !in candidate && '\\' !in candidate) { "Paths are not allowed" }
        require(':' !in candidate) { "Ports are not allowed" }

        val ascii = IDN.toASCII(candidate, IDN.USE_STD3_ASCII_RULES).lowercase()
        require(ascii.length <= 253) { "Domain is too long" }
        require('.' in ascii) { "Domain must include a public suffix" }

        val labels = ascii.split('.')
        require(labels.all(::isValidLabel)) { "Invalid domain" }
        ascii
    }

    private fun isValidLabel(label: String): Boolean =
        label.isNotEmpty() &&
            label.length <= 63 &&
            label.first() != '-' &&
            label.last() != '-' &&
            label.all { it.isLetterOrDigit() || it == '-' }
}
