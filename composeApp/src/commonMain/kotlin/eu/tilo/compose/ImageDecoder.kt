package eu.tilo.compose

import androidx.compose.ui.graphics.ImageBitmap

/** Decodes raw image bytes into a Compose [ImageBitmap]. Returns null on failure. */
expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap?

