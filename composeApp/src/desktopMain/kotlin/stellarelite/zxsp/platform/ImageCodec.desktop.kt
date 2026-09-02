package stellarelite.zxsp.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

actual fun ImageBitmap.toJpegBytes(quality: Int): ByteArray? {
    return try {
        Image.makeFromBitmap(asSkiaBitmap())
            .encodeToData(EncodedImageFormat.JPEG, quality)?.bytes
    } catch (e: Exception) { null }
}

actual fun ByteArray.toImageBitmap(): ImageBitmap? {
    return try {
        Image.makeFromEncoded(this).toComposeImageBitmap()
    } catch (e: Exception) { null }
}
