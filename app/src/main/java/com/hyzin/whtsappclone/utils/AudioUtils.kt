package com.hyzin.whtsappclone.utils

import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/**
 * AudioUtils provides version-safe methods for audio routing.
 * Handles the deprecation of [AudioManager.isSpeakerphoneOn] in API 31+.
 */
object AudioUtils {
    fun setSpeakerphoneOn(audioManager: AudioManager, on: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val devices = audioManager.availableCommunicationDevices
            val speakerDevice = devices.find { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            if (on) {
                speakerDevice?.let { audioManager.setCommunicationDevice(it) }
            } else {
                audioManager.clearCommunicationDevice()
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = on
        }
    }
}
