package com.inscopelabs.buildengine.manualpipeline.domain.usecase

import com.inscopelabs.buildengine.manualpipeline.domain.model.ArchiveDigest
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import javax.inject.Inject

/**
 * MP-006: Pure-Kotlin SHA-256 integrity calculator over an immutable `.zip` archive byte buffer
 * and its individual file entries (contract §3.10).
 */
class CalculateArtifactIntegrityUseCase @Inject constructor() {

    fun calculateFromZipBytes(zipBytes: ByteArray): Result<ArchiveDigest> = runCatching {
        require(zipBytes.isNotEmpty()) { "Submission archive bytes must not be empty." }
        val zipSha256 = MessageDigest.getInstance("SHA-256")
            .digest(zipBytes)
            .joinToString(separator = "") { b -> "%02x".format(b.toInt() and 0xFF) }

        val fileDigests = linkedMapOf<String, String>()
        val fileSizes = linkedMapOf<String, Long>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            val buffer = ByteArray(8192)
            while (entry != null) {
                if (!entry.isDirectory) {
                    val path = entry.name.replace('\\', '/').trimStart('/')
                    val digest = MessageDigest.getInstance("SHA-256")
                    var size = 0L
                    while (true) {
                        val read = zis.read(buffer)
                        if (read == -1) break
                        digest.update(buffer, 0, read)
                        size += read
                    }
                    fileDigests[path] = digest.digest()
                        .joinToString(separator = "") { b -> "%02x".format(b.toInt() and 0xFF) }
                    fileSizes[path] = size
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        require(fileDigests.isNotEmpty()) { "Submission archive must contain at least one file." }
        ArchiveDigest(
            zipSha256 = zipSha256,
            totalSizeBytes = zipBytes.size.toLong(),
            fileDigests = fileDigests.toMap(),
            fileSizesBytes = fileSizes.toMap(),
        )
    }
}
