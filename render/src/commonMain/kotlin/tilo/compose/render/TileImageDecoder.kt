package tilo.compose.render

import androidx.compose.ui.graphics.ImageBitmap

internal expect fun decodeTileImageBitmap(bytes: ByteArray): ImageBitmap?
