package com.hyzin.whtsappclone

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import com.hyzin.whtsappclone.utils.AudioUtils

/**
 * WebRTCClient manages the native PeerConnection and media tracks.
 * Uses a singleton PeerConnectionFactory and EglBase to prevent crashes on subsequent calls.
 */
class WebRTCClient(
    private val context: Context,
    private val onDescriptionGenerated: (SessionDescription) -> Unit,
    private val onCandidateFound: (IceCandidate) -> Unit,
    private val isVideoCall: Boolean
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var peerConnection: PeerConnection? = null
    var localVideoTrack: VideoTrack? = null
    var remoteVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoCapturer: VideoCapturer? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager

    companion object {
        @Volatile
        private var factory: PeerConnectionFactory? = null
        @Volatile
        private var eglBase: EglBase? = null

        fun getEglBase(): EglBase {
            return eglBase ?: synchronized(this) {
                eglBase ?: EglBase.create().also { eglBase = it }
            }
        }

        fun getFactory(context: Context): PeerConnectionFactory {
            return factory ?: synchronized(this) {
                if (factory == null) {
                    val eglContext = getEglBase().eglBaseContext
                    val videoEncoderFactory = DefaultVideoEncoderFactory(eglContext, true, true)
                    val videoDecoderFactory = DefaultVideoDecoderFactory(eglContext)
                    
                    val audioDeviceModule = JavaAudioDeviceModule.builder(context)
                        .setUseHardwareAcousticEchoCanceler(true)
                        .setUseHardwareNoiseSuppressor(true)
                        .createAudioDeviceModule()

                    val webrtcOptions = PeerConnectionFactory.Options().apply {
                        disableNetworkMonitor = true 
                    }

                    factory = PeerConnectionFactory.builder()
                        .setVideoEncoderFactory(videoEncoderFactory)
                        .setVideoDecoderFactory(videoDecoderFactory)
                        .setAudioDeviceModule(audioDeviceModule)
                        .setOptions(webrtcOptions)
                        .createPeerConnectionFactory()
                }
                factory!!
            }
        }
    }

    val eglBaseContext: EglBase.Context get() = getEglBase().eglBaseContext

    private val _remoteVideoTrack = MutableSharedFlow<VideoTrack>(replay = 1, extraBufferCapacity = 64)
    val remoteVideoTrackFlow = _remoteVideoTrack.asSharedFlow()
    private val _localVideoTrack = MutableSharedFlow<VideoTrack>(replay = 1, extraBufferCapacity = 64)
    val localVideoTrackFlow = _localVideoTrack.asSharedFlow()

    private val _isFrontCamera = kotlinx.coroutines.flow.MutableStateFlow(true)
    val isFrontCamera = _isFrontCamera.asStateFlow()


    private val _connectionStatus = MutableSharedFlow<PeerConnection.IceConnectionState>(replay = 1, extraBufferCapacity = 64)
    val connectionStatusFlow = _connectionStatus.asSharedFlow()

    private val queuedRemoteCandidates = mutableListOf<IceCandidate>()
    // 🛡️ Thread-safe set for deduplication
    private val sentCandidates = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    init {
        try {
            createPeerConnection()
            Log.d("WebRTCClient", "✅ WebRTC Client Initialized")
        } catch (e: Exception) {
            Log.e("WebRTCClient", "❌ Initialization failed", e)
        }
    }

    private fun createPeerConnection() {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
                .setUsername("openrelayproject")
                .setPassword("openrelayproject")
                .createIceServer(),
            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443")
                .setUsername("openrelayproject")
                .setPassword("openrelayproject")
                .createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_ONCE
        }

        val factory = getFactory(context)
        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                synchronized(this) {
                    try {
                        val candidateKey = "${candidate.sdpMid}:${candidate.sdp}"
                        if (sentCandidates.contains(candidateKey)) return
                        
                        sentCandidates.add(candidateKey)
                        Log.d("WebRTCClient", "❄️ [v2-safe] New ICE Candidate: ${candidate.sdpMid}")
                        onCandidateFound(candidate)
                    } catch (e: Exception) {
                        Log.e("WebRTCClient", "❌ Error in onIceCandidate callback: ${e.message}")
                    }
                }
            }
            
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                Log.d("WebRTCClient", "📶 Connection State: $state")
                scope.launch { _connectionStatus.emit(state) }
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                if (track is VideoTrack) {
                    Log.d("WebRTCClient", "📺 Remote Video Track Received")
                    remoteVideoTrack = track
                    _remoteVideoTrack.tryEmit(track)
                }
            }

            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
        })
    }

    fun initializeMedia() {
        Log.d("WebRTCClient", "🎤 Initializing Media...")
        val factory = getFactory(context)

        // Audio
        audioManager.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
        AudioUtils.setSpeakerphoneOn(audioManager, isVideoCall)

        val audioSource = factory.createAudioSource(MediaConstraints())
        localAudioTrack = factory.createAudioTrack("ARDAMSa0", audioSource).apply {
            setEnabled(true)
        }
        localAudioTrack?.let { peerConnection?.addTrack(it, listOf("ARDAMS")) }

        // Video
        if (isVideoCall) {
            try {
                var enumerator: CameraEnumerator = Camera2Enumerator(context)
                if (enumerator.deviceNames.isEmpty()) {
                    Log.w("WebRTCClient", "⚠️ Camera2Enumerator found no cameras, falling back to Camera1")
                    enumerator = Camera1Enumerator(false)
                }
                
                var deviceName = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) } 
                               ?: enumerator.deviceNames.firstOrNull()
                
                if (deviceName == null) {
                    Log.e("WebRTCClient", "❌ No camera device found after fallback")
                    return
                }
                
                val videoSource = factory.createVideoSource(false)
                videoCapturer = enumerator.createCapturer(deviceName, null)
                _isFrontCamera.value = enumerator.isFrontFacing(deviceName)
                val surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", getEglBase().eglBaseContext)
                
                Log.d("WebRTCClient", "📸 Initializing Camera: $deviceName")
                videoCapturer?.initialize(surfaceTextureHelper, context, videoSource.capturerObserver)
                videoCapturer?.startCapture(640, 480, 30) // Lowered resolution for broader compatibility
                Log.d("WebRTCClient", "✅ Camera Capture Started")
                
                localVideoTrack = factory.createVideoTrack("ARDAMSv0", videoSource).apply {
                    setEnabled(true)
                }
                localVideoTrack?.let { 
                    Log.d("WebRTCClient", "📺 Local Video Track Created")
                    peerConnection?.addTrack(it, listOf("ARDAMS")) 
                    _localVideoTrack.tryEmit(it)
                }
            } catch (e: Exception) {
                Log.e("WebRTCClient", "❌ Camera initialization failed: ${e.message}", e)
                // Final fallback try with Camera1
                try {
                   val enumerator = Camera1Enumerator(false)
                   val deviceName = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) } ?: enumerator.deviceNames.firstOrNull()
                   if (deviceName != null) {
                       val videoSource = factory.createVideoSource(false)
                       videoCapturer = enumerator.createCapturer(deviceName, null)
                       videoCapturer?.initialize(SurfaceTextureHelper.create("CaptureThreadFallback", getEglBase().eglBaseContext), context, videoSource.capturerObserver)
                       videoCapturer?.startCapture(640, 480, 30)
                       localVideoTrack = factory.createVideoTrack("ARDAMSv0", videoSource).apply { setEnabled(true) }
                       localVideoTrack?.let { 
                           peerConnection?.addTrack(it, listOf("ARDAMS"))
                           _localVideoTrack.tryEmit(it)
                       }
                       Log.d("WebRTCClient", "✅ Fallback Camera Capture Started")
                   }
                } catch (inner: Exception) {
                   Log.e("WebRTCClient", "❌ Final camera fallback failed: ${inner.message}")
                }
            }
        }
    }

    fun call() {
        Log.d("WebRTCClient", "📞 Creating Offer...")
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if(isVideoCall) "true" else "false"))
        }

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                Log.d("WebRTCClient", "✅ Offer Created Success")
                peerConnection?.setLocalDescription(this, desc)
                desc?.let { onDescriptionGenerated(it) }
            }
            override fun onSetSuccess() {
                Log.d("WebRTCClient", "✅ Local Description Set Success")
            }
            override fun onCreateFailure(p0: String?) {
                Log.e("WebRTCClient", "❌ Create Offer Failure: $p0")
            }
            override fun onSetFailure(p0: String?) {
                Log.e("WebRTCClient", "❌ Set Local Description Failure: $p0")
            }
        }, constraints)
    }

    fun handleOffer(offer: SessionDescription, onSuccess: (() -> Unit)? = null) {
        Log.d("WebRTCClient", "📞 Handling Offer...")
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                Log.d("WebRTCClient", "✅ Remote Description (Offer) Set Success")
                drainIceCandidates()
                onSuccess?.invoke()
            }
            override fun onSetFailure(p0: String?) {
                Log.e("WebRTCClient", "❌ Set Remote Description (Offer) Failure: $p0")
            }
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, offer)
    }

    fun answer() {
        Log.d("WebRTCClient", "📞 Creating Answer...")
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if(isVideoCall) "true" else "false"))
        }
        
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                Log.d("WebRTCClient", "✅ Answer Created Success")
                peerConnection?.setLocalDescription(this, desc)
                desc?.let { onDescriptionGenerated(it) }
            }
            override fun onSetSuccess() {
                Log.d("WebRTCClient", "✅ Local Description (Answer) Set Success")
            }
            override fun onCreateFailure(p0: String?) {
                Log.e("WebRTCClient", "❌ Create Answer Failure: $p0")
            }
            override fun onSetFailure(p0: String?) {
                Log.e("WebRTCClient", "❌ Set Local Description (Answer) Failure: $p0")
            }
        }, constraints)
    }

    fun handleAnswer(answer: SessionDescription) {
        Log.d("WebRTCClient", "✅ Handling Answer...")
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                Log.d("WebRTCClient", "✅ Remote Description (Answer) Set Success")
                drainIceCandidates()
            }
            override fun onSetFailure(p0: String?) {
                Log.e("WebRTCClient", "❌ Set Remote Description (Answer) Failure: $p0")
            }
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, answer)
    }

    fun handleIceCandidate(candidate: IceCandidate) {
        if (peerConnection?.remoteDescription != null) {
            peerConnection?.addIceCandidate(candidate)
        } else {
            queuedRemoteCandidates.add(candidate)
        }
    }

    private fun drainIceCandidates() {
        queuedRemoteCandidates.forEach { peerConnection?.addIceCandidate(it) }
        queuedRemoteCandidates.clear()
    }

    fun renderLocalVideo(renderer: SurfaceViewRenderer) {
        localVideoTrack?.addSink(renderer)
    }

    fun toggleAudio(isMuted: Boolean) {
        localAudioTrack?.setEnabled(!isMuted)
    }

    fun toggleVideo(isVideoOff: Boolean) {
        localVideoTrack?.setEnabled(!isVideoOff)
    }

    fun switchCamera() {
        if (videoCapturer is CameraVideoCapturer) {
            val cameraCapturer = videoCapturer as CameraVideoCapturer
            val enumerator = Camera2Enumerator(context)
            val deviceNames = enumerator.deviceNames
            
            if (deviceNames.size < 2) {
                Log.w("WebRTCClient", "⚠️ Only one camera found, skipping switch.")
                return
            }

            val currentlyFront = _isFrontCamera.value
            // Find the first camera that has the opposite facing
            val targetName = deviceNames.firstOrNull { 
                if (currentlyFront) enumerator.isBackFacing(it) else enumerator.isFrontFacing(it)
            }

            Log.d("WebRTCClient", "🔄 Switching camera. Current front: $currentlyFront, Target: $targetName")

            if (targetName != null) {
                cameraCapturer.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
                    override fun onCameraSwitchDone(isFront: Boolean) {
                        _isFrontCamera.value = isFront
                        Log.d("WebRTCClient", "✅ Camera Switched to: $targetName (IsFront: $isFront)")
                    }
                    override fun onCameraSwitchError(error: String?) {
                        Log.e("WebRTCClient", "❌ Explicit Camera Switch Error: $error. Trying default switch...")
                        // Fallback to default switch logic
                        cameraCapturer.switchCamera(this)
                    }
                }, targetName)
            } else {
                // Fallback: Just tell it to switch to the "next" one
                cameraCapturer.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
                    override fun onCameraSwitchDone(isFront: Boolean) {
                        _isFrontCamera.value = isFront
                        Log.d("WebRTCClient", "✅ Camera Switched (Default). IsFront: $isFront")
                    }
                    override fun onCameraSwitchError(error: String?) {
                        Log.e("WebRTCClient", "❌ Default Camera Switch Error: $error")
                    }
                })
            }
        } else {
            Log.e("WebRTCClient", "❌ videoCapturer is not a CameraVideoCapturer")
        }
    }

    fun disconnect() {
        try {
            Log.d("WebRTCClient", "🔌 Disconnecting WebRTC Client...")
            
            // Clean up capturer
            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
            videoCapturer = null
            
            // Dispose tracks
            localVideoTrack?.dispose()
            localVideoTrack = null
            localAudioTrack?.dispose()
            localAudioTrack = null
            
            // Close connection
            peerConnection?.close()
            peerConnection?.dispose()
            peerConnection = null
            
            // Reset audio
            audioManager.mode = android.media.AudioManager.MODE_NORMAL
            AudioUtils.setSpeakerphoneOn(audioManager, false)

            scope.cancel()
        } catch (e: Exception) {
            Log.e("WebRTCClient", "❌ Error during disconnect: ${e.message}")
        }
    }
}
