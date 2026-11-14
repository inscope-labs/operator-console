package com.inscopelabs.buildengine.ui.submission

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import com.inscopelabs.buildengine.domain.usecase.write.ArchivePreparationResult
import com.inscopelabs.buildengine.domain.usecase.write.SubmissionArchivePreparer

/**
 * Zero-permission Storage Access Framework (SAF) picker for submission `.zip` archives (T3-403).
 *
 * Uses [ActivityResultContracts.OpenDocument] + [android.content.ContentResolver.openInputStream]
 * so the console never requests broad storage permissions (`READ_EXTERNAL_STORAGE` / media
 * permissions) and leaves the user-selected source file completely untouched.
 */
@Composable
fun rememberSubmissionArchivePicker(
    context: Context,
    onResult: (ArchivePreparationResult) -> Unit,
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }
        val prepared = SubmissionArchivePreparer.prepareFromStream(
            openStream = { context.contentResolver.openInputStream(uri) },
        )
        onResult(prepared)
    }
    return {
        launcher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
    }
}
