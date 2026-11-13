package app.ransel.feature.documents.viewer

import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.platform.DeviceFiles

/**
 * Loads the photo of a trip document: the copy kept on the phone, else a download that is kept for the next time (phones;
 * the web downloads every time). Shared by the photo viewer, the Struk rows of "Dokumen trip" and the expense form.
 */
class DocumentPhotoSource(private val tripId: String, private val api: RanselApi, private val files: DeviceFiles) {
    val keepsFiles: Boolean get() = files.keepsFiles

    /** [name] is the file name on the phone (see `DocumentsViewModel.fileName`). [onKept] runs when a download was kept. */
    suspend fun load(docId: String, name: String, onKept: () -> Unit = {}): PhotoLoad {
        if (files.keepsFiles) files.read(name)?.let { return PhotoLoad.Ready(it) }
        val link = when (val result = api.documentDownload(tripId, docId)) {
            is ApiResult.Success -> result.value.url
            is ApiResult.Failure -> return if (result.error.status == null) PhotoLoad.Offline else PhotoLoad.Failed
        }
        return when (val bytes = api.downloadBytes(link)) {
            is ApiResult.Success -> {
                if (files.keepsFiles) {
                    files.save(name, bytes.value)
                    onKept()
                }
                PhotoLoad.Ready(bytes.value)
            }
            is ApiResult.Failure -> if (bytes.error.status == null) PhotoLoad.Offline else PhotoLoad.Failed
        }
    }

    companion object {
        /** The name a receipt photo is kept under when only its document id is known (the expense form). */
        fun receiptName(docId: String) = "$docId.jpg"
    }
}
