package app.ransel.shared.dto

import app.ransel.shared.model.DocumentCategory
import app.ransel.shared.model.DocumentKind
import app.ransel.shared.serialization.Timestamp
import kotlinx.serialization.Serializable

/*
 * Trip documents (PRD P2 "Trip documents"): tickets, booking confirmations and vouchers as PDF or image files, or a link
 * (the booking link of a STAY poll winner). Every member sees them; EDITOR+ adds them; the uploader or the OWNER
 * deletes them. Online only (not in /sync); the app keeps opened files on the device for use without signal.
 */

/** File limits (owner decision 26 Oct 2026). */
const val MAX_DOCUMENT_BYTES: Long = 20L * 1024 * 1024
const val MAX_TRIP_DOCUMENT_BYTES: Long = 200L * 1024 * 1024

/** PDF and the common image types. */
val DOCUMENT_MIME_TYPES = setOf("application/pdf", "image/jpeg", "image/png", "image/webp", "image/heic")

@Serializable
data class TripDocumentDto(
    val id: String,
    val tripId: String,
    val title: String,
    val category: DocumentCategory = DocumentCategory.OTHER,
    val kind: DocumentKind,
    /** LINK only. */
    val url: String? = null,
    /** FILE only: the original name, mime type and size. */
    val fileName: String? = null,
    val mime: String? = null,
    val sizeBytes: Long? = null,
    /** The activity it belongs to ("Hari 1, 06.10"), if any. */
    val itineraryItemId: String? = null,
    val uploadedByMemberId: String,
    val uploadedByName: String,
    val createdAt: Timestamp,
)

/** GET /trips/{id}/documents: newest first, with the trip's storage use. */
@Serializable
data class TripDocumentsDto(
    val items: List<TripDocumentDto>,
    val usedBytes: Long,
    val limitBytes: Long = MAX_TRIP_DOCUMENT_BYTES,
)

/**
 * POST /trips/{id}/documents as JSON: a link. A file goes to the same path as multipart/form-data with the parts
 * `meta` (this object as JSON, [url] null) and `file` (the bytes, with its file name and content type).
 */
@Serializable
data class CreateDocumentRequest(
    val id: String,
    val title: String,
    val category: DocumentCategory = DocumentCategory.OTHER,
    val url: String? = null,
    val itineraryItemId: String? = null,
)

/** PATCH /trips/{id}/documents/{docId} (the uploader or EDITOR+). null = unchanged; [unlinkItinerary] clears the activity. */
@Serializable
data class PatchDocumentRequest(
    val title: String? = null,
    val category: DocumentCategory? = null,
    val itineraryItemId: String? = null,
    val unlinkItinerary: Boolean = false,
)

/**
 * GET /trips/{id}/documents/{docId}/download: for a FILE, a path relative to the API base (like place photos) that works
 * without a bearer token for 15 minutes; for a LINK, the link itself.
 */
@Serializable
data class DocumentDownloadDto(val url: String, val expiresAt: Timestamp)
