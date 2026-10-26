package app.ransel.platform

/**
 * Files on this device: trip documents kept for use without signal (mobile), and images to share (the recap). The web
 * keeps nothing: [share] downloads there, and documents open from their signed link.
 */
interface DeviceFiles {
    val keepsFiles: Boolean
    suspend fun has(name: String): Boolean
    suspend fun save(name: String, bytes: ByteArray)
    /** Opens a kept file in the phone's viewer (PDF reader, gallery). */
    suspend fun open(name: String)
    suspend fun delete(name: String)
    /** The system share sheet (mobile) or a download (web). */
    suspend fun share(fileName: String, bytes: ByteArray)
}

expect fun deviceFiles(): DeviceFiles
