package com.hyzin.whtsappclone.utils

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.io.File

object AppProtection {

    /**
     * Checks if the device is rooted.
     */
    fun isDeviceRooted(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        for (path in paths) {
            if (File(path).exists()) return true
        }
        
        // Additional check for test-keys
        val buildTags = Build.TAGS
        if (buildTags != null && buildTags.contains("test-keys")) return true
        
        return false
    }

    /**
     * Checks if the app is being run on an emulator.
     */
    fun isRunningOnEmulator(): Boolean {
        val buildModel = Build.MODEL
        val buildHardware = Build.HARDWARE
        val buildFingerprint = Build.FINGERPRINT
        val buildProduct = Build.PRODUCT
        val buildManufacturer = Build.MANUFACTURER
        val buildBrand = Build.BRAND
        val buildDevice = Build.DEVICE

        var isEmulator = (buildBrand.startsWith("generic") && buildDevice.startsWith("generic"))
                || buildFingerprint.startsWith("generic")
                || buildHardware.contains("goldfish")
                || buildHardware.contains("ranchu")
                || buildModel.contains("google_sdk")
                || buildModel.contains("Emulator")
                || buildModel.contains("Android SDK built for x86")
                || buildManufacturer.contains("Genymotion")
                || buildProduct.contains("sdk_google")
                || buildProduct.contains("google_sdk")
                || buildProduct.contains("sdk")
                || buildProduct.contains("sdk_x86")
                || buildProduct.contains("vbox86p")
                || buildProduct.contains("emulator")
                || buildProduct.contains("simulator")

        // Refined check: physical devices might have 'unknown' fingerprint but they have a valid manufacturer/brand
        if (buildFingerprint.startsWith("unknown") && (buildManufacturer.equals("unknown", ignoreCase = true) || buildBrand.equals("unknown", ignoreCase = true))) {
            isEmulator = true
        }

        return isEmulator
    }

    /**
     * Checks if USB debugging is enabled.
     */
    fun isDebugModeEnabled(context: Context): Boolean {
        return Settings.Secure.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) != 0
    }
}
