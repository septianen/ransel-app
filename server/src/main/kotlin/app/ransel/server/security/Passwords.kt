package app.ransel.server.security

import at.favre.lib.crypto.bcrypt.BCrypt

class PasswordHasher(private val cost: Int) {
    fun hash(password: String): String = BCrypt.withDefaults().hashToString(cost, password.toCharArray())

    fun verify(password: String, hash: String): Boolean =
        BCrypt.verifyer().verify(password.toCharArray(), hash).verified

    /** A valid hash nobody knows the password of; login verifies against it when the email is unknown. */
    val decoy: String by lazy { hash("decoy-" + java.util.UUID.randomUUID()) }

    companion object {
        /** bcrypt only looks at the first 72 bytes; longer passwords are rejected instead of silently cut. */
        const val MAX_BYTES = 72
        fun isTooLong(password: String) = password.toByteArray(Charsets.UTF_8).size > MAX_BYTES
    }
}
