package stellarelite.zxsp.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

@Composable
actual fun rememberCamera(onCaptured: (ImageBitmap?) -> Unit): () -> Unit {
    // iOS 相机拍照暂未接入（当前以 Android 为主）
    return { onCaptured(null) }
}
