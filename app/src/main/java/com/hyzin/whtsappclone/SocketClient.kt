package com.hyzin.whtsappclone

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject
import java.net.URISyntaxException

/**
 * SocketClient: Low-latency real-time networking using Socket.io.
 * This unifies signaling for WebRTC and instant chat messaging.
 */
object SocketClient {
    private var socket: Socket? = null
    
    // Default IPs to try: 10.0.2.2 (Emulator), Localhost, and Hardcoded IP
    private const val SERVER_URL = "http://10.223.107.150:3005"
    private const val EMULATOR_URL = "http://10.0.2.2:3005"

    private val _chatMessages = MutableSharedFlow<Map<String, String>>(extraBufferCapacity = 10)
    val chatMessages = _chatMessages.asSharedFlow()
    
    private val _signalingEvents = MutableSharedFlow<JSONObject>(replay = 20, extraBufferCapacity = 50)
    val signalingEvents = _signalingEvents.asSharedFlow()
    
    private val _securityEvents = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 5)
    val securityEvents = _securityEvents.asSharedFlow()

    fun connect(userId: String) {
        if (socket?.connected() == true) return
        
        val url = if (android.os.Build.MODEL.contains("sdk_gphone") || android.os.Build.MODEL.contains("Emulator")) {
            EMULATOR_URL
        } else {
            SERVER_URL
        }

        android.util.Log.d("SocketClient", "🔄 Attempting to connect to: $url")

        try {
            val opts = IO.Options().apply {
                forceNew = true
                reconnection = true
            }
            socket = IO.socket(url, opts)
            
            socket?.on(Socket.EVENT_CONNECT) {
                Log.d("SocketClient", "Connected to signaling server")
                val registrationData = JSONObject().apply {
                    put("userId", userId)
                    put("deviceName", android.os.Build.MODEL)
                }
                socket?.emit("register-device", registrationData)
            }

            socket?.on("receive-message") { args ->
                val data = args[0] as JSONObject
                val messageMap = mutableMapOf<String, String>()
                val keys = data.keys()
                while (keys.hasNext()) {
                    val key = keys.next() as String
                    messageMap[key] = data.optString(key, "")
                }
                _chatMessages.tryEmit(messageMap)
            }

            // WebRTC Signaling
            socket?.on("video-offer") { args -> 
                android.util.Log.d("WattsHubSocket", "📡 Socket SIGNAL: video-offer")
                _signalingEvents.tryEmit(args[0] as JSONObject) 
            }
            socket?.on("video-answer") { args -> 
                android.util.Log.d("WattsHubSocket", "📡 Socket SIGNAL: video-answer")
                _signalingEvents.tryEmit(args[0] as JSONObject) 
            }
            socket?.on("ice-candidate") { args -> 
                android.util.Log.d("WattsHubSocket", "📡 Socket SIGNAL: ice-candidate")
                _signalingEvents.tryEmit(args[0] as JSONObject) 
            }
            socket?.on("call-decline") { args -> 
                android.util.Log.d("WattsHubSocket", "📡 Socket SIGNAL: call-decline")
                _signalingEvents.tryEmit(args[0] as JSONObject) 
            }
            socket?.on("call-ended") { args -> 
                android.util.Log.d("WattsHubSocket", "📡 Socket SIGNAL: call-ended")
                _signalingEvents.tryEmit(args[0] as JSONObject) 
            }
            socket?.on("call-busy") { args -> 
                _signalingEvents.tryEmit(args[0] as JSONObject) 
            }

            socket?.on("security-alert") { args ->
                val data = args[0] as JSONObject
                _securityEvents.tryEmit("alert" to data.optString("message", "Security Alert"))
            }

            socket?.on("logout-force") { _ ->
                _securityEvents.tryEmit("logout" to "Device removed")
            }

            socket?.connect()
        } catch (e: URISyntaxException) {
            Log.e("SocketClient", "URL error: ${e.message}")
        }
    }
    
    fun sendMessage(data: JSONObject) {
        socket?.emit("send-message", data)
    }
    
    fun emit(event: String, data: JSONObject) {
        socket?.emit(event, data)
    }

    fun disconnect() {
        socket?.disconnect()
        socket = null
    }

    fun joinGroup(groupId: String) {
        socket?.emit("join-room", groupId)
    }
}

