package app.ransel.platform

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.dialogs.openFileWithDefaultApplication
import io.github.vinceglb.filekit.dialogs.shareFile
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.write

/** Documents in the app's own files folder ("documents/"), shared images in the cache. */
private object MobileDeviceFiles : DeviceFiles {
    override val keepsFiles = true

    private fun folder(): PlatformFile = FileKit.filesDir / "documents"

    override suspend fun has(name: String) = (folder() / name).exists()

    override suspend fun save(name: String, bytes: ByteArray) {
        folder().createDirectories()
        (folder() / name).write(bytes)
    }

    override suspend fun open(name: String) = FileKit.openFileWithDefaultApplication(folder() / name)

    override suspend fun delete(name: String) {
        val file = folder() / name
        if (file.exists()) file.delete()
    }

    override suspend fun share(fileName: String, bytes: ByteArray) {
        val file = FileKit.cacheDir / fileName
        file.write(bytes)
        FileKit.shareFile(file)
    }
}

actual fun deviceFiles(): DeviceFiles = MobileDeviceFiles
