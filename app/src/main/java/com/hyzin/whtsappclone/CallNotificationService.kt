package com.hyzin.whtsappclone

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.*
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * CallNotificationService manages the high-priority ringtone and foreground notification
 * during an active or incoming call.
 */
class CallNotificationService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var signalingListener: com.google.firebase.firestore.ListenerRegistration? = null
    private val CHANNEL_ID = "incoming_calls_v2"
    private val NOTIFICATION_ID = 2024
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        // 🛡️ Acquire WakeLock to keep CPU alive during incoming call alerts in sleep mode
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WattsHub:CallWakeLock")
        wakeLock?.acquire(30000) // Timeout matches the 30s ring limit
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "STOP_SERVICE") {
            stopSelf()
            return START_NOT_STICKY
        }

        val chatId = intent?.getStringExtra("chatId")
        val callerName = intent?.getStringExtra("callerName")?.takeIf { it.isNotBlank() } ?: "User"
        val isVideo = intent?.getBooleanExtra("isVideo", false) ?: false
        val isIncoming = intent?.getBooleanExtra("isIncoming", true) ?: true
        val callerAvatarUrl = intent?.getStringExtra("callerAvatarUrl") ?: ""

        if (chatId != null) {
            showNotification(chatId, callerName, isVideo, isIncoming)
            // ✅ Always start signaling listener to ensure notification cleanup if call ends remotely
            startSignalingListener(chatId)
            if (isIncoming) {
                startAlerts()
                
                // 🛡️ VERY IMPORTANT: Prevent infinite ringing with a 30s timeout
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    android.util.Log.w("CallService", "Call timeout after 30s, stopping ringtone.")
                    stopSelf()
                }, 30000)
            }
        } else {
            android.util.Log.e("CallService", "startCommand: chatId is null, stopping service")
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun showNotification(chatId: String, callerName: String, isVideo: Boolean, isIncoming: Boolean) {
        // 1. Full-screen intent for heads-up — MUST URL-encode chatId to avoid NavHost crash
        val encodedChatId = java.net.URLEncoder.encode(chatId, "UTF-8")
        val encodedCallerName = java.net.URLEncoder.encode(callerName, "UTF-8")
        // ✅ Pass callerName in route so CallScreen shows real name immediately
        val targetRoute = if (isIncoming)
            "call/$encodedChatId/$isVideo/true/$encodedCallerName/false"
        else
            "call/$encodedChatId/$isVideo/false/$encodedCallerName/false"
        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("navigate_to", targetRoute)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or 
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(this, 10, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // 2. Action buttons
        val declineIntent = Intent(this, CallActionReceiver::class.java).apply {
            action = "DECLINE_CALL"
            putExtra("chatId", chatId)
            putExtra("callerName", callerName)
        }
        val declinePendingIntent = PendingIntent.getBroadcast(this, 11, declineIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val answerIntent = Intent(this, CallActionReceiver::class.java).apply {
            action = "ANSWER_CALL"
            putExtra("chatId", chatId)
            putExtra("isVideo", isVideo)
            putExtra("callerName", callerName)
        }
        val answerPendingIntent = PendingIntent.getBroadcast(this, 12, answerIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(if (isIncoming) "Incoming Call" else "Active Call")
            .setContentText(callerName)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setOngoing(true)
            .setAutoCancel(false)

        if (isIncoming) {
            builder.addAction(0, "Decline", declinePendingIntent)
            builder.addAction(0, "Answer", answerPendingIntent)
        } else {
            val endIntent = Intent(this, CallActionReceiver::class.java).apply {
                action = "END_CALL"
                putExtra("chatId", chatId)
            }
            val endPendingIntent = PendingIntent.getBroadcast(this, 13, endIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(0, "End Call", endPendingIntent)
        }

        val notification = builder.build()

        try {
            if (Build.VERSION.SDK_INT >= 34) {
                // FOREGROUND_SERVICE_TYPE_PHONE_CALL is 128
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e("CallService", "Failed to start foreground service: ${e.message}")
        }
    }

    private fun startAlerts() {
        try {
            // ✅ Find a valid ringtone with fallbacks
            val ringtoneUri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            mediaPlayer?.stop()
            mediaPlayer?.release()

            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@CallNotificationService, ringtoneUri)
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                isLooping = true
                prepare()
                start()
            }
            
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            val pattern = longArrayOf(0, 500, 1000)
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } catch (e: Exception) {
            Log.e("CallService", "Alert start error: ${e.message}", e)
        }
    }

    private fun startSignalingListener(chatId: String) {
        signalingListener?.remove()
        signalingListener = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("signaling_calls")
            .document(chatId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null || !snapshot.exists()) {
                    Log.d("CallService", "Signaling doc missing or deleted, stopping service.")
                    stopSelf()
                    return@addSnapshotListener
                }
                
                val status = snapshot.getString("status")
                val hasAnswer = snapshot.contains("answer")
                
                Log.d("CallService", "Signaling update - status: $status, hasAnswer: $hasAnswer")
                
                // ✅ Stop ringer if call state transitioned to terminal or active (answered elsewhere)
                if (status in listOf("answered", "ended", "declined", "cancelled") || hasAnswer) {
                    Log.i("CallService", "Call state changed to $status, stopping service.")
                    stopSelf()
                }
            }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Calls", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Incoming call notifications"
                setSound(null, null) // Sound handled by MediaPlayer for better control
                enableVibration(true)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        Log.d("CallService", "onDestroy: Cleaning up signaling and media.")
        try {
            stopForeground(true)
        } catch (e: Exception) {
            Log.e("CallService", "Error stopping foreground: ${e.message}")
        }
        
        signalingListener?.remove()

        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
        } catch (e: Exception) {
            Log.e("CallService", "Error stopping mediaPlayer", e)
        } finally {
            try {
                mediaPlayer?.release()
            } catch (e: Exception) {}
            mediaPlayer = null
        }

        try {
            vibrator?.cancel()
        } catch (e: Exception) {}
        vibrator = null
        
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        wakeLock = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
