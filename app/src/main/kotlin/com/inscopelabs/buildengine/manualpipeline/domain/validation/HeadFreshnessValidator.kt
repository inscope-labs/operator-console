package com.inscopelabs.buildengine.manualpipeline.domain.validation

import javax.inject.Inject

/**
 * Validates that a branch head SHA is a full 40-character hexadecimal string and matches
 * the live Gateway A branch head (I-07).
 */
class HeadFreshnessValidator @Inject constructor() {

    sealed interface Result {
        data class Fresh(val verifiedHeadSha: String) : Result
        data class InvalidFormat(val rawSha: String) : Result
        data class Drifted(val expectedSha: String, val liveSha: String) : Result
    }

    fun validate(expectedSha: String, liveSha: String): Result {
        val cleanExpected = expectedSha.trim().lowercase()
        val cleanLive = liveSha.trim().lowercase()
        if (!FULL_SHA_REGEX.matches(cleanExpected)) {
            return Result.InvalidFormat(expectedSha)
        }
        if (!FULL_SHA_REGEX.matches(cleanLive)) {
            return Result.InvalidFormat(liveSha)
        }
        return if (cleanExpected == cleanLive) {
            Result.Fresh(cleanLive)
        } else {
            Result.Drifted(expectedSha = cleanExpected, liveSha = cleanLive)
        }
    }

    fun isFullSha(sha: String?): Boolean =
        sha != null && FULL_SHA_REGEX.matches(sha.trim())

    companion object {
        private val FULL_SHA_REGEX = Regex("^[0-9a-f]{40}$")
    }
}
