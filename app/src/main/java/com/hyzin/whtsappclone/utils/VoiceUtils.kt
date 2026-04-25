package com.hyzin.whtsappclone.utils

import android.content.Context
import android.media.MediaRecorder
import android.media.MediaPlayer
import android.util.Base64
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.IOException

object VoiceUtils {
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var mediaPlayer: MediaPlayer? = null

    fun startRecording(context: Context): Boolean {
        return try {
            audioFile = File(context.cacheDir, "temp_voice_${System.currentTimeMillis()}.m4a")
            mediaRecorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile?.absolutePath)
                prepare()
                start()
            }
            true
        } catch (e: Exception) {
            Log.e("VoiceUtils", "Failed to start recording", e)
            false
        }
    }

    fun stopRecording(): File? {
        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null

            audioFile?.let { file ->
                if (file.exists()) {
                    return file
                }
            }
            null
        } catch (e: Exception) {
            Log.e("VoiceUtils", "Failed to stop recording", e)
            null
        }
    }

    fun playBase64Audio(context: Context, base64Audio: String) {
        try {
            // Stop and release previous player if any
            mediaPlayer?.release()
            
            // Extract raw base64 if it's a data URI
            val pureBase64 = if (base64Audio.startsWith("data:")) {
                base64Audio.substringAfter("base64,")
            } else {
                base64Audio
            }
            
            val audioBytes = Base64.decode(pureBase64, Base64.DEFAULT)
            val tempFile = File(context.cacheDir, "play_voice_${System.currentTimeMillis()}.m4a")
            tempFile.writeBytes(audioBytes)
            
            mediaPlayer = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                prepare()
                start()
                setOnCompletionListener {
                    it.release()
                    mediaPlayer = null
                    tempFile.delete()
                }
            }
        } catch (e: Exception) {
            Log.e("VoiceUtils", "Failed to play audio", e)
        }
    }
}
