package com.inscopelabs.buildengine.manualpipeline.util

/**
 * Formatting and validation utilities for 40-character hexadecimal Git head SHAs (I-07).
 */
object HeadHex {

    const val CONFIRMATION_PREFIX_LENGTH = 7
    private val FULL_40_REGEX = Regex("^[0-9a-fA-F]{40}$")

    fun isFull40(sha: String?): Boolean =
        sha != null && FULL_40_REGEX.matches(sha.trim())

    fun normalize(sha: String?): String =
        sha?.trim()?.lowercase().orEmpty()

    fun first7(sha: String?): String =
        normalize(sha).take(CONFIRMATION_PREFIX_LENGTH)

    fun grouped40(sha: String?): String {
        val clean = normalize(sha)
        if (clean.isEmpty()) return ""
        return clean.chunked(8).joinToString(" ")
    }
}
