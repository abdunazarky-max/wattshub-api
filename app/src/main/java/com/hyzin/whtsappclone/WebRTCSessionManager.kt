package com.hyzin.whtsappclone

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * WebRTCSessionManager tracks the active WebRTC session and call state globally.
 */
object WebRTCSessionManager {
    // Media handling
    var activeClient: WebRTCClient? = null
    val p2pMessages = MutableSharedFlow<String>(extraBufferCapacity = 50)

    // Call state tracking
    var pendingOffer: org.webrtc.SessionDescription? = null
    var pendingCallLogId: String? = null
    var shouldAutoAnswer: Boolean = false
    private val _currentCallState = MutableStateFlow<CallState>(CallState.Idle)
    val currentCallState = _currentCallState.asStateFlow()

    fun updateCallState(newState: CallState) {
        _currentCallState.value = newState
    }
}

sealed class CallState {
    object Idle : CallState()
    data class Ringing(val chatId: String, val callerName: String, val isVideo: Boolean) : CallState()
    data class Connected(val chatId: String, val isVideo: Boolean) : CallState()
}
