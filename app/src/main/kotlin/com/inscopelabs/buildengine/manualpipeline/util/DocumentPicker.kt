package com.inscopelabs.buildengine.manualpipeline.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import java.io.ByteArrayOutputStream

/**
 * Zero-permission Storage Access Framework (SAF) document picker for `.zip` archives and
 * companion deliverable files. Uses [ActivityResultContracts.OpenDocument] only; never requests
 * `READ_EXTERNAL_STORAGE` or media storage permissions.
 */
object DocumentPicker {

    val ZIP_MIME_TYPES = arrayOf(
        "application/zip",
        "application/x-zip-compressed",
        "application/octet-stream",
    )

    val DELIVERABLE_MIME_TYPES = arrayOf("*/*")

    fun resolveDisplayName(context: Context, uri: Uri): String {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else {
                    null
                }
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment
            ?: "submission.zip"
    }

    fun readBytesBounded(context: Context, uri: Uri, maxBytes: Long = ZipBuilder.MAX_ARCHIVE_BYTES): ByteArray? {
        return context.contentResolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(buf)
                if (read == -1) break
                total += read
                if (total > maxBytes) return null
                out.write(buf, 0, read)
            }
            out.toByteArray()
        }
    }
}

@Composable
fun rememberManualArchivePicker(
    context: Context,
    onPicked: (ZipBuilder.BuildResult) -> Unit,
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val displayName = DocumentPicker.resolveDisplayName(context, uri)
        val result = ZipBuilder.inspectFromStream(
            sourceDisplayName = displayName,
            openStream = { context.contentResolver.openInputStream(uri) },
        )
        onPicked(result)
    }
    return {
        launcher.launch(DocumentPicker.ZIP_MIME_TYPES)
    }
}

@Composable
fun rememberDeliverableFilePicker(
    context: Context,
    onPicked: (displayName: String, bytes: ByteArray?) -> Unit,
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = DocumentPicker.resolveDisplayName(context, uri)
        val bytes = DocumentPicker.readBytesBounded(context, uri)
        onPicked(name, bytes)
    }
    return {
        launcher.launch(DocumentPicker.DELIVERABLE_MIME_TYPES)
    }
}
