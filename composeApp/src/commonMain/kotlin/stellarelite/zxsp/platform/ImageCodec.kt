package stellarelite.zxsp.platform

import androidx.compose.ui.graphics.ImageBitmap

expect fun ImageBitmap.toJpegBytes(quality: Int = 80): ByteArray?

expect fun ByteArray.toImageBitmap(): ImageBitmap?
