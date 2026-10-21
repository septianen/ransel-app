package app.ransel.admin.data

import app.ransel.shared.api.ErrorCode
import app.ransel.shared.api.PageMeta

/**
 * A failed call as the screens see it: they switch on [code] (an `ErrorCode`), never on the HTTP status or message.
 */
data class ApiFailure(
    val code: String,
    val message: String,
    /** HTTP status when the server answered; null when it never did (timeout, offline). */
    val status: Int? = null,
    val details: Map<String, String>? = null,
) {
    val isConnection: Boolean
        get() = code == ErrorCode.NETWORK_TIMEOUT || code == ErrorCode.NETWORK_UNAVAILABLE
}

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(val error: ApiFailure) : ApiResult<Nothing>
}

/** One page of a list endpoint (25 rows, `meta` from the envelope). */
data class Page<T>(val items: List<T>, val meta: PageMeta) {
    val pageCount: Int get() = if (meta.total == 0) 1 else (meta.total + meta.pageSize - 1) / meta.pageSize
    val hasNext: Boolean get() = meta.page < pageCount
    val first: Int get() = if (items.isEmpty()) 0 else (meta.page - 1) * meta.pageSize + 1
    val last: Int get() = (meta.page - 1) * meta.pageSize + items.size
}
