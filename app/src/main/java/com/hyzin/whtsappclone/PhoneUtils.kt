
package com.hyzin.whtsappclone

object PhoneUtils {
    /**
     * Normalizes a phone number to E.164 format roughly.
     * Removes all non-digit characters except for the leading '+'.
     * If digits are provided without a leading '+', it adds '+' to ensure compatibility.
     */
    fun normalize(phone: String): String {
        val trimmed = phone.trim()
        if (trimmed.isEmpty()) return ""
        
        // Remove everything except digits and +
        val clean = trimmed.replace(Regex("[^0-9+]"), "")
        
        if (clean.startsWith("+")) {
            return clean
        }
        
        // If it starts with 00, replace with +
        if (clean.startsWith("00")) {
            return "+" + clean.substring(2)
        }
        
        // If it's just digits, we assume it's a full international number but missing '+'
        // Note: This is an assumption. In a real app, you'd use a country code picker.
        // But for "didn't find y" issues, missing '+' is a common culprit.
        return if (clean.isNotEmpty()) "+$clean" else ""
    }

    fun isProbablyPhone(query: String): Boolean {
        val clean = query.trim().replace(Regex("[^0-9+]"), "")
        return clean.length >= 7 && (clean.startsWith("+") || clean.all { it.isDigit() })
    }

    fun getDeviceId(context: android.content.Context): String {
        return android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "unknown_device"
    }
}
