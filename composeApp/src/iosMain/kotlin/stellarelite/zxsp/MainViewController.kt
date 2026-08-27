package stellarelite.zxsp

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController
import stellarelite.zxsp.data.SessionManager

fun MainViewController(): UIViewController {
    SessionManager.load()
    return ComposeUIViewController { App() }
}
