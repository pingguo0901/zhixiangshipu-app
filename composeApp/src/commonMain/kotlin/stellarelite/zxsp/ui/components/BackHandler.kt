package stellarelite.zxsp.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

// 全局返回栈：子页面/全屏页面注册自己的返回动作，ESC 触发最上层动作（后注册的先响应）
object BackHandlerOwner {
    private val handlers = mutableListOf<() -> Unit>()

    fun register(handler: () -> Unit): () -> Unit {
        handlers.add(handler)
        return { handlers.remove(handler) }
    }

    // 触发最上层的返回动作；没有可返回的动作时返回 false
    fun dispatchBack(): Boolean {
        val handler = handlers.lastOrNull() ?: return false
        handlers.removeAt(handlers.size - 1)
        handler()
        return true
    }
}

// 在子页面/全屏页面内调用：页面可见时注册返回动作，离开时自动注销
@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val currentOnBack by rememberUpdatedState(onBack)
    DisposableEffect(enabled) {
        if (enabled) {
            val dispose = BackHandlerOwner.register { currentOnBack() }
            onDispose { dispose() }
        } else {
            onDispose { }
        }
    }
}
