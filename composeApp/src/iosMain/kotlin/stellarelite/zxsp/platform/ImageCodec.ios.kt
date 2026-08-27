package stellarelite.zxsp.platform

import androidx.compose.ui.graphics.ImageBitmap

// iOS 图片编解码暂未接入（当前以 Android 为主）
actual fun ImageBitmap.toJpegBytes(quality: Int): ByteArray? = null

actual fun ByteArray.toImageBitmap(): ImageBitmap? = null
