package stellarelite.zxsp

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import stellarelite.zxsp.data.LanguageManager
import stellarelite.zxsp.data.SessionManager

fun main() = application {
    SessionManager.load()
    LanguageManager.load()

    val state = rememberWindowState(width = 1280.dp, height = 820.dp)
    Window(
        onCloseRequest = ::exitApplication,
        title = "炙巷食铺 · ZHI XIANG FOOD ENTERPRISE",
        state = state
    ) {
        App(
            useSideNav = true,
            onCheckUpdate = { DesktopUpdater.checkForUpdate() },
            onRequestUpdate = { info -> DesktopUpdater.openDownload(info.apkUrl) }
        )
    }
}
