import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.muncho.kaptal.App

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Kaptal",
    ) {
        App()
    }
}
