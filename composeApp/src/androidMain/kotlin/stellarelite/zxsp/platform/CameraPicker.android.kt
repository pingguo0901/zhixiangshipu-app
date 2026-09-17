package stellarelite.zxsp.platform

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

// 用 TakePicture（完整照片存到临时文件）替代 TakePicturePreview（缩略图，部分机型会返回 null）
@Composable
actual fun rememberCamera(onCaptured: (ImageBitmap?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val photoUri = remember {
        val file = File.createTempFile("receipt_", ".jpg", context.cacheDir)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            onCaptured(
                runCatching {
                    context.contentResolver.openInputStream(photoUri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                }.getOrNull()
            )
        } else {
            onCaptured(null)
        }
    }
    return { launcher.launch(photoUri) }
}
