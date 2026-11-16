package com.inscopelabs.buildengine.manualpipeline.util

import com.inscopelabs.buildengine.domain.usecase.write.ArchivePreparationResult
import com.inscopelabs.buildengine.domain.usecase.write.SubmissionArchivePreparer
import com.inscopelabs.buildengine.manualpipeline.domain.model.ArchiveDigest
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Single-archive builder and contract §3.10 validator wrapping [SubmissionArchivePreparer].
 * Produces a single immutable archive buffer hashed once (`zipSha256`) and base64-encoded once.
 */
object ZipBuilder {

    const val MAX_ARCHIVE_BYTES: Long = SubmissionArchivePreparer.MAX_UPLOAD_BYTES

    data class BuiltArchive(
        val zipSha256: String,
        val zipBase64: String,
        val digest: ArchiveDigest,
        val entryPaths: List<String>,
        val sourceDisplayName: String = "",
    ) {
        override fun toString(): String =
            "BuiltArchive(zipSha256=$zipSha256, entries=${entryPaths.size}, bytes=${digest.totalSizeBytes})"
    }

    sealed interface BuildResult {
        data class Ready(val archive: BuiltArchive) : BuildResult
        data class Invalid(val reason: String) : BuildResult
    }

    fun inspectFromStream(
        sourceDisplayName: String,
        openStream: () -> InputStream?,
    ): BuildResult = when (val res = SubmissionArchivePreparer.prepareFromStream(openStream, MAX_ARCHIVE_BYTES)) {
        is ArchivePreparationResult.Invalid -> BuildResult.Invalid(res.reason)
        is ArchivePreparationResult.Ready -> {
            val inspected = Sha256Hasher.inspectZipDigests(res.archive.bytes)
            BuildResult.Ready(
                BuiltArchive(
                    zipSha256 = res.archive.zipSha256,
                    zipBase64 = res.archive.zipBase64,
                    digest = ArchiveDigest(
                        zipSha256 = inspected.zipSha256,
                        totalSizeBytes = inspected.totalSizeBytes,
                        fileDigests = inspected.fileDigests,
                        fileSizesBytes = inspected.fileSizesBytes,
                    ),
                    entryPaths = res.archive.entryPaths,
                    sourceDisplayName = sourceDisplayName,
                ),
            )
        }
    }

    fun buildFromEntries(
        entries: Map<String, ByteArray>,
        sourceDisplayName: String = "manual-bundle.zip",
    ): BuildResult {
        if (entries.isEmpty()) {
            return BuildResult.Invalid("No deliverables supplied to build submission archive.")
        }
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            entries.toSortedMap().forEach { (rawPath, content) ->
                val cleanPath = rawPath.replace('\\', '/').trimStart('/')
                val entry = ZipEntry(cleanPath)
                zos.putNextEntry(entry)
                zos.write(content)
                zos.closeEntry()
            }
        }
        val rawBytes = baos.toByteArray()
        return when (val res = SubmissionArchivePreparer.prepareFromBytes(rawBytes, MAX_ARCHIVE_BYTES)) {
            is ArchivePreparationResult.Invalid -> BuildResult.Invalid(res.reason)
            is ArchivePreparationResult.Ready -> {
                val inspected = Sha256Hasher.inspectZipDigests(res.archive.bytes)
                BuildResult.Ready(
                    BuiltArchive(
                        zipSha256 = inspected.zipSha256,
                        zipBase64 = Base64.getEncoder().encodeToString(res.archive.bytes),
                        digest = ArchiveDigest(
                            zipSha256 = inspected.zipSha256,
                            totalSizeBytes = inspected.totalSizeBytes,
                            fileDigests = inspected.fileDigests,
                            fileSizesBytes = inspected.fileSizesBytes,
                        ),
                        entryPaths = res.archive.entryPaths,
                        sourceDisplayName = sourceDisplayName,
                    ),
                )
            }
        }
    }
}
