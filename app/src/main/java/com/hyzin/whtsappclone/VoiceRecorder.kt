package com.hyzin.whtsappclone

import android.content.Context
import android.media.MediaRecorder
import java.io.File
import java.io.IOException

class VoiceRecorder(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null

    fun startRecording(): File? {
        audioFile = File(context.cacheDir, "audio_record_${System.currentTimeMillis()}.m4a")
        
        mediaRecorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        return try {
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile?.absolutePath)
                prepare()
                start()
            }
            audioFile
        } catch (e: Exception) {
            android.util.Log.e("VoiceRecorder", "Start recording failed: ${e.message}")
            null
        }
    }

    fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            android.util.Log.d("VoiceRecorder", "Successfully stopped recording. File saved: ${audioFile?.absolutePath}")
        } catch (e: Exception) {
            android.util.Log.e("VoiceRecorder", "🛑 Stop recording failed (likely too short): ${e.message}")
        } finally {
            mediaRecorder = null
        }
    }

    fun cancelRecording() {
        stopRecording()
        audioFile?.delete()
        audioFile = null
    }
}
