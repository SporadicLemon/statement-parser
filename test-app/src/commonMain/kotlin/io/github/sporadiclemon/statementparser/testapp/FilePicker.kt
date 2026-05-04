package io.github.sporadiclemon.statementparser.testapp

import androidx.compose.runtime.Composable

/**
 * A simple cross-platform file picker.
 */
@Composable
expect fun FilePicker(
    show: Boolean,
    onFilePicked: (name: String, content: String) -> Unit,
    onDismiss: () -> Unit
)
