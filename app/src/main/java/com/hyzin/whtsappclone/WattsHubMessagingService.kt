package com.hyzin.whtsappclone

import android.os.PowerManager
import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.hyzin.whtsappclone.utils.NotificationHelper

class WattsHubMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        val data = remoteMessage.data
        val notification = remoteMessage.notification
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        
        Log.d("WattsHubDiagnostics", "--- FCM RECEIVED (Foreground) ---")
        Log.d("WattsHubDiagnostics", "Data: $data")
        Log.d("WattsHubDiagnostics", "Notification: ${notification?.body}")

        val senderId = data["senderId"] ?: data["callerId"] ?: ""
        if (senderId == currentUserId && senderId.isNotEmpty()) {
            Log.w("WattsHubDiagnostics", "Suppressed: Self-notification")
            return
        }

        // 🛡️ Special Case: Incoming Call
        if (data["type"] == "call") {
            android.util.Log.d("WattsHubNotify", "Incoming call from ${data["callerName"]}")
            
            // 🛡️ WakeLock to ensure service starts in sleep mode
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WattsHub:FCMCallWake")
            wakeLock.acquire(5000) // 5 seconds is enough to start the service

            val callIntent = android.content.Intent(this, CallNotificationService::class.java).apply {
                putExtra("chatId", data["chatId"])
                putExtra("callerName", data["callerName"])
                putExtra("isVideo", data["isVideo"] == "true")
                putExtra("callerAvatarUrl", data["callerAvatarUrl"])
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(callIntent)
            } else {
                startService(callIntent)
            }
            return
        }

        // 🛡️ Special Case: Missed Call
        if (data["type"] == "missed_call") {
            NotificationHelper.showNotification(
                this, "missed_call", "Missed Call", "You missed a call from ${data["callerName"]}", 
                data["chatId"] ?: "", "", false
            )
            return
        }

        // 1. Get Title & Body (Priority to notification block)
        val title = notification?.title ?: data["senderName"] ?: "New Message"
        val body = notification?.body ?: data["message"] ?: data["body"] ?: ""
        
        // 2. Get Routing Data
        val chatId = data["chatId"] ?: "general_notify"
        val isGroup = data["isGroup"] == "true"
        val avatarUrl = data["avatarUrl"] ?: data["senderPic"]

        // 3. Show Notification
        NotificationHelper.showNotification(
            this, senderId, title, body, chatId, avatarUrl, isGroup
        )
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("WattsHubNotify", "New FCM Token: $token")
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            FirebaseFirestore.getInstance().collection("users").document(uid)
                .update("fcmToken", token)
        }
    }
}
