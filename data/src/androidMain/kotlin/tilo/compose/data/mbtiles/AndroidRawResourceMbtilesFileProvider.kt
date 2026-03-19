package tilo.compose.data.mbtiles

import android.content.Context
import java.io.FileOutputStream

class AndroidRawResourceMbtilesFileProvider(
    private val context: Context,
    private val rawResourceId: Int,
    private val cacheFileName: String = "vector_dataset.mbtiles"
) : MbtilesFileProvider {
    override fun provideDatabasePath(): String {
        val outputFile = context.getDatabasePath(cacheFileName)
        outputFile.parentFile?.mkdirs()
        if (!outputFile.exists()) {
            context.resources.openRawResource(rawResourceId).use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return outputFile.absolutePath
    }
}
