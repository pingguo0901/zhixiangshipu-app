package stellarelite.zxsp.platform

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State

// 打印结果提示共享状态：平台层打印完成后写入，UI 层（App.kt）监听弹出内置 Dialog
object PrintNotifier {
    private val _message = mutableStateOf<String?>(null)
    val message: State<String?> get() = _message

    fun show(msg: String) { _message.value = msg }
    fun dismiss() { _message.value = null }
}
