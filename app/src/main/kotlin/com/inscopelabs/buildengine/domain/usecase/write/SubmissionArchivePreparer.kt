package com.inscopelabs.buildengine.domain.usecase.write

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/**
 * Immutable submission archive ready for `POST /tasks/{id}/upload` (§3.10; T3-403, PR3-022).
 *
 * [toString] is intentionally redacted so [bytes] and [zipBase64] can never leak into logs.
 */
class PreparedSubmissionArchive internal constructor(
    val bytes: ByteArray,
    val zipSha256: String,
    val zipBase64: String,
    val entryPaths: List<String>,
) {
    override fun toString(): String =
        "PreparedSubmissionArchive(size=${bytes.size}, zipSha256=$zipSha256, entries=${entryPaths.size})"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PreparedSubmissionArchive) return false
        return zipSha256 == other.zipSha256 &&
            zipBase64 == other.zipBase64 &&
            entryPaths == other.entryPaths &&
            bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + zipSha256.hashCode()
        result = 31 * result + zipBase64.hashCode()
        result = 31 * result + entryPaths.hashCode()
        return result
    }
}

sealed interface ArchivePreparationResult {
    data class Ready(val archive: PreparedSubmissionArchive) : ArchivePreparationResult
    data class Invalid(val reason: String) : ArchivePreparationResult
}

/**
 * Pure JVM preparation and client-side safety validation for submission ZIP archives (T3-403).
 *
 * Enforces:
 * - Non-empty archive within [MAX_UPLOAD_BYTES] (25 MiB matching gateway `MAX_UPLOAD_BYTES`).
 * - Single immutable byte buffer hashed once with SHA-256 and encoded once with Base64 (never
 *   re-packed or rewritten).
 * - Valid ZIP container structure (`PK` local header + readable entries >= 1, no corrupt stream).
 * - Zero path traversal (`..` segments, leading `/` or `\`, NUL bytes, drive letters) and zero
 *   denied repository paths (`engine/`, `plans/`, `.github/`).
 */
object SubmissionArchivePreparer {

    const val MAX_UPLOAD_BYTES: Long = 25L * 1024L * 1024L

    private val DENIED_PREFIXES = listOf("engine/", "plans/", ".github/")
    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B) // "PK"

    fun prepareFromStream(
        openStream: () -> InputStream?,
        maxBytes: Long = MAX_UPLOAD_BYTES,
    ): ArchivePreparationResult {
        val stream = try {
            openStream()
        } catch (e: SecurityException) {
            return ArchivePreparationResult.Invalid("Cannot read selected archive: ${e.message ?: "permission denied"}")
        } catch (e: IOException) {
            return ArchivePreparationResult.Invalid("Cannot read selected archive: ${e.message ?: "IO error"}")
        } ?: return ArchivePreparationResult.Invalid("No archive stream returned by the document picker")

        val rawBytes = try {
            stream.use { input -> readBoundedBytes(input, maxBytes) }
        } catch (e: OversizedArchiveException) {
            return ArchivePreparationResult.Invalid(
                "Submission archive exceeds the maximum size of $maxBytes bytes",
            )
        } catch (e: IOException) {
            return ArchivePreparationResult.Invalid("Failed reading archive: ${e.message ?: "IO error"}")
        }

        return prepareFromBytes(rawBytes, maxBytes = maxBytes)
    }

    fun prepareFromBytes(
        bytes: ByteArray,
        maxBytes: Long = MAX_UPLOAD_BYTES,
    ): ArchivePreparationResult {
        if (bytes.isEmpty()) {
            return ArchivePreparationResult.Invalid("Submission archive is empty")
        }
        if (bytes.size.toLong() > maxBytes) {
            return ArchivePreparationResult.Invalid(
                "Submission archive (${bytes.size} bytes) exceeds maximum of $maxBytes bytes",
            )
        }
        if (bytes.size < 4 || bytes[0] != ZIP_MAGIC[0] || bytes[1] != ZIP_MAGIC[1]) {
            return ArchivePreparationResult.Invalid("Submission file is not a valid ZIP archive")
        }

        // Preserve exact immutable byte sequence before hashing/encoding so nothing alters it.
        val immutableBytes = bytes.copyOf()
        val entryPaths = try {
            validateZipEntries(immutableBytes)
        } catch (e: InvalidArchiveEntryException) {
            return ArchivePreparationResult.Invalid(e.message ?: "Archive contains a forbidden path")
        } catch (e: ZipException) {
            return ArchivePreparationResult.Invalid("Submission archive is corrupt: ${e.message ?: "invalid ZIP"}")
        } catch (e: IOException) {
            return ArchivePreparationResult.Invalid("Submission archive could not be parsed: ${e.message ?: "IO error"}")
        }

        val sha256Hex = MessageDigest.getInstance("SHA-256")
            .digest(immutableBytes)
            .joinToString(separator = "") { b -> "%02x".format(b.toInt() and 0xFF) }
        val base64 = Base64.getEncoder().encodeToString(immutableBytes)

        return ArchivePreparationResult.Ready(
            PreparedSubmissionArchive(
                bytes = immutableBytes,
                zipSha256 = sha256Hex,
                zipBase64 = base64,
                entryPaths = entryPaths,
            ),
        )
    }

    private fun readBoundedBytes(input: InputStream, maxBytes: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            total += read
            if (total > maxBytes) {
                throw OversizedArchiveException()
            }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun validateZipEntries(bytes: ByteArray): List<String> {
        val paths = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val rawName = entry.name
                val normalized = validateEntryPath(rawName)
                if (!entry.isDirectory) {
                    paths += normalized
                }
                // Drain entry so CRC/stream integrity errors on corrupt entries surface here.
                val scratch = ByteArray(4096)
                while (zis.read(scratch) != -1) {
                    // draining to verify stream integrity without mutating source bytes
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        if (paths.isEmpty()) {
            throw InvalidArchiveEntryException("Submission ZIP contains no file entries")
        }
        return paths.toList()
    }

    private fun validateEntryPath(rawName: String): String {
        if (rawName.isBlank()) {
            throw InvalidArchiveEntryException("Submission ZIP contains an empty entry path")
        }
        if (rawName.contains('\u0000')) {
            throw InvalidArchiveEntryException("Submission ZIP contains a NUL byte in entry path")
        }
        if (rawName.startsWith("/") || rawName.startsWith("\\")) {
            throw InvalidArchiveEntryException("Absolute path rejected in archive: $rawName")
        }
        if (rawName.length >= 2 && rawName[1] == ':' && rawName[0].isLetter()) {
            throw InvalidArchiveEntryException("Drive-qualified path rejected in archive: $rawName")
        }
        val slashNormalized = rawName.replace('\\', '/')
        val segments = slashNormalized.split('/')
        if (segments.any { it == ".." }) {
            throw InvalidArchiveEntryException("Path traversal rejected in archive: $rawName")
        }
        val clean = segments.filter { it.isNotEmpty() && it != "." }.joinToString("/")
        if (clean.isEmpty()) {
            throw InvalidArchiveEntryException("Invalid archive entry path: $rawName")
        }
        val lower = clean.lowercase()
        if (DENIED_PREFIXES.any { prefix -> lower == prefix.trimEnd('/') || lower.startsWith(prefix) }) {
            throw InvalidArchiveEntryException("Denied repository path rejected in archive: $clean")
        }
        return clean
    }

    private class OversizedArchiveException : RuntimeException()
    private class InvalidArchiveEntryException(message: String) : RuntimeException(message)
}
