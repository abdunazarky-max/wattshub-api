package com.hyzin.whtsappclone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject

/**
 * CallActionReceiver handles notification button clicks.
 */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val receivedAction = intent.action ?: return
        val chatId = intent.getStringExtra("chatId") ?: return
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val receiverId = chatId.split("_").firstOrNull { it != currentUserId } ?: return
        val rawName = intent.getStringExtra("callerName")
        val callerName = if (rawName.isNullOrBlank()) "User" else rawName
        
        try {
            when (receivedAction) {
                "ANSWER_CALL" -> {
                    val isVideo = intent.getBooleanExtra("isVideo", false)
                    
                    // ✅ Update Firestore status IMMEDIATELY to stop all listeners and inform the caller
                    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    db.collection("signaling_calls").document(chatId).update("status", "answered")

                    // Stop ringtone service via action for consistency and reliability
                    context.startService(Intent(context, CallNotificationService::class.java).apply { action = "STOP_SERVICE" })

                    // ✅ Signal auto-answer for CallScreen via BOTH static flag and Route parameter (Extra redundancy)
                    WebRTCSessionManager.shouldAutoAnswer = true

                    // Launch Activity with full route (6 parts)
                    val encodedChatId = java.net.URLEncoder.encode(chatId, "UTF-8")
                    val encodedCallerName = java.net.URLEncoder.encode(callerName, "UTF-8")
                    val mainIntent = Intent(context, MainActivity::class.java).apply {
                        putExtra("navigate_to", "call/$encodedChatId/$isVideo/true/$encodedCallerName/true")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    context.startActivity(mainIntent)
                }
            "DECLINE_CALL" -> {
                // ... same as before but explicitly log decline ...
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                db.collection("signaling_calls").document(chatId).update("status", "declined")
                context.startService(Intent(context, CallNotificationService::class.java).apply { action = "STOP_SERVICE" })
                saveCallMessageToChat(context, chatId, currentUserId, receiverId, "declined")
            }
            "END_CALL" -> {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                db.collection("signaling_calls").document(chatId).update("status", "ended")
                context.startService(Intent(context, CallNotificationService::class.java).apply { action = "STOP_SERVICE" })
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("CallActionReceiver", "❌ Error processing call action: ${e.message}", e)
    }
}

    private fun saveCallMessageToChat(context: Context, chatId: String, currentUserId: String, receiverId: String, status: String) {
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val isVideo = false // Receiver doesn't always know, default to voice for log or handle via intent
        
        val msgText = when(status) {
            "missed" -> "Missed voice call"
            "declined" -> "Declined voice call"
            "ended" -> "Call ended"
            else -> "Call ended"
        }

        val messageData = hashMapOf(
            "sender_id" to receiverId,
            "receiver_id" to currentUserId,
            "text" to msgText,
            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "type" to "call",
            "status" to "sent",
            "callStatus" to status
        )

        db.collection("messages").document(chatId).collection("contents").add(messageData)
        
        // Update conversation meta
        db.collection("conversations").document(chatId).update(
            "lastMessage", msgText,
            "lastMessageTimestamp", com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
    }

    companion object {
        var lastHandledCallId: String? = null
    }
}
