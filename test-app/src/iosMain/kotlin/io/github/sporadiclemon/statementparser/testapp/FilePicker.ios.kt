package io.github.sporadiclemon.statementparser.testapp

import androidx.compose.runtime.Composable

@Composable
actual fun FilePicker(
    show: Boolean,
    onFilePicked: (name: String, content: String) -> Unit,
    onDismiss: () -> Unit
) {
    // Note: iOS implementation usually requires platform-specific UIViewController logic
    // or a bridge to UIDocumentPickerViewController. 
    // For now, providing a placeholder that can be expanded.
}
