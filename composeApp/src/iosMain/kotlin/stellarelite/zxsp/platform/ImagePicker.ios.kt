package stellarelite.zxsp.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

@Composable
actual fun rememberImagePicker(onPicked: (ImageBitmap?) -> Unit): () -> Unit {
    // iOS 相册选择暂未接入（当前以 Android 为主）
    return { onPicked(null) }
}
