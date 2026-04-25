package com.hyzin.whtsappclone.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.hyzin.whtsappclone.MainActivity
import com.hyzin.whtsappclone.R
import java.net.URL

object NotificationUtils {
    
    // Global state to track currently open chat
    var currentActiveChatId: String? = null

    private const val GROUP_KEY_MESSAGES = "com.hyzin.wattshub.MESSAGES"
    private const val CHANNEL_ID = "chat_messages_v3"

    fun showChatNotification(
        context: Context,
        title: String,
        body: String,
        chatId: String,
        senderName: String,
        avatarUrl: String,
        isGroup: Boolean
    ) {
        Log.d("Notification", "Triggering notification for chatId: $chatId, sender: $senderName")
        
        // 1. Don't show notification if we are already in this chat (Loop prevention)
        if (chatId == currentActiveChatId) {
            Log.d("Notification", "Skipping notification: User is actively chatting in $chatId")
            return
        }

        val prefManager = PreferenceManager(context)
        if (!prefManager.getNotificationsEnabled()) return 

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("navigate_to", "chat/$chatId/$senderName/$isGroup")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 
            chatId.hashCode(), 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        
        // 2. Build the message notification
        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(senderName)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setGroup(GROUP_KEY_MESSAGES) // Grouping

        // Always play sound as requested
        notificationBuilder.setSound(soundUri)
        try {
            val r = RingtoneManager.getRingtone(context, soundUri)
            r.play()
            Log.d("Notification", "Sound played manually for message from $senderName")
        } catch (e: Exception) {
            Log.e("Notification", "Error playing sound", e)
        }

        // Handle Avatar
        if (avatarUrl.isNotEmpty()) {
            try {
                val bitmap = if (avatarUrl.startsWith("data:image")) {
                    decodeBase64(avatarUrl)
                } else {
                    getBitmapFromUrl(avatarUrl)
                }
                if (bitmap != null) {
                    notificationBuilder.setLargeIcon(bitmap)
                }
            } catch (e: Exception) {
                Log.e("Notification", "Error loading avatar", e)
            }
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 3. Create Channel (Android O+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, 
                "Chat Messages", 
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Instantly notified of new messages"
                enableVibration(true)
                setSound(soundUri, null)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        // 4. Show Notification
        notificationManager.notify(chatId.hashCode(), notificationBuilder.build())
        
        // Show debug toast (Temporary per requirement 11)
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, "New Message from $senderName", Toast.LENGTH_SHORT).show()
        }

        // 5. Build and Show Summary Notification (WhatsApp style grouping)
        val summaryNotification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("WattsHub")
            .setContentText("New messages")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(GROUP_KEY_MESSAGES)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(GROUP_KEY_MESSAGES.hashCode(), summaryNotification)
        
        Log.d("Notification", "Notification displayed for $senderName: $body")
    }

    fun cancelNotification(context: Context, chatId: String?) {
        if (chatId == null) return
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(chatId.hashCode())
        Log.d("Notification", "Cancelled notification for chatId: $chatId")
    }

    private fun decodeBase64(base64Str: String): Bitmap? {
        return try {
            val pureBase64 = base64Str.substringAfter(",")
            val decodedBytes = Base64.decode(pureBase64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            null
        }
    }

    private fun getBitmapFromUrl(url: String): Bitmap? {
        return try {
            val inputStream = URL(url).openStream()
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            null
        }
    }
}
