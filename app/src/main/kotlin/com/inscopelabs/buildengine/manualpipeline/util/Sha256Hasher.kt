package com.inscopelabs.buildengine.manualpipeline.util

import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Computes full-archive SHA-256 and per-entry SHA-256 digests and sizes without altering bytes.
 */
object Sha256Hasher {

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { b -> "%02x".format(b.toInt() and 0xFF) }

    fun inspectZipDigests(zipBytes: ByteArray): ZipIntegrityInspection {
        val zipSha256 = sha256Hex(zipBytes)
        val fileDigests = linkedMapOf<String, String>()
        val fileSizes = linkedMapOf<String, Long>()

        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            val buffer = ByteArray(8192)
            while (entry != null) {
                if (!entry.isDirectory) {
                    val normalizedName = entry.name.replace('\\', '/').trimStart('/')
                    val digest = MessageDigest.getInstance("SHA-256")
                    var size = 0L
                    while (true) {
                        val read = zis.read(buffer)
                        if (read == -1) break
                        digest.update(buffer, 0, read)
                        size += read
                    }
                    fileDigests[normalizedName] = digest.digest()
                        .joinToString(separator = "") { b -> "%02x".format(b.toInt() and 0xFF) }
                    fileSizes[normalizedName] = size
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        return ZipIntegrityInspection(
            zipSha256 = zipSha256,
            totalSizeBytes = zipBytes.size.toLong(),
            fileDigests = fileDigests.toMap(),
            fileSizesBytes = fileSizes.toMap(),
        )
    }
}

data class ZipIntegrityInspection(
    val zipSha256: String,
    val totalSizeBytes: Long,
    val fileDigests: Map<String, String>,
    val fileSizesBytes: Map<String, Long>,
)
