package com.hyzin.whtsappclone

import android.Manifest
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.hyzin.whtsappclone.ui.theme.*
import com.hyzin.whtsappclone.utils.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ── Data Model ──────────────────────────────────────────────────────────────
@Immutable
data class Message(
    val id: String = "",
    val text: String = "",
    val isSentByMe: Boolean,
    val time: String,
    val fullDate: String = "",
    val imageUrl: String? = null,
    val videoUrl: String? = null,
    val audioUrl: String? = null,
    val type: String = "text",
    val status: String = "sent",
    val senderName: String = "",
    val senderPic: String = "",
    val replyTo: String? = null,
    val uploadProgress: Float? = null // Add progress tracking
)

// ── Utility Components ───────────────────────────────────────────────────────
@Composable
fun DateHeader(date: String) {
    if (date.isEmpty()) return
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = SoftBlueGrayLight.copy(alpha = 0.8f),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 1.dp
        ) {
            Text(
                text = date,
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun MessageBubble(
    message: Message, 
    isGroup: Boolean = false,
    onVideoClick: (String) -> Unit = {}
) {
    var showReactions by remember { mutableStateOf(false) }
    val isMe = message.isSentByMe
    
    // Theme-aware bubble colors
    val bubbleColor = if (isMe) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    
    val contentColor = if (isMe) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    
    val shape = if (isMe) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
    }

    // Remove heavy entrance animations for faster scrolling
    if (message.type == "call") {
             Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                 Surface(
                     color = Color.Black.copy(alpha = 0.2f),
                     shape = RoundedCornerShape(12.dp),
                     border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                 ) {
                     Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                         val icon = when {
                             message.text.contains("Missed", true) -> Icons.AutoMirrored.Filled.CallMissed
                             message.text.contains("Video", true) -> Icons.Default.Videocam
                             else -> Icons.Default.Call
                         }
                         val iconColor = if (message.text.contains("Missed", true)) Color.Red.copy(alpha = 0.7f) else AppGreen
                         Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(16.dp))
                         Spacer(Modifier.width(8.dp))
                         Text(message.text, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                     }
                 }
             }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.Bottom
            ) {
            if (!isMe) {
                Surface(
                    modifier = Modifier.size(32.dp).clip(CircleShape).padding(end = 4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    if (message.senderPic.isNotEmpty()) {
                        AsyncImage(model = message.senderPic, contentDescription = null, contentScale = ContentScale.Crop)
                    } else {
                        Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray, modifier = Modifier.padding(4.dp))
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .widthIn(max = 260.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = { showReactions = true }
                        )
                    },
                color = bubbleColor,
                shape = shape,
                tonalElevation = 1.dp
            ) {
                if (showReactions) {
                    androidx.compose.ui.window.Popup(
                        alignment = Alignment.TopCenter,
                        onDismissRequest = { showReactions = false }
                    ) {
                        Surface(
                            modifier = Modifier
                                .padding(bottom = 8.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 8.dp
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                listOf("👍", "❤️", "😂", "😮", "😢", "🙏").forEach { emoji ->
                                    Text(
                                        text = emoji,
                                        fontSize = 24.sp,
                                        modifier = Modifier.clickable { showReactions = false }
                                    )
                                }
                            }
                        }
                    }
                }
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (isGroup && !isMe) {
                        Text(
                            text = message.senderName,
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }

                    when (message.type) {
                        "text" -> {
                            Text(
                                text = message.text, 
                                color = contentColor, 
                                fontSize = 16.sp,
                                lineHeight = 20.sp
                            )
                        }
                        "image" -> {
                            AsyncImage(
                                model = message.imageUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        "video" -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black)
                                    .clickable { message.videoUrl?.let { onVideoClick(it) } },
                                contentAlignment = Alignment.Center
                            ) {
                                // For video thumbnails, we could use a placeholder or extract one.
                                // For now, use a generic video icon background or the imageUrl if available as a thumbnail.
                                if (message.imageUrl != null) {
                                    AsyncImage(model = message.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                }
                                Icon(
                                    Icons.Default.PlayArrow, 
                                    contentDescription = "Play", 
                                    tint = Color.White, 
                                    modifier = Modifier.size(48.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape).padding(8.dp)
                                )
                                // Show video duration or label
                                Text("Video", color = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp), fontSize = 10.sp)
                            }
                        }
                        "audio" -> {
                            VoiceMessagePlayer(audioUrl = message.audioUrl ?: "")
                        }
                    }

                    if (message.uploadProgress != null && message.uploadProgress < 1f) {
                        LinearProgressIndicator(
                            progress = { message.uploadProgress },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            color = VibrantGreenAction,
                            trackColor = Color.White.copy(alpha = 0.2f)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = message.time,
                            color = contentColor.copy(alpha = 0.7f)
                        )
                        if (isMe) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = if (message.status == "read") Icons.Default.DoneAll else Icons.Default.Done,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (message.status == "read") VibrantGreenAction else contentColor.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    mediaPicker: androidx.activity.result.ActivityResultLauncher<String>,
    onCameraClick: () -> Unit,
    isRecording: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    recordingTime: Int,
    onAiClick: () -> Unit
) {
    Surface(
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(28.dp),
                color = if (isSystemInDarkTheme()) Color(0xFF1F2C34) else Color.White,
                tonalElevation = 0.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    if (isRecording) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null, tint = Color.Red, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = String.format(Locale.getDefault(), "%02d:%02d", recordingTime / 60, recordingTime % 60),
                                color = if (isSystemInDarkTheme()) Color.White else Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(Modifier.width(16.dp))
                            Text("Recording...", color = Color.Gray, fontSize = 15.sp)
                        }
                    } else {
                        IconButton(onClick = { /* Emoji picker placeholder */ }, modifier = Modifier.size(40.dp)) {
                            Icon(
                                Icons.Default.SentimentSatisfiedAlt, 
                                contentDescription = "Emoji", 
                                tint = Color.Gray,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        IconButton(onClick = onAiClick, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.AutoAwesome, 
                                contentDescription = "AI Magic", 
                                tint = SkyBlueAccent, 
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        BasicTextField(
                            value = text,
                            onValueChange = onTextChange,
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = if (isSystemInDarkTheme()) Color.White else Color.Black,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            cursorBrush = Brush.verticalGradient(listOf(AppGreen, AppGreen)),
                            decorationBox = { innerTextField ->
                                if (text.isEmpty()) {
                                    Text(
                                        "Message", 
                                        color = Color.Gray,
                                        fontSize = 17.sp
                                    )
                                }
                                innerTextField()
                            }
                        )

                        IconButton(onClick = { mediaPicker.launch("*/*") }, modifier = Modifier.size(40.dp)) {
                            Icon(
                                Icons.Default.AttachFile, 
                                contentDescription = "Attach", 
                                tint = Color.Gray, 
                                modifier = Modifier.size(24.dp).rotate(-45f)
                            )
                        }

                        IconButton(onClick = onCameraClick, modifier = Modifier.size(40.dp)) {
                            Icon(
                                Icons.Default.CameraAlt, 
                                contentDescription = "Camera", 
                                tint = Color.Gray,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(6.dp))

            Surface(
                onClick = { 
                    if (text.isNotBlank()) onSend() 
                    else if (isRecording) onStopRecording() 
                    else onStartRecording()
                },
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = if (text.isNotBlank()) AppGreen else Color.White,
                tonalElevation = 0.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (text.isNotBlank()) Icons.AutoMirrored.Filled.Send else if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = "Action",
                        tint = if (text.isNotBlank()) Color.White else Color(0xFF121B22),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun VoiceMessagePlayer(audioUrl: String) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        IconButton(onClick = { 
            if (audioUrl.startsWith("http")) {
                // If it's a URL, use standard MediaPlayer logic (could be improved with a shared player)
                MediaPlayer().apply {
                    setDataSource(audioUrl)
                    prepareAsync()
                    setOnPreparedListener { start() }
                    setOnCompletionListener { isPlaying = false; release() }
                }
            } else {
                VoiceUtils.playBase64Audio(context, audioUrl)
            }
            isPlaying = true
        }) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, 
                contentDescription = "Play", 
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        
        // Visualizer placeholder
        Row(
            modifier = Modifier.weight(1f).height(20.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(15) { _ ->
                val height = remember { (5..15).random() }.dp
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(height)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), RoundedCornerShape(1.dp))
                )
            }
        }
        
        Spacer(Modifier.width(8.dp))
        Text("Voice", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    contactId: String,
    contactName: String,
    isGroup: Boolean = false,
    capturedImageUri: String? = null,
    onImageUploaded: () -> Unit = {},
    onBack: () -> Unit,
    onNavigateToCall: (Boolean, String, String) -> Unit = { _, _, _ -> },
    onNavigateToCamera: () -> Unit = {},
    onNavigateToVideoPlayer: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    
    // Permissions for Audio
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, "Microphone permission required for voice messages", Toast.LENGTH_SHORT).show()
        }
    }
    var messageText by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<Message>() }

    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val listState = rememberLazyListState()
    val currentUserId = auth.currentUser?.uid ?: ""

    // Voice Recording States
    var isRecording by remember { mutableStateOf(false) }
    var recordingTime by remember { mutableIntStateOf(0) }
    
    // Fetch Profile Info
    var contactPicUrl by remember { mutableStateOf("") }
    var contactBio by remember { mutableStateOf("") }
    var contactEmail by remember { mutableStateOf("") }
    var isOnline by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showFullScreenImage by remember { mutableStateOf<String?>(null) }
    var isContactBlocked by remember { mutableStateOf(false) }
    var contactLastSeen by remember { mutableStateOf<com.google.firebase.Timestamp?>(null) }

    // Typing Indicator State
    var isContactTyping by remember { mutableStateOf(false) }
    var typingDebounceJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var isSelfTypingActive by remember { mutableStateOf(false) }

    val chatId = if (isGroup) contactId else {
        if (currentUserId < contactId) "${currentUserId}_$contactId" else "${contactId}_$currentUserId"
    }

    val optimisticPendingMessages = remember { mutableStateListOf<Message>() }
    
    // Pagination States
    var lastDocument by remember { mutableStateOf<com.google.firebase.firestore.DocumentSnapshot?>(null) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var isEndReached by remember { mutableStateOf(false) }

    // Helper: Convert Firestore doc to Message model
    fun docToMessage(doc: com.google.firebase.firestore.DocumentSnapshot): Message {
        val data = doc.data ?: emptyMap<String, Any>()
        val timestamp = doc.getTimestamp("timestamp")?.toDate()
        val senderId = data["sender_id"] as? String ?: ""
        return Message(
            id = doc.id,
            text = data["text"] as? String ?: "",
            isSentByMe = senderId == currentUserId,
            time = if (timestamp != null) SimpleDateFormat("hh:mm a", Locale.getDefault()).format(timestamp) else "...",
            fullDate = if (timestamp != null) SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(timestamp) else "",
            type = data["type"] as? String ?: "text",
            imageUrl = data["imageUrl"] as? String,
            videoUrl = data["videoUrl"] as? String,
            audioUrl = data["audioUrl"] as? String,
            status = data["status"] as? String ?: "sent",
            senderName = data["sender_name"] as? String ?: "Other",
            senderPic = data["sender_pic"] as? String ?: ""
        )
    }

    // Function to load older messages
    fun loadMoreMessages() {
        if (isLoadingMore || isEndReached || lastDocument == null) return
        isLoadingMore = true
        Log.d("ChatDetail", "🔄 Loading more messages...")
        
        val collection = if (isGroup) {
            db.collection("groups").document(chatId).collection("messages")
        } else {
            db.collection("messages").document(chatId).collection("contents")
        }
        
        collection.orderBy("timestamp", Query.Direction.DESCENDING)
            .startAfter(lastDocument!!)
            .limit(25)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    isEndReached = true
                } else {
                    lastDocument = snapshot.documents.lastOrNull()
                    val newMessages = snapshot.documents.map { docToMessage(it) }
                    messages.addAll(newMessages)
                }
                isLoadingMore = false
            }
            .addOnFailureListener {
                isLoadingMore = false
            }
    }

    // Helper: Update global typing status for Home Screen
    fun updateTypingStatus(state: String?) {
        if (currentUserId.isNotEmpty() && !isGroup) {
            Log.d("TypingStatus", "Updating global status for $currentUserId: $state in $chatId")
            val userRef = db.collection("users").document(currentUserId)
            if (state == null) {
                userRef.update(mapOf(
                    "typingChatId" to com.google.firebase.firestore.FieldValue.delete(),
                    "typingState" to com.google.firebase.firestore.FieldValue.delete()
                )).addOnFailureListener { Log.e("TypingStatus", "Global clear failed: ${it.message}") }
            } else {
                userRef.update(mapOf(
                    "typingChatId" to chatId,
                    "typingState" to state
                )).addOnFailureListener { Log.e("TypingStatus", "Global update failed: ${it.message}") }
            }
        }
    }

    // Helper for sending media via Storage
    fun sendMediaMessage(uri: Uri, type: String) {
        val senderName = auth.currentUser?.displayName ?: "User"
        val senderPic = auth.currentUser?.photoUrl?.toString() ?: ""
        val timestamp = System.currentTimeMillis()
        val extension = if (type == "image") "jpg" else if (type == "video") "mp4" else "m4a"
        val fileName = "${timestamp}.$extension"
        
        val storageRef = FirebaseStorage.getInstance().reference.child("chat_media/$chatId/$fileName")
        
        // 1. Optimistic UI with Progress
        val tempId = "temp_media_$timestamp"
        val tempMsg = Message(
            id = tempId,
            text = if (type == "image") "📷 Photo" else if (type == "video") "🎥 Video" else "🎤 Voice Message",
            isSentByMe = true,
            time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()),
            fullDate = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(Date()),
            type = type,
            imageUrl = if (type == "image") uri.toString() else null,
            videoUrl = if (type == "video") uri.toString() else null,
            audioUrl = if (type == "audio") uri.toString() else null,
            status = "uploading...",
            senderName = senderName,
            senderPic = senderPic,
            uploadProgress = 0.01f
        )
        optimisticPendingMessages.add(0, tempMsg)

        // 2. Upload to Storage
        storageRef.putFile(uri).addOnProgressListener { taskSnapshot ->
            val progress = taskSnapshot.bytesTransferred.toFloat() / taskSnapshot.totalByteCount.toFloat()
            val index = optimisticPendingMessages.indexOfFirst { it.id == tempId }
            if (index != -1) {
                optimisticPendingMessages[index] = optimisticPendingMessages[index].copy(uploadProgress = progress)
            }
        }.addOnSuccessListener {
            storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                val downloadUrl = downloadUri.toString()
                
                // 3. Save to Firestore
                val messageData = hashMapOf(
                    "sender_id" to currentUserId,
                    "receiver_id" to contactId,
                    "text" to (if (type == "image") "📷 Photo" else if (type == "video") "🎥 Video" else "🎤 Voice Message"),
                    "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "type" to type,
                    "status" to "sent",
                    "sender_name" to senderName,
                    "sender_pic" to senderPic
                )
                if (type == "image") messageData["imageUrl"] = downloadUrl
                if (type == "video") messageData["videoUrl"] = downloadUrl
                if (type == "audio") messageData["audioUrl"] = downloadUrl

                val collection = if (isGroup) {
                    db.collection("groups").document(chatId).collection("messages")
                } else {
                    db.collection("messages").document(chatId).collection("contents")
                }

                collection.add(messageData).addOnSuccessListener {
                    // Remove optimistic message
                    optimisticPendingMessages.removeAll { it.id == tempId }
                    
                    // Update conversation meta with denormalized data
                    val conversationUpdate = hashMapOf(
                        "lastMessage" to (if (type == "image") "📷 Photo" else if (type == "video") "🎥 Video" else "🎤 Voice Message"),
                        "lastMessageTimestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                        "participantIds" to if (isGroup) emptyList() else listOf(currentUserId, contactId)
                    )
                    
                    if (!isGroup) {
                        conversationUpdate["participantInfo"] = hashMapOf(
                            currentUserId to hashMapOf(
                                "name" to senderName, 
                                "pic" to senderPic
                            ),
                            contactId to hashMapOf(
                                "name" to contactName, 
                                "pic" to contactPicUrl,
                                "email" to contactEmail,
                                "bio" to contactBio,
                                "phone" to (if (contactId.length > 5) contactId else "") // Assuming contactId is phone if long
                            )
                        )
                    } else {
                        conversationUpdate["lastSenderName"] = senderName
                    }

                    db.collection("conversations").document(chatId).set(
                        conversationUpdate,
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                }

                // 4. Relay via Socket
                val socketData = org.json.JSONObject().apply {
                    put("senderId", currentUserId)
                    put("receiverId", contactId)
                    put("text", if (type == "image") "📷 Photo" else if (type == "video") "🎥 Video" else "🎤 Voice Message")
                    put("chatId", chatId)
                    put("type", type)
                    if (type == "image") put("imageUrl", downloadUrl)
                    if (type == "video") put("videoUrl", downloadUrl)
                    if (type == "audio") put("audioUrl", downloadUrl)
                    put("isGroup", isGroup)
                    put("senderName", senderName)
                    put("senderPic", senderPic)
                }
                SocketClient.sendMessage(socketData)
            }
        }.addOnFailureListener { uploadError ->
            Toast.makeText(context, "Upload failed: ${uploadError.message}", Toast.LENGTH_SHORT).show()
            optimisticPendingMessages.removeAll { it.id == tempId }
        }
    }


    // 1. Manage Active Chat ID for Notifications
    DisposableEffect(chatId) {
        NotificationUtils.currentActiveChatId = chatId
        // Clear any existing notification for this chat
        NotificationHelper.clearNotification(context, chatId)
        
        onDispose {
            NotificationUtils.currentActiveChatId = null
        }
    }

    // 1.1 Socket Room Management
    LaunchedEffect(chatId, isGroup) {
        if (isGroup) {
            SocketClient.joinGroup(chatId)
        }
    }


    // 2. Fetch Profile Info & Messages
    DisposableEffect(contactId, chatId) {
        // Clear unread counts for this chat (non-suspending delete)
        if (currentUserId.isNotEmpty()) {
            db.collection("users").document(currentUserId).collection("unread_counts").document(chatId).delete()
        }

        // Listen for contact state
        val contactListener = db.collection("users").document(contactId).addSnapshotListener { snap, _ ->
            if (snap != null) {
                contactPicUrl = snap.getString("profilePicUrl") ?: ""
                contactBio = snap.getString("bio") ?: "Available"
                contactEmail = snap.getString("email") ?: "N/A"
                isOnline = snap.getString("status") == "online"
                contactLastSeen = snap.getTimestamp("lastSeen")
                Log.d("PresenceStatus", "Contact: $contactId, Online: $isOnline, LastSeen: ${contactLastSeen?.toDate()}")
            } else {
                Log.w("PresenceStatus", "Contact snapshot is null for $contactId")
            }
        }
        
        // Listen for messages (Optimized with pagination and incremental updates)
        val collection = if (isGroup) {
            db.collection("groups").document(chatId).collection("messages")
        } else {
            db.collection("messages").document(chatId).collection("contents")
        }
        
        val messageListener = collection.orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50) 
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    // Update lastDocument for pagination on initial load
                    if (lastDocument == null && snapshot.documents.isNotEmpty()) {
                        lastDocument = snapshot.documents.lastOrNull()
                    }

                    for (docChange in snapshot.documentChanges) {
                        val doc = docChange.document
                        val msg = docToMessage(doc)
                        
                        when (docChange.type) {
                            com.google.firebase.firestore.DocumentChange.Type.ADDED -> {
                                val index = messages.indexOfFirst { it.id == msg.id }
                                if (index == -1) {
                                    optimisticPendingMessages.removeAll { it.text == msg.text && it.isSentByMe }
                                    messages.add(docChange.newIndex, msg)
                                }
                            }
                            com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                                val index = messages.indexOfFirst { it.id == msg.id }
                                if (index != -1) {
                                    messages[index] = msg
                                }
                            }
                            com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                                messages.removeAll { it.id == msg.id }
                            }
                        }

                        if (msg.isSentByMe.not() && msg.status != "read") {
                            doc.reference.update("status", "read")
                        }
                    }
                }
            }

        // Cleanup listeners when effect is removed
        onDispose {
            contactListener.remove()
            messageListener.remove()
        }
    }

    // 🚀 3. INSTANT SOCKET RELAY: Listen for real-time messages directly
    LaunchedEffect(chatId) {
        SocketClient.chatMessages.collect { msg ->
            val incomingChatId = msg["chatId"] ?: ""
            val senderId = msg["senderId"] ?: ""
            
            // Only process if it belongs to this active chat and is NOT from me 
            // (since I handled my own message optimistically)
            if (incomingChatId == chatId && senderId != currentUserId) {
                val text = msg["text"] ?: ""
                
                // Avoid double insertion if Firestore beat the socket (rare but possible)
                val isDuplicate = messages.any { it.text == text && !it.isSentByMe }
                if (!isDuplicate) {
                    val newMsg = Message(
                        id = "socket_${System.currentTimeMillis()}",
                        text = text,
                        isSentByMe = false,
                        time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()),
                        fullDate = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(Date()),
                        type = msg["type"] ?: "text",
                        status = "received",
                        senderName = msg["senderName"] ?: "Contact",
                        senderPic = msg["senderPic"] ?: ""
                    )
                    // Add to top of reverse list
                    messages.add(0, newMsg)
                    Log.d("ChatDetail", "Instant message received via Socket: $text")
                }
            }
        }
    }

    LaunchedEffect(messages.size, optimisticPendingMessages.size) {
        if (messages.isNotEmpty() || optimisticPendingMessages.isNotEmpty()) {
            listState.animateScrollToItem(0)
            if (currentUserId.isNotEmpty()) {
                db.collection("users").document(currentUserId).collection("unread_counts").document(chatId).delete()
            }
        }
    }

    // Auto-hide keyboard on scroll
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            keyboardController?.hide()
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingTime = 0
            while (isRecording) {
                delay(1000)
                recordingTime++
            }
        }
    }

    // Typing Indicator Listener — listen to contact's typing status in Firestore
    DisposableEffect(chatId, contactId) {
        var typingListener: com.google.firebase.firestore.ListenerRegistration? = null
        isContactTyping = false // Reset on entry
        
        if (!isGroup && contactId.isNotEmpty() && currentUserId.isNotEmpty()) {
            val typingPath = "messages/$chatId/typing/$contactId"
            Log.d("TypingStatus", "Listening to: $typingPath")
            val typingRef = db.document(typingPath)
            typingListener = typingRef.addSnapshotListener { snap, error ->
                if (error != null) {
                    Log.e("TypingStatus", "Listen failed: ${error.message}")
                    return@addSnapshotListener
                }
                val typing = snap?.getBoolean("isTyping") ?: false
                Log.d("TypingStatus", "Update received: $typing (for $contactId)")
                isContactTyping = typing
            }
        }
        
        onDispose {
            typingListener?.remove()
            // Reset our own typing status when leaving the screen
            typingDebounceJob?.cancel()
            if (isSelfTypingActive || isRecording) {
                isSelfTypingActive = false
                isRecording = false
                updateTypingStatus(null)
                val typingPath = "messages/$chatId/typing/$currentUserId"
                Log.d("TypingStatus", "Dispose Reset: $typingPath = false")
                db.document(typingPath).set(mapOf("isTyping" to false))
            }
        }
    }

    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && currentUserId.isNotEmpty()) {
            val mimeType = context.contentResolver.getType(uri) ?: ""
            val type = if (mimeType.startsWith("video")) "video" else "image"
            sendMediaMessage(uri, type)
        }
    }

    // Handle captured image from camera
    LaunchedEffect(capturedImageUri) {
        if (capturedImageUri != null && currentUserId.isNotEmpty()) {
            val uri = capturedImageUri.toUri()
            sendMediaMessage(uri, "image")
            onImageUploaded() // Reset capturedImageUri in parent
        }
    }


    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            val topBarGradient = Brush.horizontalGradient(
                colors = listOf(OceanBlueSecondary, Color(0xFF041023))
            )
            
            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(60.dp).background(topBarGradient, RoundedCornerShape(30.dp)),
                    shape = RoundedCornerShape(30.dp),
                    color = Color.Transparent,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)
                    ) {
                        // Styled Back Button with circular shadow effect as seen in the image
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.15f))
                                .clickable { onBack() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack, 
                                contentDescription = "Back", 
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(Modifier.width(8.dp))

                        // Header Profile Section
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showProfileDialog = true }
                        ) {
                            Surface(
                                modifier = Modifier.size(38.dp).clip(CircleShape), 
                                color = Color.White.copy(alpha = 0.1f)
                            ) {
                                if (contactPicUrl.isNotEmpty()) {
                                    AsyncImage(model = contactPicUrl, contentDescription = null, contentScale = ContentScale.Crop)
                                } else {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.padding(6.dp))
                                }
                            }
                            
                            Spacer(Modifier.width(10.dp))
                            
                            Column(verticalArrangement = Arrangement.Center) {
                                Text(
                                    text = contactName, 
                                    fontSize = 17.sp, 
                                    fontWeight = FontWeight.Bold, 
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                
                                val subtitleText = when {
                                    isContactTyping -> "typing..."
                                    isOnline -> "🟢 Online"
                                    else -> formatLastSeen(contactLastSeen)
                                }
                                
                                AnimatedContent(
                                    targetState = subtitleText,
                                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                                    label = "typing_status"
                                ) { statusText ->
                                    Text(
                                        text = statusText,
                                        fontSize = 12.sp,
                                        color = if (isContactTyping || isOnline) AppGreen else Color.White.copy(alpha = 0.7f),
                                        fontWeight = if (isOnline) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        // Action Icons with white tint to match the pattern
                        IconButton(onClick = { 
                             try {
                                 onNavigateToCall(true, chatId, contactName)
                             } catch (_: Exception) {
                                 Toast.makeText(context, "Error starting call", Toast.LENGTH_SHORT).show()
                             }
                        }) {
                            Icon(Icons.Default.Videocam, contentDescription = "Video", tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        
                        IconButton(onClick = { 
                             try {
                                 onNavigateToCall(false, chatId, contactName)
                             } catch (_: Exception) {
                                 Toast.makeText(context, "Error starting call", Toast.LENGTH_SHORT).show()
                             }
                        }) {
                            Icon(Icons.Default.Call, contentDescription = "Call", tint = Color.White, modifier = Modifier.size(20.dp))
                        }

                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Clear Chat") },
                                    leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
                                    onClick = { 
                                        showMenu = false
                                        val collection = if (isGroup) {
                                            db.collection("groups").document(chatId).collection("messages")
                                        } else {
                                            db.collection("messages").document(chatId).collection("contents")
                                        }
                                        collection.get().addOnSuccessListener { snapshot ->
                                            if (snapshot.isEmpty) return@addOnSuccessListener
                                            val batch = db.batch()
                                            snapshot.documents.forEach { batch.delete(it.reference) }
                                            batch.commit().addOnSuccessListener {
                                                Toast.makeText(context, "Chat cleared", Toast.LENGTH_SHORT).show()
                                                messages.clear()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            Column(modifier = Modifier.imePadding()) {


                // The AI suggestion bar is removed to keep the 'top' area same size as the attached pic.
                // AI functionality is now triggered manually via the Sparkle icon in the text field.

                // AI Bar removed
                
                ChatInputBar(
                text = messageText,
                onTextChange = { newText ->
                    messageText = newText
                    if (!isGroup && currentUserId.isNotEmpty()) {
                        if (newText.isEmpty()) {
                            // User cleared the box — cancel debounce, immediately set false
                            typingDebounceJob?.cancel()
                            typingDebounceJob = null
                            if (isSelfTypingActive) {
                                isSelfTypingActive = false
                                updateTypingStatus(null)
                                val typingPath = "messages/$chatId/typing/$currentUserId"
                                Log.d("TypingStatus", "Clear Reset: $typingPath = false")
                                db.document(typingPath).set(mapOf("isTyping" to false))
                            }
                        } else {
                            // Write isTyping=true only on first keystroke of a typing session
                            if (!isSelfTypingActive) {
                                isSelfTypingActive = true
                                updateTypingStatus("typing")
                                val typingPath = "messages/$chatId/typing/$currentUserId"
                                Log.d("TypingStatus", "Attempting write: $typingPath = true")
                                db.document(typingPath)
                                    .set(mapOf("isTyping" to true))
                                    .addOnSuccessListener { Log.d("TypingStatus", "Write Success: $typingPath") }
                                    .addOnFailureListener { Log.e("TypingStatus", "Write Failed: ${it.message}") }
                            }
                            // Cancel previous debounce and restart the 1500ms countdown
                            typingDebounceJob?.cancel()
                            typingDebounceJob = coroutineScope.launch {
                                kotlinx.coroutines.delay(1500)
                                isSelfTypingActive = false
                                updateTypingStatus(null)
                                val typingPath = "messages/$chatId/typing/$currentUserId"
                                Log.d("TypingStatus", "Debounce timeout: $typingPath = false")
                                db.document(typingPath).set(mapOf("isTyping" to false))
                            }
                        }
                    }
                },
                onSend = {
                    val textToSend = messageText.trim()
                    if (textToSend.isNotEmpty()) {
                        val senderName = auth.currentUser?.displayName ?: "User"
                        val senderPic = auth.currentUser?.photoUrl?.toString() ?: ""
                        
                        Log.d("ChatDetail", "Sending message: $textToSend")

                        // 1. Optimistic UI updating
                        val tempMsg = Message(
                            id = "temp_${System.currentTimeMillis()}",
                            text = textToSend,
                            isSentByMe = true,
                            time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()),
                            fullDate = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(Date()),
                            type = "text",
                            status = "sending...",
                            senderName = senderName,
                            senderPic = senderPic
                        )
                        optimisticPendingMessages.add(0, tempMsg)
                        messageText = ""

                        // Reset typing status immediately on send
                        typingDebounceJob?.cancel()
                        typingDebounceJob = null
                        if (isSelfTypingActive) {
                            isSelfTypingActive = false
                            updateTypingStatus(null)
                            val typingPath = "messages/$chatId/typing/$currentUserId"
                            Log.d("TypingStatus", "Send Reset: $typingPath = false")
                            db.document(typingPath).set(mapOf("isTyping" to false))
                        }

                        // 2. Direct Firestore Write (Primary Source of Truth)
                        val messageData = hashMapOf(
                            "sender_id" to currentUserId,
                            "receiver_id" to contactId,
                            "text" to textToSend,
                            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                            "type" to "text",
                            "status" to "sent",
                            "sender_name" to senderName,
                            "sender_pic" to senderPic
                        )

                        val collection = if (isGroup) {
                            db.collection("groups").document(chatId).collection("messages")
                        } else {
                            db.collection("messages").document(chatId).collection("contents")
                        }

                        collection.add(messageData).addOnSuccessListener {
                            // 3. Update Conversation Meta for Home Screen with denormalized data
                            val conversationUpdate = hashMapOf(
                                "lastMessage" to textToSend,
                                "lastMessageTimestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                                "participantIds" to if (isGroup) emptyList() else listOf(currentUserId, contactId)
                            )
                            
                            if (!isGroup) {
                                conversationUpdate["participantInfo"] = hashMapOf(
                                    currentUserId to hashMapOf(
                                        "name" to senderName, 
                                        "pic" to senderPic
                                    ),
                                    contactId to hashMapOf(
                                        "name" to contactName, 
                                        "pic" to contactPicUrl,
                                        "email" to contactEmail,
                                        "bio" to contactBio,
                                        "phone" to (if (contactId.length > 5) contactId else "")
                                    )
                                )
                            } else {
                                conversationUpdate["lastSenderName"] = senderName
                            }

                            db.collection("conversations").document(chatId).set(
                                conversationUpdate,
                                com.google.firebase.firestore.SetOptions.merge()
                            )
                            
                            // 4. Update Recipient's Unread Count
                            if (!isGroup) {
                                db.collection("users").document(contactId)
                                  .collection("unread_counts").document(chatId).set(
                                    hashMapOf(
                                        "count" to com.google.firebase.firestore.FieldValue.increment(1),
                                        "lastMessage" to textToSend,
                                        "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                                    ),
                                    com.google.firebase.firestore.SetOptions.merge()
                                  )
                            }
                        }

                        // 5. Emit via Socket for signaling & push notifications
                        val socketData = org.json.JSONObject().apply {
                            put("senderId", currentUserId)
                            put("receiverId", contactId)
                            put("text", textToSend)
                            put("chatId", chatId)
                            put("isGroup", isGroup)
                            put("senderName", senderName)
                            put("senderPic", senderPic)
                            put("clientHandled", true)
                        }
                        SocketClient.sendMessage(socketData)
                    }
                },
                mediaPicker = mediaPicker,
                onCameraClick = onNavigateToCamera,
                isRecording = isRecording,
                onStartRecording = { 
                    coroutineScope.launch {
                        val started = VoiceUtils.startRecording(context)
                        if (started) {
                            isRecording = true
                            updateTypingStatus("voice")
                        }
                        else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onStopRecording = { 
                    isRecording = false
                    updateTypingStatus(null)
                    coroutineScope.launch {
                        val file = VoiceUtils.stopRecording()
                        if (file != null) {
                            sendMediaMessage(Uri.fromFile(file), "audio")
                        }
                    }
                },
                recordingTime = recordingTime,
                onAiClick = {
                    coroutineScope.launch {
                        if (messageText.isNotEmpty()) {
                            val corrected = GeminiService.correctGrammar(messageText)
                            if (corrected != null) messageText = corrected
                        } else {
                            // Trigger Magic Greeting manually if empty
                            val created = GeminiService.createSentence("a friendly greeting")
                            if (created != null) messageText = created
                        }
                    }
                }
                )
            }
        }
    ) { paddingValues ->
        // Trigger loadMore when scrolling near the end
        LaunchedEffect(listState) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                .collect { lastIndex ->
                    if (lastIndex != null && lastIndex >= (messages.size + optimisticPendingMessages.size) - 10) {
                        loadMoreMessages()
                    }
                }
        }

        Box(modifier = Modifier.fillMaxSize().padding(paddingValues).background(Color.Transparent)) {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { 
                            keyboardController?.hide() 
                        })
                    },
                contentPadding = PaddingValues(top = 80.dp, bottom = 100.dp),
                flingBehavior = ScrollableDefaults.flingBehavior() // Default is good, but can be customized
            ) {
                // 1. Optimistic messages (Top because reverseLayout=true)
                items(
                    items = optimisticPendingMessages,
                    key = { it.id }
                ) { message ->
                    MessageBubble(
                        message = message, 
                        isGroup = isGroup,
                        onVideoClick = onNavigateToVideoPlayer
                    )
                }

                // 2. Real messages
                itemsIndexed(
                    items = messages,
                    key = { _, message -> message.id }
                ) { index, message ->
                    val nextMessage = if (index + 1 < messages.size) messages[index + 1] else null
                    
                    MessageBubble(
                        message = message, 
                        isGroup = isGroup,
                        onVideoClick = onNavigateToVideoPlayer
                    )
                    
                    if (nextMessage == null || message.fullDate != nextMessage.fullDate) {
                        DateHeader(message.fullDate)
                    }
                }
            }
        }

        if (showProfileDialog) {
            AlertDialog(
                onDismissRequest = { showProfileDialog = false },
                containerColor = Color(0xFF1E1E1E),
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape)
                                .clickable { showFullScreenImage = contactPicUrl },
                            color = SoftBlueGrayLight
                        ) {
                            if (contactPicUrl.isNotEmpty()) AsyncImage(model = contactPicUrl, contentDescription = null, contentScale = ContentScale.Crop)
                            else Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(80.dp).padding(20.dp))
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(contactName, color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text(if (isOnline) "Online" else formatLastSeen(contactLastSeen), color = if (isOnline) VibrantGreenAction else TextSecondary, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Contact Details Section
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                            Text("Mobile", color = SkyBlueAccent, fontSize = 12.sp)
                            Text(if (contactId.contains("+") || contactId.length > 8) contactId else "N/A", color = Color.White, fontSize = 16.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("Email", color = SkyBlueAccent, fontSize = 12.sp)
                            Text(contactEmail, color = Color.White, fontSize = 14.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("Bio", color = SkyBlueAccent, fontSize = 12.sp)
                            Text(contactBio.ifEmpty { "Hey there! I am using WattsHub." }, color = Color.White, fontSize = 14.sp)
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 16.dp))
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(onClick = { onNavigateToCall(false, chatId, contactName); showProfileDialog = false }) { Icon(Icons.Default.Call, contentDescription = null, tint = VibrantGreenAction) }
                                Text("Call", color = TextSecondary, fontSize = 12.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(onClick = { onNavigateToCall(true, chatId, contactName); showProfileDialog = false }) { Icon(Icons.Default.Videocam, contentDescription = null, tint = VibrantGreenAction) }
                                Text("Video", color = TextSecondary, fontSize = 12.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(onClick = { 
                                    isContactBlocked = !isContactBlocked
                                    Toast.makeText(context, if (isContactBlocked) "Contact Blocked" else "Contact Unblocked", Toast.LENGTH_SHORT).show()
                                }) { 
                                    Icon(
                                        imageVector = if (isContactBlocked) Icons.Default.Block else Icons.Default.PersonOff, 
                                        contentDescription = "Block", 
                                        tint = if (isContactBlocked) Color.Red else VibrantGreenAction
                                    ) 
                                }
                                Text(if (isContactBlocked) "Unblock" else "Block", color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showProfileDialog = false }) {
                        Text("CLOSE", color = VibrantGreenAction)
                    }
                }
            )
        }

        if (showFullScreenImage != null) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { showFullScreenImage = null }) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f)).clickable { showFullScreenImage = null }) {
                    AsyncImage(
                        model = showFullScreenImage,
                        contentDescription = "Profile",
                        modifier = Modifier.fillMaxWidth().align(Alignment.Center),
                        contentScale = ContentScale.Fit
                    )
                    Row(
                        modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { 
                            showFullScreenImage?.let { url ->
                                ImageUtils.downloadImage(context, url, coroutineScope)
                            }
                        }) {
                            Icon(Icons.Default.Download, contentDescription = "Download", tint = Color.White)
                        }
                        IconButton(onClick = { showFullScreenImage = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }

                }
            }
        }

    }
}

private fun formatLastSeen(timestamp: com.google.firebase.Timestamp?): String {
    if (timestamp == null) return "last seen recently"
    val date = timestamp.toDate()
    val now = Calendar.getInstance()
    val time = Calendar.getInstance().apply { time = date }
    
    val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(date)
    
    return when {
        now.get(Calendar.YEAR) == time.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == time.get(Calendar.DAY_OF_YEAR) -> "last seen today at $timeStr"
        
        now.get(Calendar.YEAR) == time.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) - 1 == time.get(Calendar.DAY_OF_YEAR) -> "last seen yesterday at $timeStr"
        
        else -> "last seen " + SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(date)
    }
}


