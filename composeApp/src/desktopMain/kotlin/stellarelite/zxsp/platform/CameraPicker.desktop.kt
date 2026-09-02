package stellarelite.zxsp.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

// 桌面端：拍照改为选图（电脑一般用摄像头，这里先用文件选择替代）
@Composable
actual fun rememberCamera(onCaptured: (ImageBitmap?) -> Unit): () -> Unit {
    return {
        val chooser = JFileChooser().apply {
            fileFilter = FileNameExtensionFilter("Images (*.jpg, *.png, *.jpeg)", "jpg", "png", "jpeg")
        }
        val result = chooser.showOpenDialog(null)
        if (result == JFileChooser.APPROVE_OPTION) {
            val f: File = chooser.selectedFile
            val bmp = try { f.readBytes().toImageBitmap() } catch (e: Exception) { null }
            onCaptured(bmp)
        } else {
            onCaptured(null)
        }
    }
}
