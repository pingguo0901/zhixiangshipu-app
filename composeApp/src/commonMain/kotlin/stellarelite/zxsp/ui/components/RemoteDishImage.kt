package stellarelite.zxsp.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import stellarelite.zxsp.network.SupabaseClient
import stellarelite.zxsp.platform.toImageBitmap
import stellarelite.zxsp.ui.theme.DiningColors

// 从 URL 加载图片，加载完成前显示 emoji 占位
@Composable
fun RemoteDishImage(url: String?, emoji: String, size: Dp) {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        if (url != null) {
            bitmap = withContext(Dispatchers.Default) {
                SupabaseClient.downloadFile(url)?.toImageBitmap()
            }
        } else {
            bitmap = null
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(DiningColors.SurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = if (size.value >= 48f) 22.sp else 18.sp)
        }
    }
}
