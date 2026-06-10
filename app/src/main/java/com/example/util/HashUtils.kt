package com.example.util

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale

object HashUtils {

    /**
     * Generates a secure random 16-byte salt, returned as a hex string.
     */
    fun generateSalt(): String {
        val random = SecureRandom()
        val saltBytes = ByteArray(16)
        random.nextBytes(saltBytes)
        return bytesToHex(saltBytes)
    }

    /**
     * Hashes a password together with its salt using SHA-256.
     */
    fun hashPassword(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val combinedInput = password + salt
        val hashBytes = digest.digest(combinedInput.toByteArray(Charsets.UTF_8))
        return bytesToHex(hashBytes)
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format(Locale.US, "%02x", b))
        }
        return sb.toString()
    }
}
