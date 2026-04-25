package com.hyzin.whtsappclone

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

/**
 * SignalingClient handles real-time negotiation using Firestore.
 * This enables 24/7 calling without a dedicated signaling server.
 */
class SignalingClient(
    private val chatId: String,
    private val currentUserId: String,
    private val receiverId: String,
    private val isVideoCall: Boolean = false,
    var callLogId: String = ""
) {
    private val db = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 🛡️ Track processed signals to avoid redundant events/loops
    private var lastOfferSdp: String? = null
    private var lastAnswerSdp: String? = null
    private var hasEmittedAnsweredState = false

    private val _signalingEvents = MutableSharedFlow<SignalingEvent>(replay = 20, extraBufferCapacity = 64)
    val signalingEvents = _signalingEvents.asSharedFlow()

    private var callListener: ListenerRegistration? = null
    private var candidateListener: ListenerRegistration? = null

    init {
        if (chatId.isNotEmpty()) {
            listenForSignaling()
        }
    }

    fun updateCallLogId(newId: String) {
        if (callLogId != newId) {
            callLogId = newId
            listenForSignaling()
        }
    }

    private fun listenForSignaling() {
        // 🛡️ Cleanup existing listeners to avoid duplicates
        callListener?.remove()
        candidateListener?.remove()

        lastOfferSdp = null
        lastAnswerSdp = null
        hasEmittedAnsweredState = false

        if (chatId.isEmpty()) return

        // 1. Listen for Offer/Answer on the main call document
        callListener = db.collection("signaling_calls").document(chatId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

                val data = snapshot.data ?: return@addSnapshotListener
                val senderId = data["from"] as? String ?: ""
                val incomingCallLogId = data["callLogId"] as? String ?: ""

                // 🛡️ STRICT SESSION FILTERING: Ignore any update that doesn't match our active call session.
                // This prevents "Automatic Decline" bugs caused by stale status from previous call attempts.
                if (callLogId.isNotEmpty() && incomingCallLogId.isNotEmpty() && incomingCallLogId != callLogId) {
                    Log.w("SignalingClient", "Ignoring stale signaling event (ID: $incomingCallLogId, current: $callLogId)")
                    return@addSnapshotListener
                }

                val callerId = data["from"] as? String ?: ""
                val isCaller = currentUserId == callerId

                // 2. Handle Terminal States FIRST
                val status = data["status"] as? String
                if (status in listOf("ended", "cancelled", "declined")) {
                    // 🛡️ Relaxed matching: Accept termination if it matches our session OR if we don't have a session yet.
                    // This prevents the "Stuck on Calling Screen" bug when a call is cancelled before the offer is processed.
                    if (callLogId.isEmpty() || incomingCallLogId == callLogId) {
                        Log.d("SignalingClient", "🛑 termination received for session: $incomingCallLogId (current: $callLogId)")
                        if (status == "declined") {
                            scope.launch { _signalingEvents.emit(SignalingEvent.CallDeclined(incomingCallLogId)) }
                        } else {
                            scope.launch { _signalingEvents.emit(SignalingEvent.CallEnded(incomingCallLogId)) }
                        }
                    } else {
                        Log.w("SignalingClient", "Ignoring stale termination event (ID: $incomingCallLogId, current: $callLogId)")
                    }
                    return@addSnapshotListener
                }

                // 2a. Handle Intermediate "Answered" state for the Caller
                if (isCaller && status == "answered" && !hasEmittedAnsweredState) {
                    hasEmittedAnsweredState = true
                    scope.launch { _signalingEvents.emit(SignalingEvent.CallAnswered(incomingCallLogId)) }
                }

                // 3. Process Signals strictly based on Role to prevent self-processing crashes
                if (!isCaller) {
                    // WE ARE THE RECIPIENT -> Only process Offers (ignore our own answers)
                    @Suppress("UNCHECKED_CAST")
                    val offer = data["offer"] as? Map<String, Any>
                    if (offer != null && !data.containsKey("answer")) {
                        val sdp = offer["sdp"] as? String ?: ""
                        
                        // 🛡️ Deduplicate to prevent signaling loops
                        if (sdp == lastOfferSdp) return@addSnapshotListener
                        lastOfferSdp = sdp
                        val cName = data["callerName"] as? String ?: "Partner"
                        val cAvatar = data["callerAvatarUrl"] as? String ?: ""
                        val cLogId = data["callLogId"] as? String ?: ""
                        
                        scope.launch {
                            updateCallLogId(cLogId) // Update so we filter candidates correctly
                            _signalingEvents.emit(SignalingEvent.OfferReceived(
                                SessionDescription(SessionDescription.Type.OFFER, sdp),
                                cName,
                                cAvatar,
                                data["isVideoCall"] as? Boolean ?: isVideoCall,
                                callerId,
                                cLogId
                            ))
                        }
                    }
                } else {
                    // WE ARE THE CALLER -> Only process Answers (ignore our own offers)
                    @Suppress("UNCHECKED_CAST")
                    val answer = data["answer"] as? Map<String, Any>
                    if (answer != null) {
                        val sdp = answer["sdp"] as? String ?: ""
                        
                        // 🛡️ Deduplicate
                        if (sdp == lastAnswerSdp) return@addSnapshotListener
                        lastAnswerSdp = sdp
                        scope.launch {
                            _signalingEvents.emit(SignalingEvent.AnswerReceived(
                                SessionDescription(SessionDescription.Type.ANSWER, sdp),
                                incomingCallLogId
                            ))
                        }
                    }
                }
            }

        // 2. Listen for ICE Candidates in the sub-collection (filtered by callLogId)
        candidateListener = db.collection("signaling_calls").document(chatId)
            .collection("candidates")
            .whereEqualTo("callLogId", callLogId)
            .addSnapshotListener { snapshot, _ ->
                snapshot?.documentChanges?.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val d = change.document.data
                        if (d["from"] != currentUserId) {
                            val candidate = IceCandidate(
                                d["sdpMid"] as? String ?: "",
                                (d["sdpMLineIndex"] as? Long ?: 0L).toInt(),
                                d["candidate"] as? String ?: ""
                            )
                            scope.launch {
                                _signalingEvents.emit(SignalingEvent.IceCandidateReceived(
                                    candidate, 
                                    d["callLogId"] as? String ?: ""
                                ))
                            }
                        }
                    }
                }
            }
    }

    fun sendOffer(sdp: SessionDescription, callerName: String, callerAvatar: String, callLogId: String, receiverName: String? = null, receiverPic: String? = null) {
        this.callLogId = callLogId
        val offerData = hashMapOf(
            "from" to currentUserId,
            "to" to receiverId,
            "callerName" to callerName,
            "callerAvatarUrl" to callerAvatar,
            "isVideoCall" to isVideoCall,
            "callLogId" to callLogId,
            "status" to "ringing",
            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "offer" to hashMapOf(
                "type" to "offer",
                "sdp" to sdp.description
            )
        )
        // 🛡️ Cleanup old candidates and set new offer
        db.collection("signaling_calls").document(chatId).collection("candidates")
            .get().addOnSuccessListener { snapshot ->
                for (doc in snapshot.documents) {
                    doc.reference.delete()
                }
                db.collection("signaling_calls").document(chatId).set(offerData).addOnSuccessListener {
                    // ✅ Start listening ONLY AFTER the new "ringing" state is successfully persisted
                    listenForSignaling()
                }
            }
        
        createCallLog("outgoing", callerName, callerAvatar, receiverName, receiverPic)
    }

    fun sendAnswer(sdp: SessionDescription) {
        val answerData = hashMapOf(
            "status" to "answered",
            "answer" to hashMapOf(
                "type" to "answer",
                "sdp" to sdp.description
            )
        )
        db.collection("signaling_calls").document(chatId).update(answerData as Map<String, Any>)
    }

    fun sendIceCandidate(candidate: IceCandidate) {
        val candidateData = hashMapOf(
            "from" to currentUserId,
            "callLogId" to callLogId,
            "sdpMid" to candidate.sdpMid,
            "sdpMLineIndex" to candidate.sdpMLineIndex,
            "candidate" to candidate.sdp,
            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        db.collection("signaling_calls").document(chatId).collection("candidates").add(candidateData)
    }

    fun declineCall() {
        db.collection("signaling_calls").document(chatId).update("status", "declined")
        updateCallLog("declined")
    }

    fun endCall() {
        db.collection("signaling_calls").document(chatId).update("status", "ended")
    }

    fun cancelCall() {
        db.collection("signaling_calls").document(chatId).update("status", "cancelled")
    }

    private fun createCallLog(initialStatus: String, callerName: String? = null, callerPic: String? = null, receiverName: String? = null, receiverPic: String? = null) {
        if (callLogId.isEmpty()) return
        val logData = hashMapOf(
            "chatId" to chatId,
            "senderId" to currentUserId,
            "receiverId" to receiverId,
            "callerName" to (callerName ?: ""),
            "callerPic" to (callerPic ?: ""),
            "receiverName" to (receiverName ?: ""),
            "receiverPic" to (receiverPic ?: ""),
            "isVideoCall" to isVideoCall,
            "participantIds" to listOf(currentUserId, receiverId),
            "status" to initialStatus,
            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        db.collection("call_logs").document(callLogId).set(logData)
    }

    private fun updateCallLog(status: String) {
        if (callLogId.isEmpty()) return
        db.collection("call_logs").document(callLogId).update("status", status)
    }

    fun destroy() {
        callListener?.remove()
        candidateListener?.remove()
        scope.cancel()
    }
}

sealed class SignalingEvent {
    data class OfferReceived(
        val sdp: SessionDescription,
        val callerName: String,
        val callerAvatar: String,
        val isVideo: Boolean,
        val callerId: String,
        val callLogId: String = ""
    ) : SignalingEvent()
    data class AnswerReceived(val sdp: SessionDescription, val callLogId: String = "") : SignalingEvent()
    data class IceCandidateReceived(val candidate: IceCandidate, val callLogId: String = "") : SignalingEvent()
    data class CallDeclined(val callLogId: String = "") : SignalingEvent()
    data class CallEnded(val callLogId: String = "") : SignalingEvent()
    data class CallAnswered(val callLogId: String = "") : SignalingEvent()
}
