package com.inscopelabs.buildengine.manualpipeline.domain.model

/**
 * Cryptographic integrity summary of a single submission `.zip` archive (contract §3.10).
 */
data class ArchiveDigest(
    val zipSha256: String,
    val totalSizeBytes: Long,
    val fileDigests: Map<String, String>,
    val fileSizesBytes: Map<String, Long>,
) {
    init {
        require(zipSha256.matches(Regex("^[0-9a-fA-F]{64}$"))) {
            "zipSha256 must be a 64-character hex SHA-256 digest"
        }
    }
}
