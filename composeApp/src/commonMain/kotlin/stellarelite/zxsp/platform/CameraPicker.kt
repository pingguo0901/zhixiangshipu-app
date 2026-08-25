package stellarelite.zxsp.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

@Composable
expect fun rememberCamera(onCaptured: (ImageBitmap?) -> Unit): () -> Unit
