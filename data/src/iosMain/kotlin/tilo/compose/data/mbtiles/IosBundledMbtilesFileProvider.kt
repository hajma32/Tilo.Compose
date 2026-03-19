package tilo.compose.data.mbtiles

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
class IosBundledMbtilesFileProvider(
    private val resourceName: String,
    private val resourceExtension: String = "mbtiles",
    private val cacheFileName: String = "$resourceName.$resourceExtension"
) : MbtilesFileProvider {
    override fun provideDatabasePath(): String {
        val bundledPath = NSBundle.mainBundle.pathForResource(resourceName, resourceExtension)
            ?: error("MBTiles resource '$resourceName.$resourceExtension' was not found in the iOS bundle.")

        val cacheDirectory = (NSSearchPathForDirectoriesInDomains(
            directory = NSCachesDirectory,
            domainMask = NSUserDomainMask,
            expandTilde = true
        ).firstOrNull() as? String).orEmpty().ifBlank { NSTemporaryDirectory() }

        val normalizedCacheDirectory = cacheDirectory.trimEnd('/')
        val cachePath = "$normalizedCacheDirectory/$cacheFileName"
        val fileManager = NSFileManager.defaultManager
        if (!fileManager.fileExistsAtPath(cachePath)) {
            fileManager.copyItemAtPath(bundledPath, cachePath, null)
        }

        return cachePath
    }
}
