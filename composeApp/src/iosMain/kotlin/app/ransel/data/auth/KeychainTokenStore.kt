package app.ransel.data.auth

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * The session as one generic-password Keychain item (accessible after first unlock, never synced to
 * iCloud). Written for the iOS target but NOT compiled on the Linux build host: verify on a Mac.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class KeychainTokenStore : TokenStore {
    private val json = Json { ignoreUnknownKeys = true }

    /** Builds a Keychain query dictionary, runs [block] with it and releases everything. */
    private fun <T> withQuery(configure: (CFDictionaryRef?, MutableList<Any?>) -> Unit, block: (CFDictionaryRef?) -> T): T {
        val dictionary = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        val retained = mutableListOf<Any?>()
        fun put(key: Any?, value: Any?) {
            CFDictionaryAddValue(dictionary, key as kotlinx.cinterop.COpaquePointer?, value as kotlinx.cinterop.COpaquePointer?)
        }
        put(kSecClass, kSecClassGenericPassword)
        val service = CFBridgingRetain(SERVICE as NSString)
        val account = CFBridgingRetain(ACCOUNT as NSString)
        put(kSecAttrService, service)
        put(kSecAttrAccount, account)
        retained += service
        retained += account
        configure(dictionary, retained)
        try {
            return block(dictionary)
        } finally {
            retained.forEach { (it as? kotlinx.cinterop.COpaquePointer)?.let(::CFRelease) }
            CFRelease(dictionary)
        }
    }

    override suspend fun read(): StoredSession? = memScoped {
        val result = alloc<CFTypeRefVar>()
        val status = withQuery(
            configure = { dictionary, _ ->
                CFDictionaryAddValue(dictionary, kSecReturnData, kCFBooleanTrue)
                CFDictionaryAddValue(dictionary, kSecMatchLimit, kSecMatchLimitOne)
            },
            block = { SecItemCopyMatching(it, result.ptr) },
        )
        if (status != errSecSuccess) return@memScoped null
        val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
        val text = NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString() ?: return@memScoped null
        runCatching { json.decodeFromString<StoredSession>(text) }.getOrNull()
    }

    override suspend fun write(session: StoredSession) {
        clear()
        @Suppress("CAST_NEVER_SUCCEEDS")
        val data = (json.encodeToString(session) as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        withQuery(
            configure = { dictionary, retained ->
                val value = CFBridgingRetain(data)
                retained += value
                CFDictionaryAddValue(dictionary, kSecValueData, value)
                CFDictionaryAddValue(dictionary, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
            },
            block = { SecItemAdd(it, null) },
        )
    }

    override suspend fun clear() {
        withQuery(configure = { _, _ -> }, block = { SecItemDelete(it) })
    }

    private companion object {
        const val SERVICE = "app.ransel.session"
        const val ACCOUNT = "session"
    }
}
