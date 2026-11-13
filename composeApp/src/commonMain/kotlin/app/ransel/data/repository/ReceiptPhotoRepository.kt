package app.ransel.data.repository

import app.ransel.feature.expenses.receipt.ReceiptImage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Where a receipt photo that has not reached the server stands (spec "Foto struk di pengeluaran", screens 7 and 8). */
enum class ReceiptUploadStatus {
    /** Kept on the phone, goes up when online and the expense is on the server ("Menunggu upload"). */
    WAITING,
    /** The upload failed again and again ("Gagal diunggah · Coba lagi"). */
    FAILED,
    /** The trip's 200 MB of documents is full: the expense stays without a photo ("Kuota dokumen penuh"). */
    FULL,
}

class PendingReceipt(val expenseId: String, val title: String, val bytes: ByteArray, val mimeType: String, val status: ReceiptUploadStatus)

/**
 * Receipt photos on their way to the server. The expense is saved at once (Room, sync); the photo is kept here and sent by
 * the sync once the expense is on the server, because the server's document needs the expense to belong to. Phones only:
 * the web is online-only and has no queue, so it attaches no photo yet ([supported] false hides the sections).
 */
interface ReceiptPhotoRepository {
    val supported: Boolean
    /** Photos waiting or failed for the trip's expenses, by expense id. */
    fun observe(tripId: String): Flow<Map<String, PendingReceipt>>
    /** Keeps [image] for the expense (a photo already waiting for it is replaced). */
    suspend fun attach(tripId: String, expenseId: String, title: String, image: ReceiptImage)
    suspend fun retry(expenseId: String)
    suspend fun discard(expenseId: String)
}

/** The web: no photos attached. */
object NoReceiptPhotos : ReceiptPhotoRepository {
    override val supported = false
    override fun observe(tripId: String): Flow<Map<String, PendingReceipt>> = MutableStateFlow(emptyMap())
    override suspend fun attach(tripId: String, expenseId: String, title: String, image: ReceiptImage) = Unit
    override suspend fun retry(expenseId: String) = Unit
    override suspend fun discard(expenseId: String) = Unit
}

/** For tests and previews. */
class InMemoryReceiptPhotoRepository : ReceiptPhotoRepository {
    override val supported = true
    val photos = MutableStateFlow<Map<String, Pair<String, PendingReceipt>>>(emptyMap())

    override fun observe(tripId: String): Flow<Map<String, PendingReceipt>> =
        photos.map { all -> all.filterValues { it.first == tripId }.mapValues { it.value.second } }

    override suspend fun attach(tripId: String, expenseId: String, title: String, image: ReceiptImage) {
        photos.value = photos.value + (expenseId to (tripId to PendingReceipt(expenseId, title, image.bytes, image.mimeType, ReceiptUploadStatus.WAITING)))
    }

    override suspend fun retry(expenseId: String) {
        photos.value = photos.value.mapValues { (id, v) ->
            if (id == expenseId) v.first to PendingReceipt(id, v.second.title, v.second.bytes, v.second.mimeType, ReceiptUploadStatus.WAITING) else v
        }
    }

    override suspend fun discard(expenseId: String) {
        photos.value = photos.value - expenseId
    }
}
