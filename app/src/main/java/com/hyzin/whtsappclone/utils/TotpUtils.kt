package com.hyzin.whtsappclone.utils

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

object TotpUtils {
    /**
     * Generates a 6-digit TOTP code based on a secret string and time step.
     * @param secret The shared secret (e.g., secretIdentity)
     * @param intervalSeconds Time step in seconds (default 30)
     * @return 6-digit OTP string
     */
    fun generateTOTP(secret: String, intervalSeconds: Int = 30): String {
        if (secret.isEmpty()) return "000000"
        
        val timeStep = System.currentTimeMillis() / 1000 / intervalSeconds
        return calculateOTP(secret, timeStep)
    }

    /**
     * Verifies if the provided OTP is valid for the given secret.
     * Allows a window of ±1 interval to account for clock drift.
     */
    fun verifyTOTP(secret: String, code: String, intervalSeconds: Int = 30): Boolean {
        if (secret.isEmpty() || code.length != 6) return false
        
        val currentTimeStep = System.currentTimeMillis() / 1000 / intervalSeconds
        
        // Check current, previous, and next intervals for robustness
        for (i in -1..1) {
            if (calculateOTP(secret, currentTimeStep + i) == code) {
                return true
            }
        }
        return false
    }

    private fun calculateOTP(secret: String, timeStep: Long): String {
        val key = secret.toByteArray()
        val data = ByteBuffer.allocate(8).putLong(timeStep).array()
        
        val hmac = Mac.getInstance("HmacSHA1")
        hmac.init(SecretKeySpec(key, "HmacSHA1"))
        val hash = hmac.doFinal(data)
        
        val offset = hash[hash.size - 1].toInt() and 0xf
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
                     ((hash[offset + 1].toInt() and 0xff) shl 16) or
                     ((hash[offset + 2].toInt() and 0xff) shl 8) or
                     (hash[offset + 3].toInt() and 0xff)
        
        val otp = binary % 10.0.pow(6).toInt()
        return otp.toString().padStart(6, '0')
    }
}
