package stellarelite.zxsp.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

// 桌面端：用文件选择器挑选图片（替代相册）
@Composable
actual fun rememberImagePicker(onPicked: (ImageBitmap?) -> Unit): () -> Unit {
    return {
        val chooser = JFileChooser().apply {
            fileFilter = FileNameExtensionFilter("Images (*.jpg, *.png, *.jpeg)", "jpg", "png", "jpeg")
        }
        val result = chooser.showOpenDialog(null)
        if (result == JFileChooser.APPROVE_OPTION) {
            val f: File = chooser.selectedFile
            val bmp = try { f.readBytes().toImageBitmap() } catch (e: Exception) { null }
            onPicked(bmp)
        } else {
            onPicked(null)
        }
    }
}
