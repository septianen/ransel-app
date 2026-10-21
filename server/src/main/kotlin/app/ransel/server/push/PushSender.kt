package app.ransel.server.push

/** What one phone gets. [data] carries `type`, `tripId` and `notificationId` for the tap. */
data class PushMessage(val title: String, val body: String, val data: Map<String, String>)

enum class PushResult {
    OK,
    /** The token is dead (app uninstalled, token rotated): disable the device. */
    INVALID_TOKEN,
    /** A temporary failure: try again later. */
    RETRY,
}

fun interface PushSender {
    suspend fun send(token: String, message: PushMessage): PushResult
}

/** Used when FCM is not configured (dev, tests, staging): the inbox works, the push is only logged. */
class LoggingPushSender : PushSender {
    private val log = org.slf4j.LoggerFactory.getLogger(LoggingPushSender::class.java)

    override suspend fun send(token: String, message: PushMessage): PushResult {
        log.info("push.logged type={} (FCM is not configured)", message.data["type"])
        return PushResult.OK
    }
}
