package io.github.sporadiclemon.statementparser.testapp

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun FilePicker(
    show: Boolean,
    onFilePicked: (name: String, bytes: ByteArray) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                val bytes = context.contentResolver.openInputStream(uri)?.use {
                    it.readBytes()
                } ?: ByteArray(0)
                val fileName = resolveFileName(context.contentResolver, uri)
                onFilePicked(fileName, bytes)
            } else {
                onDismiss()
            }
        }
    )

    LaunchedEffect(show) {
        if (show) {
            launcher.launch(arrayOf("*/*"))
        }
    }
}

private fun resolveFileName(resolver: ContentResolver, uri: Uri): String {
    // Query display name — works for Google Drive and all content providers
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val name = cursor.getString(0)
            if (!name.isNullOrBlank()) return name
        }
    }
    // Fall back to MIME type → synthesize extension
    val ext = when (resolver.getType(uri)) {
        "application/pdf" -> ".pdf"
        "text/csv", "text/comma-separated-values", "application/vnd.ms-excel" -> ".csv"
        "application/x-ofx", "text/x-ofx", "application/ofx" -> ".ofx"
        else -> ""
    }
    return "statement$ext"
}
