package io.github.sporadiclemon.statementparser.testapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
actual fun FilePicker(
    show: Boolean,
    onFilePicked: (name: String, content: String) -> Unit,
    onDismiss: () -> Unit
) {
    LaunchedEffect(show) {
        if (show) {
            val dialog = FileDialog(null as Frame?, "Select Statement", FileDialog.LOAD)
            dialog.isVisible = true
            
            val file = dialog.file
            val dir = dialog.directory
            
            if (file != null && dir != null) {
                val pickedFile = File(dir, file)
                onFilePicked(file, pickedFile.readText())
            } else {
                onDismiss()
            }
        }
    }
}
