package stellarelite.zxsp

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import stellarelite.zxsp.data.LanguageManager
import stellarelite.zxsp.data.SessionManager

fun main() = application {
    SessionManager.load()
    LanguageManager.load()

    val state = rememberWindowState(placement = WindowPlacement.Fullscreen)
    Window(
        onCloseRequest = ::exitApplication,
        title = "炙巷食铺 · ZHI XIANG FOOD ENTERPRISE",
        state = state,
        undecorated = true
    ) {
        App(
            useDesktopLayout = true,
            onExit = ::exitApplication,
            onCheckUpdate = { DesktopUpdater.checkForUpdate() },
            onApplyUpdate = { info, onProgress ->
                val err = DesktopUpdater.downloadAndApply(info.apkUrl, onProgress)
                if (err == null) exitApplication()
                err
            }
        )
    }
}
