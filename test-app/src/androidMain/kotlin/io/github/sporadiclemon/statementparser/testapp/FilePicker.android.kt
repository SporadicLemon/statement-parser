package io.github.sporadiclemon.statementparser.testapp

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun FilePicker(
    show: Boolean,
    onFilePicked: (name: String, content: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                val content = context.contentResolver.openInputStream(uri)?.use { 
                    it.bufferedReader().readText() 
                } ?: ""
                val fileName = uri.path?.split("/")?.lastOrNull() ?: "statement"
                onFilePicked(fileName, content)
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
