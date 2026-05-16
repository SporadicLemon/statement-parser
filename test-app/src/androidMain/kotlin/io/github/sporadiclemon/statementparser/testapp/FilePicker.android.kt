package io.github.sporadiclemon.statementparser.testapp

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
                val fileName = uri.path?.split("/")?.lastOrNull() ?: "statement"
                onFilePicked(fileName, bytes)
            } else {
                onDismiss()
            }
        }
    )

    LaunchedEffect(show) {
        if (show) {
            launcher.launch(arrayOf("*/*")) // Allow all, though we could filter
        }
    }
}
