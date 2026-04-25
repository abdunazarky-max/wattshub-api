package com.hyzin.whtsappclone.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import com.hyzin.whtsappclone.MainActivity
import com.hyzin.whtsappclone.R

object NotificationHelper {
    private const val CHANNEL_ID = "chat_messages_v6" // Force update 🚀
    private const val OLD_CHANNEL_ID = "chat_messages_v5"
    private const val GROUP_KEY = "com.hyzin.wattshub.MESSAGES"

    fun showNotification(
        context: Context,
        senderId: String,
        senderName: String,
        message: String,
        chatId: String,
        avatarUrl: String? = null,
        isGroup: Boolean = false
    ) {
        Log.d("WattsHubNotify", "Notification Triggered: From $senderName, Chat: $chatId")

        val appContext = context.applicationContext
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Ensure channel exists with highest priority
        createChannel(notificationManager)

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

        // 🚀 Android 9+ (Pie) Style: MessagingStyle for native conversation look
        val person = Person.Builder()
            .setName(senderName)
            .setKey(senderId)
            .build()

        val messagingStyle = NotificationCompat.MessagingStyle(person)
            .addMessage(message, System.currentTimeMillis(), person)
            .setConversationTitle(if (isGroup) senderName else null)
            .setGroupConversation(isGroup)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setStyle(messagingStyle)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setDefaults(NotificationCompat.DEFAULT_ALL) // Ensures sound/vibration defaults are active
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(pendingIntent)
            .setGroup(GROUP_KEY)

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < 33) {
            notificationManager.notify(chatId.hashCode(), builder.build())
            showSummaryNotification(context, notificationManager)
            Log.d("WattsHubNotify", "Notification shown for $senderName")
        } else {
            Log.w("WattsHubNotify", "Permission missing, cannot show notification")
        }
    }

    private fun createChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 🧹 Clean up ANY old channels to ensure fresh start
            manager.deleteNotificationChannel(OLD_CHANNEL_ID)
            manager.deleteNotificationChannel("chat_channel")
            manager.deleteNotificationChannel("incoming_messages") 
            manager.deleteNotificationChannel("chat_messages_v3")
            manager.deleteNotificationChannel("chat_messages_v4")
            manager.deleteNotificationChannel("chat_messages_v5")

            // 🚀 Create Fresh High-Priority Channel with FULL sound/vibration support
            val channel = NotificationChannel(
                CHANNEL_ID,
                "WattsHub Messages", // Premium name
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                    .build()
                setSound(soundUri, audioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500) // Stronger premium vibration
                description = "Premium real-time messaging alerts"
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setShowBadge(true)
                enableLights(true)
                lightColor = android.graphics.Color.BLUE
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun showSummaryNotification(context: Context, manager: NotificationManager) {
        val summary = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()
        manager.notify(GROUP_KEY.hashCode(), summary)
    }

    fun clearNotification(context: Context, chatId: String?) {
        if (chatId == null) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(chatId.hashCode())
        Log.d("WattsHubNotify", "Notification cleared for chatId: $chatId")
    }
}
