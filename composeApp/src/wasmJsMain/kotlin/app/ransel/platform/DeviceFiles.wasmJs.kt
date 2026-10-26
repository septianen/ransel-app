package app.ransel.platform

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.download

/** The browser keeps nothing; sharing is a download. */
private object WebDeviceFiles : DeviceFiles {
    override val keepsFiles = false
    override suspend fun has(name: String) = false
    override suspend fun save(name: String, bytes: ByteArray) = Unit
    override suspend fun open(name: String) = Unit
    override suspend fun delete(name: String) = Unit
    override suspend fun share(fileName: String, bytes: ByteArray) = FileKit.download(bytes, fileName)
}

actual fun deviceFiles(): DeviceFiles = WebDeviceFiles
