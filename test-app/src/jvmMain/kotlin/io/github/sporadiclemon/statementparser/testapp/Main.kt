package io.github.sporadiclemon.statementparser.testapp

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Statement Parser Test") {
        App()
    }
}
