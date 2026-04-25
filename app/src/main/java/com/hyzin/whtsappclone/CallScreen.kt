package com.hyzin.whtsappclone

import android.Manifest
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import com.hyzin.whtsappclone.utils.AudioUtils

@Composable
fun CallScreen(
    chatId: String,
    isVideo: Boolean,
    isIncoming: Boolean,
    callerName: String = "",  // ✅ Pre-populated from route — no waiting for Firestore
    shouldAutoAnswer: Boolean = false, // ✅ Directly from navigation route (immune to process restarts)
    onEndCall: () -> Unit
) {
    val context = LocalContext.current
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
        android.util.Log.e("CallScreen", "❌ No current user — cannot start call")
        onEndCall()
        return
    }
    // Correctly extract the other participant: split by '_' and find the part that isn't us
    val receiverId = chatId.split("_").firstOrNull { it != currentUserId } ?: run {
        android.util.Log.e("CallScreen", "❌ Could not parse receiverId from chatId: $chatId")
        onEndCall()
        return
    }
    
    val db = remember { FirebaseFirestore.getInstance() }
    val scope = rememberCoroutineScope()
    
    // Core states
    var callConnected by remember { mutableStateOf(false) }
    var isRemoteOfferSet by remember { mutableStateOf(false) }
    var isHandleOfferStarted by remember { mutableStateOf(false) }
    var remoteVideoTrack by remember { mutableStateOf<VideoTrack?>(null) }
    var localVideoTrack by remember { mutableStateOf<VideoTrack?>(null) }
    var hasLoggedCall by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var isVideoOff by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(false) }
    var displayName = remember { mutableStateOf(callerName.ifBlank { "Connecting..." }) }
    var callStatusText by remember { mutableStateOf(if (isIncoming) "" else "Calling...") }
    var avatarUrl = remember { mutableStateOf("") }
    var myName = remember { mutableStateOf("") }
    var myAvatarUrl = remember { mutableStateOf("") }
    var isIdentityLoaded by remember { mutableStateOf(false) }
    var isFrontCamera by remember { mutableStateOf(true) }
    var isLocalVideoFullscreen by remember { mutableStateOf(false) }
    // 🛡️ Pre-emptive hardware check: ToneGenerator can throw RuntimeException if native track init fails
    val toneGenerator = remember { 
        try {
            ToneGenerator(AudioManager.STREAM_VOICE_CALL, 70)
        } catch (e: Exception) {
            android.util.Log.e("CallScreen", "❌ ToneGenerator init failed: ${e.message}")
            null
        }
    }

    // Signaling & WebRTC
    val signalingClient = remember { SignalingClient(chatId, currentUserId, receiverId, isVideo) }
    var pendingOfferSdp by remember { mutableStateOf<org.webrtc.SessionDescription?>(null) }
    var callLogId by remember { 
        mutableStateOf(if (!isIncoming) "${currentUserId}_${receiverId}_${System.currentTimeMillis()}" else "") 
    }
    // Duration tracking
    var callStartTime by remember { mutableLongStateOf(0L) }
    var durationSeconds by remember { mutableIntStateOf(0) }

    // 🛡️ Avoid rememberUpdatedState for values needed immediately after async callbacks
    // Read directly from the mutableStateOf value properties below
    val currentCallLogId by rememberUpdatedState(callLogId)

    val webRTCClient = remember {
        WebRTCClient(
            context = context,
            onDescriptionGenerated = { sdp ->
                if (sdp.type == org.webrtc.SessionDescription.Type.OFFER) {
                    // 🛡️ Ensure we have our own identity before sending the offer
                    val nameToSend = myName.value.ifBlank { FirebaseAuth.getInstance().currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "User" }
                    signalingClient.sendOffer(
                        sdp = sdp,
                        callerName = nameToSend,
                        callerAvatar = myAvatarUrl.value,
                        callLogId = currentCallLogId,
                        receiverName = displayName.value,
                        receiverPic = avatarUrl.value
                    )
                } else {
                    signalingClient.sendAnswer(sdp)
                }
            },
            onCandidateFound = { signal -> signalingClient.sendIceCandidate(signal) },
            isVideoCall = isVideo
        )
    }

    var isMediaInitialized by remember { mutableStateOf(false) }
    var isAnswering by remember { mutableStateOf(false) }

    // Permissions & Init
    val permissions = if (isVideo) arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                      else arrayOf(Manifest.permission.RECORD_AUDIO)
    
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        if (perms.values.all { it }) {
            // Media initialized inside the identity fetch block below
        } else {
            Toast.makeText(context, "Permissions denied", Toast.LENGTH_SHORT).show()
            onEndCall()
        }
    }

    // Lifecycle
    LaunchedEffect(Unit) {
        Log.d("CallScreen", "🚀 CallScreen Launched for chatId: $chatId, isIncoming: $isIncoming")
        launcher.launch(permissions)
        
        val db = FirebaseFirestore.getInstance()
        // 1. Fetch Identities (Ours and Theirs)
        try {
            val s = db.collection("users").document(receiverId).get().await()
            val rawName = s.getString("name") ?: s.getString("phone") ?: "User"
            avatarUrl.value = s.getString("profilePicUrl") ?: s.getString("profilePic") ?: ""
            
            val me = db.collection("users").document(currentUserId).get().await()
            @Suppress("UNCHECKED_CAST")
            val aliases = me.get("contact_aliases") as? Map<String, String> ?: emptyMap()
            myName.value = me.getString("name") ?: me.getString("phone") ?: "User"
            myAvatarUrl.value = me.getString("profilePicUrl") ?: me.getString("profilePic") ?: ""
            
            val cleanAlias = aliases[receiverId]?.trim()
            val rawPhone = s.getString("phone")?.trim() ?: ""
            val cleanRaw = rawName.trim()
            val cleanCaller = callerName.trim()
            
            if (cleanAlias != null && !cleanAlias.equals("Me", ignoreCase = true) && !cleanAlias.equals("User", ignoreCase = true)) {
                displayName.value = cleanAlias
            } else if (cleanRaw.isNotBlank() && cleanRaw != "User" && !cleanRaw.equals("Me", ignoreCase = true)) {
                displayName.value = cleanRaw
            } else if (rawPhone.isNotBlank()) {
                displayName.value = rawPhone
            } else if (cleanCaller.isNotBlank() && cleanCaller != "User" && !cleanCaller.equals("Me", ignoreCase = true) && cleanCaller != "Connecting...") {
                displayName.value = cleanCaller
            } else {
                displayName.value = "Unknown Caller"
            }
            
            isIdentityLoaded = true
            
            // 2. Start Media
            try {
                webRTCClient.initializeMedia()
                isMediaInitialized = true
            } catch (e: Exception) {
                android.util.Log.e("CallScreen", "❌ Hardware initialization failed: ${e.message}")
                onEndCall()
                return@LaunchedEffect
            }
            
            // 3. Start Signaling (if we are the caller)
            if (!isIncoming) {
                // ⏱️ Small delay to ensure media and UI are fully ready before signaling
                delay(500)
                android.util.Log.d("CallScreen", "🚀 Starting Outgoing Call")
                webRTCClient.call()
            } else {
                if (shouldAutoAnswer || WebRTCSessionManager.shouldAutoAnswer) {
                    android.util.Log.d("CallScreen", "✅ Auto-answer signal detected")
                    WebRTCSessionManager.shouldAutoAnswer = false
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("CallScreen", "❌ Identity load failed: ${e.message}")
        }
        
        // 🛡️ Safety Timeout: Fail call if no connection in 30 seconds
        delay(30000)
        if (!callConnected) {
            android.util.Log.w("CallScreen", "⏳ Call timed out")
            onEndCall()
        }

        // Apply GLOBAL PENDING OFFER
        WebRTCSessionManager.pendingOffer?.let { offer ->
            pendingOfferSdp = offer
            WebRTCSessionManager.pendingCallLogId?.let { id -> if (id.isNotEmpty()) callLogId = id }
        }

        // ✅ Suppress chat notifications while call screen is active
        com.hyzin.whtsappclone.utils.NotificationUtils.currentActiveChatId = chatId
    }

    // Process Pending Offer as soon as Media Initializes
    LaunchedEffect(isMediaInitialized) {
        if (isMediaInitialized && isIncoming && pendingOfferSdp != null && !isRemoteOfferSet && !isHandleOfferStarted) {
            isHandleOfferStarted = true
            Log.d("CallScreen", "📥 Media ready. Setting pending remote offer immediately...")
            try {
                webRTCClient.handleOffer(pendingOfferSdp!!) {
                     isRemoteOfferSet = true
                }
            } catch (e: Exception) {
                isHandleOfferStarted = false
                Log.e("CallScreen", "❌ Error setting pending offer: ${e.message}")
            }
        }
    }

    // 🛡️ Centralized Call Answering Logic: 
    // Safely execute the answer ONLY when WebRTC has completely finished compiling the remote SDP logic.
    LaunchedEffect(isAnswering, isRemoteOfferSet) {
        if (isAnswering && isRemoteOfferSet && !callConnected) {
            try {
                Log.d("CallScreen", "🚀 Auto-answering pending call now safely...")
                db.collection("signaling_calls").document(chatId).update("status", "answered")
                webRTCClient.answer()
                callConnected = true
            } catch (e: Exception) {
                Log.e("CallScreen", "❌ Crash during safe answer execution: ${e.message}")
                onEndCall()
            }
        }
    }


    LaunchedEffect(callLogId) {
        if (callLogId.isNotEmpty()) {
            signalingClient.updateCallLogId(callLogId)
        }
    }

    // Helper to log call directly to chat box
    fun saveCallMessageToChat(status: String, duration: Int) {
        if (hasLoggedCall) return
        hasLoggedCall = true
        
        val db = FirebaseFirestore.getInstance()
        val timestamp = System.currentTimeMillis()
        val senderId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        // Define human-readable text
        val durationText = if (duration > 0) String.format("%02d:%02d", duration / 60, duration % 60) else ""
        val msgText = when(status) {
            "completed" -> if (isVideo) "Video call ($durationText)" else "Voice call ($durationText)"
            "missed" -> if (isVideo) "Missed video call" else "Missed voice call"
            "cancelled" -> if (isVideo) "Cancelled video call" else "Cancelled voice call"
            "declined" -> if (isVideo) "Declined video call" else "Declined voice call"
            else -> "Call ended"
        }

        val messageData = hashMapOf(
            "sender_id" to if (status == "missed" || status == "declined") receiverId else currentUserId,
            "receiver_id" to if (status == "missed" || status == "declined") currentUserId else receiverId,
            "text" to msgText,
            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "type" to "call",
            "status" to "sent",
            "callStatus" to status,
            "duration" to duration,
            "isVideo" to isVideo
        )

        db.collection("messages").document(chatId).collection("contents").add(messageData)
        
        // Update conversation meta
        db.collection("conversations").document(chatId).update(
            "lastMessage", msgText,
            "lastMessageTimestamp", com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
    }

    // ✅ Collect Signaling Events (Must be top-level)
    LaunchedEffect(signalingClient) {
        signalingClient.signalingEvents.collect { event ->
            when (event) {
                is SignalingEvent.OfferReceived -> {
                    pendingOfferSdp = event.sdp
                    if (event.callLogId.isNotEmpty()) callLogId = event.callLogId
                    
                    if (displayName.value == "Connecting..." || displayName.value == "User" || displayName.value.equals("Me", ignoreCase = true) || displayName.value == "Unknown") {
                        val cleanEventCaller = event.callerName.trim()
                        if (!cleanEventCaller.equals("Me", ignoreCase = true) && !cleanEventCaller.equals("User", ignoreCase = true) && cleanEventCaller.isNotBlank()) {
                            displayName.value = cleanEventCaller
                        } else {
                            displayName.value = "Unknown"
                        }
                        avatarUrl.value = event.callerAvatar
                    }
                    
                    // 🛡️ Only process if media is ready, otherwise it will fail
                    scope.launch {
                        Log.d("CallScreen", "📥 Offer received. Waiting for media init...")
                        while(!isMediaInitialized) delay(100)
                        
                        if (!isRemoteOfferSet && !isHandleOfferStarted) {
                            isHandleOfferStarted = true
                            Log.d("CallScreen", "📥 Media ready. Setting remote offer from event...")
                            webRTCClient.handleOffer(event.sdp) {
                                isRemoteOfferSet = true
                            }
                        }
                        
                        // If we are auto-answering, trigger it now safely out of the coroutine
                        if (shouldAutoAnswer || WebRTCSessionManager.shouldAutoAnswer) {
                             Log.d("CallScreen", "🚀 Auto-answering triggered...")
                             WebRTCSessionManager.shouldAutoAnswer = false
                             isAnswering = true
                        }
                    }
                }
                is SignalingEvent.AnswerReceived -> {
                    if (event.callLogId == callLogId || event.callLogId.isEmpty() || callLogId.isEmpty()) {
                        if (callLogId.isEmpty() && event.callLogId.isNotEmpty()) callLogId = event.callLogId
                        webRTCClient.handleAnswer(event.sdp)
                        callConnected = true
                        toneGenerator?.stopTone()
                    }
                }
                is SignalingEvent.IceCandidateReceived -> {
                    if (event.callLogId == callLogId || event.callLogId.isEmpty() || callLogId.isEmpty()) {
                        if (callLogId.isEmpty() && event.callLogId.isNotEmpty()) callLogId = event.callLogId
                        webRTCClient.handleIceCandidate(event.candidate)
                    }
                }
                is SignalingEvent.CallAnswered -> {
                    if (event.callLogId == callLogId || event.callLogId.isEmpty() || callLogId.isEmpty()) {
                        callStatusText = "Connecting..."
                    }
                }
                is SignalingEvent.CallEnded, is SignalingEvent.CallDeclined -> {
                    val eventId = if (event is SignalingEvent.CallEnded) event.callLogId else (event as SignalingEvent.CallDeclined).callLogId
                    
                    // 🛡️ Relaxed matching: If we don't have a callLogId yet, adopt the one from the terminal event
                    // to ensure we can log the missed/cancelled call correctly.
                    if (callLogId.isEmpty() && eventId.isNotEmpty()) {
                        callLogId = eventId
                    }

                    // 🛡️ Only terminate if IDs match (or if we are still in an uninitialized/ringing state)
                    if (eventId == callLogId || callLogId.isEmpty()) {
                        val effectiveId = if (callLogId.isNotEmpty()) callLogId else eventId
                        effectiveId.takeIf { it.isNotEmpty() }?.let { id ->
                            val endStatus = if (callConnected) "completed" 
                                           else if (event is SignalingEvent.CallDeclined) "declined"
                                           else if (isIncoming) "missed" 
                                           else "cancelled"
                            
                            val updateData = mutableMapOf<String, Any>(
                                "status" to endStatus,
                                "endTime" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                                "duration" to durationSeconds
                            )
                            
                            FirebaseFirestore.getInstance().collection("call_logs")
                                .document(id).update(updateData)
                            
                            saveCallMessageToChat(endStatus, durationSeconds)
                        }
                        onEndCall()
                    }
                }
            }
        }
    }


    // 📺 Track Collection
    LaunchedEffect(Unit) {
        launch {
            webRTCClient.localVideoTrackFlow.collect { track -> localVideoTrack = track }
        }
        launch {
            webRTCClient.remoteVideoTrackFlow.collect { track -> remoteVideoTrack = track }
        }
        launch {
            webRTCClient.isFrontCamera.collect { isFront -> isFrontCamera = isFront }
        }
        launch {
            webRTCClient.connectionStatusFlow.collect { state ->
                if (state == org.webrtc.PeerConnection.IceConnectionState.CONNECTED || 
                    state == org.webrtc.PeerConnection.IceConnectionState.COMPLETED) {
                    
                    if (!callConnected) {
                        callConnected = true
                        toneGenerator?.stopTone()
                        
                        context.stopService(android.content.Intent(context, CallNotificationService::class.java).apply { 
                            action = "STOP_SERVICE" 
                        })
                        
                        callLogId.takeIf { it.isNotEmpty() }?.let { id ->
                            FirebaseFirestore.getInstance().collection("call_logs")
                                .document(id).update("status", "completed")
                        }
                    }
                }
            }
        }
    }

    // 🔊 Ringback tone for outgoing calls
    LaunchedEffect(callConnected) {
        if (!isIncoming && !callConnected) {
            // Start dialing tone
            toneGenerator?.startTone(ToneGenerator.TONE_SUP_RINGTONE)
        } else if (callConnected) {
            toneGenerator?.stopTone()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webRTCClient.disconnect()
            signalingClient.destroy()
            
            // ✅ Only log locally if we are the one ending it without a remote signal
            if (CallActionReceiver.lastHandledCallId != callLogId) {
                callLogId?.takeIf { it.isNotEmpty() }?.let { id ->
                    val endStatus = if (callConnected) "completed" 
                                   else if (isIncoming) "missed" 
                                   else "cancelled"
                                   
                    val updateData = mutableMapOf<String, Any>(
                        "status" to endStatus,
                        "endTime" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                        "duration" to durationSeconds
                    )
                    
                    FirebaseFirestore.getInstance().collection("call_logs")
                        .document(id).update(updateData)
                    
                    saveCallMessageToChat(endStatus, durationSeconds)
                    
                    // ✅ CRITICAL: Signal terminal state to partner in Firestore signaling collection
                    if (!callConnected) {
                        if (isIncoming) signalingClient.declineCall() else signalingClient.cancelCall()
                    } else {
                        signalingClient.endCall()
                    }
                }
            }
            
            // Reset speaker on exit
            val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
            AudioUtils.setSpeakerphoneOn(audioManager, false)
            
            com.hyzin.whtsappclone.utils.NotificationUtils.currentActiveChatId = null
            
            toneGenerator?.stopTone()
            toneGenerator?.release()
            signalingClient.destroy()
            webRTCClient.disconnect()
            
            // ✅ Ensure ringing stops when leaving the screen
            context.stopService(android.content.Intent(context, CallNotificationService::class.java))
        }
    }

    // UI Colors & Gradients
    val callGradient = androidx.compose.ui.graphics.Brush.verticalGradient(
        colors = listOf(Color.Black.copy(alpha = 0.3f), Color.Transparent, Color.Black.copy(alpha = 0.6f))
    )

    // Persistent Renderers to avoid flicker
    val localRenderer = remember { SurfaceViewRenderer(context) }
    val remoteRenderer = remember { SurfaceViewRenderer(context) }
    
    // UI
    Box(modifier = Modifier.fillMaxSize().background(callGradient)) {
        if (isVideo) {
            val fullscreenTrack = if (isLocalVideoFullscreen) localVideoTrack else remoteVideoTrack
            val fullscreenMirror = if (isLocalVideoFullscreen) isFrontCamera else false
            
            val pipTrack = if (isLocalVideoFullscreen) remoteVideoTrack else localVideoTrack
            val pipMirror = if (isLocalVideoFullscreen) false else isFrontCamera

            // --- FULLSCREEN LAYER ---
            Box(modifier = Modifier.fillMaxSize().clickable { isLocalVideoFullscreen = !isLocalVideoFullscreen }) {
                AndroidView(
                    factory = { _ ->
                        remoteRenderer.apply {
                            init(webRTCClient.eglBaseContext, null)
                            setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                            setEnableHardwareScaler(true)
                        }
                    },
                    update = { renderer ->
                        renderer.setMirror(fullscreenMirror)
                    },
                    modifier = Modifier.fillMaxSize()
                )
                
                // Top & Bottom Gradient Overlays for UI visibility
                Box(modifier = Modifier.fillMaxSize().background(callGradient))
            }

            // --- HEADER ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onEndCall) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimize", tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(displayName.value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color.LightGray.copy(alpha = 0.7f), modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("End-to-end encrypted", color = Color.LightGray.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                }
                CallTimerView(callConnected, durationSeconds) { durationSeconds = it }
            }

            // --- PIP LAYER (Floating self-view) ---
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 110.dp, end = 16.dp)
                    .size(110.dp, 160.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
                    .border(1.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .clickable { isLocalVideoFullscreen = !isLocalVideoFullscreen }
            ) {
                AndroidView(
                    factory = { _ ->
                        localRenderer.apply {
                            setZOrderMediaOverlay(true)
                            init(webRTCClient.eglBaseContext, null)
                            setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                            setEnableHardwareScaler(true)
                        }
                    },
                    update = { renderer ->
                        renderer.setMirror(pipMirror)
                    },
                    modifier = Modifier.fillMaxSize()
                )
                
                if (pipTrack == null) {
                    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A1A)), contentAlignment = Alignment.Center) {
                         CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
            }

            // --- UNIFIED TRACK MANAGER (Prevents Duplication) ---
            LaunchedEffect(isLocalVideoFullscreen, localVideoTrack, remoteVideoTrack) {
                // Use direct tracks from client for better reliability
                val localTrack = webRTCClient.localVideoTrack
                val remoteTrack = webRTCClient.remoteVideoTrack
                
                // 1. CLEAR: Detach everything from both renderers to prevent cross-talk
                localTrack?.removeSink(localRenderer)
                localTrack?.removeSink(remoteRenderer)
                remoteTrack?.removeSink(localRenderer)
                remoteTrack?.removeSink(remoteRenderer)
                
                // 2. RE-BIND: Attach to the correct targets based on the swap state
                if (isLocalVideoFullscreen) {
                    // Self-view is Fullscreen
                    localTrack?.addSink(remoteRenderer)
                    remoteTrack?.addSink(localRenderer)
                } else {
                    // Remote-view is Fullscreen (Default)
                    remoteTrack?.addSink(remoteRenderer)
                    localTrack?.addSink(localRenderer)
                }
            }
        } else {
            // Audio Call View
            Column(
                modifier = Modifier.fillMaxSize().padding(top = 100.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(modifier = Modifier.size(120.dp).clip(CircleShape).background(Color.Gray)) {
                    AsyncImage(
                        model = avatarUrl.value,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(Modifier.height(24.dp))
                Text(displayName.value, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                CallTimerView(callConnected, durationSeconds) { durationSeconds = it }
                if (!callConnected) {
                    Text(callStatusText, color = Color.LightGray, fontSize = 16.sp)
                }
            }
        }

        if (isIncoming && !callConnected) {
            IncomingCallScreen(
                callerName = displayName.value,
                callerAvatarUrl = avatarUrl.value,
                isVideo = isVideo,
                onAccept = {
                    if (!isAnswering && !callConnected) {
                        android.util.Log.d("CallScreen", "✅ Call accepted by user, queued for answering")
                        isAnswering = true
                    }
                },
                onDecline = {
                    android.util.Log.d("CallScreen", "❌ Call declined by user")
                    FirebaseFirestore.getInstance().collection("signaling_calls").document(chatId).update("status", "declined")
                    onEndCall()
                }
            )
        } else {
            // Floating Bottom Controls
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(horizontal = 8.dp, vertical = 12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { isSpeakerOn = !isSpeakerOn; AudioUtils.setSpeakerphoneOn(context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager, isSpeakerOn) },
                        modifier = Modifier.size(52.dp).background(if (isSpeakerOn) Color.White.copy(alpha = 0.2f) else Color.Transparent, CircleShape)
                    ) {
                        Icon(if (isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeMute, contentDescription = null, tint = Color.White)
                    }

                    if (isVideo) {
                        IconButton(
                            onClick = { webRTCClient.switchCamera() },
                            modifier = Modifier.size(52.dp).background(Color.White.copy(alpha = 0.1f), CircleShape)
                        ) {
                            Icon(Icons.Default.FlipCameraAndroid, contentDescription = null, tint = Color.White)
                        }
                        
                        IconButton(
                            onClick = { isVideoOff = !isVideoOff; webRTCClient.toggleVideo(isVideoOff) },
                            modifier = Modifier.size(52.dp).background(if (isVideoOff) Color.Red.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.1f), CircleShape)
                        ) {
                            Icon(if (isVideoOff) Icons.Default.VideocamOff else Icons.Default.Videocam, contentDescription = null, tint = Color.White)
                        }
                    }

                    IconButton(
                        onClick = { isMuted = !isMuted; webRTCClient.toggleAudio(isMuted) },
                        modifier = Modifier.size(52.dp).background(if (isMuted) Color.White.copy(alpha = 0.2f) else Color.Transparent, CircleShape)
                    ) {
                        Icon(if (isMuted) Icons.Default.MicOff else Icons.Default.Mic, contentDescription = null, tint = Color.White)
                    }

                    IconButton(
                        onClick = { 
                            context.stopService(android.content.Intent(context, CallNotificationService::class.java).apply { action = "STOP_SERVICE" })
                            if (!callConnected) {
                                if (isIncoming) signalingClient.declineCall() else signalingClient.cancelCall()
                            } else {
                                signalingClient.endCall()
                            }
                            onEndCall() 
                        },
                        modifier = Modifier.size(52.dp).background(Color.Red, CircleShape)
                    ) {
                        Icon(Icons.Default.CallEnd, contentDescription = null, tint = Color.White)
                    }
                }
            }
        }
    }
}

/**
 * ⏱️ Optimized Call Timer Component
 * Isolates duration updates to prevent full-screen recompositions.
 */
@Composable
fun CallTimerView(
    isActive: Boolean,
    initialSeconds: Int,
    onTick: (Int) -> Unit
) {
    var seconds by remember { mutableIntStateOf(initialSeconds) }
    
    LaunchedEffect(isActive) {
        if (isActive) {
            while (isActive) {
                delay(1000)
                seconds++
                onTick(seconds)
            }
        }
    }
    
    if (isActive) {
        val timerText = String.format("%02d:%02d", seconds / 60, seconds % 60)
        Text(timerText, color = Color.LightGray, fontSize = 16.sp)
    }
}
