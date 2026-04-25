package com.hyzin.whtsappclone

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import android.app.KeyguardManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.key.onKeyEvent
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import android.util.Log
import com.google.firebase.auth.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.hyzin.whtsappclone.ui.theme.*
import com.hyzin.whtsappclone.utils.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val deepLinkFlow = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 1)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("navigate_to")?.let { deepLinkFlow.tryEmit(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Install Splash Screen MUST be called before super.onCreate
        try {
            installSplashScreen()
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "installSplashScreen failed: ${e.message}")
        }

        super.onCreate(savedInstanceState)

        // 2. Add Global Crash Logging to identify terminal errors
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("WattsHubCrash", "FATAL CRASH on thread ${thread.name}: ${throwable.message}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        try {
            enableEdgeToEdge()
            
            // 🛡️ Full-Screen Call Support: Allow activity to show over lock screen and turn screen on
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
                val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                keyguardManager.requestDismissKeyguard(this, null)
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                )
            }
        } catch (e: Exception) {
             android.util.Log.e("MainActivity", "UI configuration failed: ${e.message}")
        }
        
        setContent {
            val activityContext = LocalContext.current
            val auth = FirebaseAuth.getInstance()
            val db = FirebaseFirestore.getInstance()
            val navController = rememberNavController()
            val scope = rememberCoroutineScope()

            // ⚙️ Auth & App Control Actions
            val performLogout = {
                updatePresence(false)
                auth.signOut()
                navController.safeNavigate("login") {
                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                }
                Toast.makeText(activityContext, "Logged out successfully", Toast.LENGTH_SHORT).show()
            }

            val performQuit = {
                finish()
            }
            
            // 🛡️ Security Check State
            var isSecurityCompromised by remember { mutableStateOf(false) }
            var securityMessage by remember { mutableStateOf("") }


            // 📩 Real-Time Deep Link Collection
            LaunchedEffect(Unit) {
                // Handle initial intent
                intent.getStringExtra("navigate_to")?.let { 
                    android.util.Log.d("WattsHubDeepLink", "Initial Intent Route: $it")
                    deepLinkFlow.tryEmit(it) 
                }
                
                deepLinkFlow.collect { route ->
                    android.util.Log.d("WattsHubDeepLink", "Navigating to: $route")
                    try {
                        navController.safeNavigate(route)
                    } catch (e: Exception) {
                        android.util.Log.e("WattsHubDeepLink", "SafeNav failed: ${e.message}")
                    }
                }
            }

            LaunchedEffect(Unit) {
                if (AppProtection.isDeviceRooted()) {
                    isSecurityCompromised = true
                    securityMessage = "Security Alert: This device appears to be rooted. For your safety, WattsHub cannot run on compromised devices."
                } else if (AppProtection.isRunningOnEmulator() && !BuildConfig.DEBUG) {
                    // Only block emulator in production/release
                    isSecurityCompromised = true
                    securityMessage = "Security Alert: Emulators are not supported for this secure application."
                }
            }
            
            // 🛠️ Theme & Preference Management
            val prefManager = remember { PreferenceManager(activityContext) }
            val savedThemeStr = prefManager.getTheme()
            var selectedTheme by remember { mutableStateOf(savedThemeStr) }

            // Map string to ThemeType enum
            val appTheme = when (selectedTheme) {
                "LIGHT" -> ThemeType.LIGHT
                "OCEAN_BLUE" -> ThemeType.OCEAN_BLUE
                else -> ThemeType.STARRY_NIGHT
            }

            // 🛠️ App Version Handling
            val currentVersionCode = 31
            val currentVersionName = "3.1.0"
            var showUpdateDialog by remember { mutableStateOf(false) }
            var isForceUpdate by remember { mutableStateOf(false) }
            var loginAttemptData by remember { mutableStateOf<Map<String, Any>?>(null) }
            var showLoginAttemptDialog by remember { mutableStateOf(false) }
            var incomingNotification by remember { mutableStateOf<Map<String, String>?>(null) }

            LaunchedEffect(Unit) {
                // 1. Cache Fix on Version Change
                val savedVersionName = prefManager.getVersionName()
                if (savedVersionName != currentVersionName) {
                    try {
                        // activityContext.cacheDir.deleteRecursively() // Temporarily disabled to avoid aggressive deletion
                        android.util.Log.i("MainActivity", "App updated to $currentVersionName. Skipping cache clear.")
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Cache clear failed: ${e.message}")
                    }
                    prefManager.setVersionName(currentVersionName)
                    prefManager.setAppVersion(currentVersionCode)
                }

                // 2. Firebase Remote Config for Updates
                val remoteConfig = FirebaseRemoteConfig.getInstance()
                val configSettings = remoteConfigSettings {
                    minimumFetchIntervalInSeconds = 3600 // 1 hour
                }
                remoteConfig.setConfigSettingsAsync(configSettings)
                remoteConfig.setDefaultsAsync(mapOf(
                    "latest_version_code" to 30L,
                    "min_required_version" to 11L
                ))

                remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val latestCode = remoteConfig.getLong("latest_version_code")
                        val minRequired = remoteConfig.getLong("min_required_version")

                        if (currentVersionCode < minRequired) {
                            isForceUpdate = true
                            showUpdateDialog = true
                        } else if (currentVersionCode < latestCode) {
                            isForceUpdate = false
                            showUpdateDialog = true
                        }
                    }
                }
            }
            var currentUserId by remember { mutableStateOf(auth.currentUser?.uid) }
            var isAuthLoaded by remember { mutableStateOf(false) }
            var lastLoginTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

            DisposableEffect(auth) {
                val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                    val uid = firebaseAuth.currentUser?.uid
                    currentUserId = uid
                    isAuthLoaded = true
                    
                    // 🟢 Reliable Presence Trigger: Mark online as soon as identity is confirmed
                    if (uid != null) {
                        android.util.Log.d("Presence", "Auth confirmed for $uid. Marking Online.")
                        updatePresence(true)
                    }
                }
                auth.addAuthStateListener(listener)
                onDispose { auth.removeAuthStateListener(listener) }
            }

            // 📩 Initialize Global Real-Time Connection & Presence Heartbeat
            LaunchedEffect(currentUserId) {
                val uid = currentUserId
                if (uid != null) {
                    SocketClient.connect(uid)
                    
                    // 💓 Heartbeat: Maintain "Online" status while app is active
                    // Running more frequently (60s) to ensure high reliability
                    while(true) {
                        Log.d("PresenceHeartbeat", "Heartbeat firing for user: $uid")
                        updatePresence(true)
                        kotlinx.coroutines.delay(60000) 
                    }
                }
            }

            // 🛡️ User Identity Stabilization: Ensure every user has a secret identity for TOTP
            LaunchedEffect(currentUserId) {
                val uid = currentUserId
                if (uid != null) {
                    db.collection("users").document(uid).get().addOnSuccessListener { doc ->
                        if (!doc.contains("secretIdentity")) {
                            val newId = IdentityUtils.generateSecretIdentity()
                            db.collection("users").document(uid).update("secretIdentity", newId)
                        }
                    }
                }
            }

            // 🛡️ Single-Session Enforcement: Sign out if another device logs in
            DisposableEffect(currentUserId) {
                val uid = currentUserId
                if (uid != null) {
                    val myDeviceId = getDeviceId(activityContext)
                    val registration = db.collection("users").document(uid).addSnapshotListener { snapshot, _ ->
                        val activeId = snapshot?.getString("activeDeviceId")
                        
                        // 🔐 Detect Login Attempt from another device (only for very fresh attempts)
                        @Suppress("UNCHECKED_CAST")
                        val attempt = snapshot?.get("loginAttempt") as? Map<String, Any>
                        if (attempt != null) {
                            val timestamp = attempt["timestamp"] as? com.google.firebase.Timestamp
                            val status = attempt["status"] as? String
                            val attemptId = "${timestamp?.seconds ?: 0}_${attempt["deviceName"]}"
                            val lastShownId = loginAttemptData?.get("id") as? String
                            val isRecent = timestamp?.let { (System.currentTimeMillis() - it.toDate().time) < 30000 } ?: false
                                 
                            if (isRecent && status == "pending" && attemptId != lastShownId) {
                                val mutableAttempt = attempt.toMutableMap()
                                mutableAttempt["id"] = attemptId
                                loginAttemptData = mutableAttempt
                                showLoginAttemptDialog = true
                            }
                        } else {
                            showLoginAttemptDialog = false
                        }

                        val currentRoute = try { navController.currentBackStackEntry?.destination?.route ?: "" } catch (e: Exception) { "" }
                        val isVerifying = (currentRoute.contains("otp") || currentRoute.contains("verification") || currentRoute == "onboarding")
                        
                        android.util.Log.d("Session", "Active: $activeId, Mine: $myDeviceId, Route: $currentRoute, Verifying: $isVerifying")
                        
                        // Only proceed if navigation graph is ready
                        val isNavReady = try { navController.graph; true } catch (e: Exception) { false }

                        if (isNavReady && !activeId.isNullOrEmpty() && activeId != myDeviceId) {
                            val timeSinceLogin = System.currentTimeMillis() - lastLoginTime
                            if (isVerifying) {
                                android.util.Log.d("Session", "Staying on verification/login screen")
                            } else if (timeSinceLogin > 5000) { // Only enforce session after 5 seconds of stability
                                if (currentRoute == "welcome" || currentRoute == "home" || currentRoute == "") {
                                    android.util.Log.d("Session", "Redirecting Device Verification. CloudActive: $activeId vs Mine: $myDeviceId")
                                    val email = auth.currentUser?.email ?: ""
                                    val fallbackIdentifier = email.ifEmpty { "phone_user" }
                                    val encodedEmail = java.net.URLEncoder.encode(fallbackIdentifier, "UTF-8")
                                    val uid = auth.currentUser?.uid ?: ""
                                    val authId = if (email.contains("@")) email else "${uid}@wattshub.com"
                                    val encodedAuthId = java.net.URLEncoder.encode(authId, "UTF-8")
                                    
                                    try {
                                         if (navController.currentBackStackEntry?.destination?.route != "new_device_verification") {
                                             scope.launch {
                                                 navController.safeNavigate("new_device_verification/$encodedEmail/$uid/$encodedAuthId") {
                                                     popUpTo(navController.graph.startDestinationId) { inclusive = true }
                                                 }
                                             }
                                         }
                                     } catch (e: Exception) {
                                         android.util.Log.e("Session", "Navigation failed: ${e.message}")
                                     }
                                } else {
                                    android.util.Log.w("Session", "Different device active: $activeId. My ID: $myDeviceId. Sign out locally.")
                                    scope.launch {
                                        auth.signOut()
                                        Toast.makeText(activityContext, "Logged out: Active on another device.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    }
                    onDispose { registration.remove() }
                } else {
                    onDispose {}
                }
            }

            // 🚀 REAL-TIME MESSAGE & NOTIFICATION ENGINE
            LaunchedEffect(currentUserId) {
                if (currentUserId != null) {
                    SocketClient.chatMessages.collect { msg ->
                        val chatId = msg["chatId"] ?: ""
                        val text = msg["text"] ?: ""
                        val senderName = msg["senderName"] ?: "New Message"
                        val senderId = msg["senderId"] ?: ""
                        val isGroup = msg["isGroup"] == "true"
                        val avatarUrl = msg["senderPic"] ?: ""

                        // 🛡️ Prevent self-notification & handle foreground visibility
                        if (senderId != currentUserId) {
                            NotificationHelper.showNotification(
                                activityContext, senderId, senderName, text, chatId, avatarUrl, isGroup
                            )
                        }

                        // Also show internal banner if not in active chat
                        if (chatId != NotificationUtils.currentActiveChatId) {
                            incomingNotification = msg
                            delay(3000)
                            incomingNotification = null
                        }
                    }
                }
            }

            // 📞 In-App Call Ringing: When app is foreground, FCM isn't delivered
            // so we listen on socket events to start CallNotificationService directly
            // 📞 In-App Call Ringing (Firestore Signaling)
            DisposableEffect(currentUserId) {
                if (currentUserId != null) {
                    val registration = db.collection("signaling_calls")
                        .whereEqualTo("to", currentUserId)
                        .whereEqualTo("status", "ringing")
                        .addSnapshotListener { snapshot, _ ->
                            snapshot?.documentChanges?.forEach { change ->
                                if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                                    val data = change.document.data
                                    val from = data["from"] as? String ?: ""
                                    if (from == currentUserId) return@forEach

                                    val callerName = data["callerName"] as? String ?: "User"
                                    val callerAvatarUrl = data["callerAvatarUrl"] as? String ?: ""
                                    val isVideo = data["isVideoCall"] as? Boolean ?: false
                                    val to = data["to"] as? String ?: currentUserId!!
                                    val chatId = if (from < to) "${from}_${to}" else "${to}_${from}"
                                    val logId = data["callLogId"] as? String ?: ""

                                    // ✅ Capture and store the offer globally
                                    val offerMap = data["offer"] as? Map<String, Any>
                                    val sdp = offerMap?.get("sdp") as? String
                                    if (!sdp.isNullOrBlank()) {
                                        WebRTCSessionManager.pendingOffer = org.webrtc.SessionDescription(
                                            org.webrtc.SessionDescription.Type.OFFER, sdp
                                        )
                                        WebRTCSessionManager.pendingCallLogId = logId
                                    }

                                    android.util.Log.d("WattsHubDeepLink", "📞 Incoming Firestore Call from $from ring...")

                                    // Start notification service
                                    val callIntent = android.content.Intent(activityContext, CallNotificationService::class.java).apply {
                                        putExtra("chatId", chatId)
                                        putExtra("callerName", callerName)
                                        putExtra("isVideo", isVideo)
                                        putExtra("isIncoming", true)
                                        putExtra("callerAvatarUrl", callerAvatarUrl)
                                    }
                                    
                                    // ✅ Only start ringtone if no answer has been sent yet
                                    val hasAnswer = change.document.contains("answer")
                                    if (!hasAnswer) {
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                            activityContext.startForegroundService(callIntent)
                                        } else {
                                            activityContext.startService(callIntent)
                                        }
                                    }
                                } else if (change.type == com.google.firebase.firestore.DocumentChange.Type.MODIFIED || 
                                           change.type == com.google.firebase.firestore.DocumentChange.Type.REMOVED) {
                                    val status = change.document.getString("status")
                                    val hasAnswer = change.document.contains("answer")
                                    
                                    // If status matches terminal/active states, stop the ringtone
                                    if (status in listOf("answered", "ended", "declined", "cancelled") || hasAnswer || change.type == com.google.firebase.firestore.DocumentChange.Type.REMOVED) {
                                         android.util.Log.d("CallService", "Global Monitor: Stopping ringtone (status: $status)")
                                         activityContext.stopService(android.content.Intent(activityContext, CallNotificationService::class.java).apply { action = "STOP_SERVICE" })
                                         
                                         if (status in listOf("ended", "declined", "cancelled") || (change.type == com.google.firebase.firestore.DocumentChange.Type.REMOVED && status != "answered" && !hasAnswer)) {
                                             WebRTCSessionManager.pendingOffer = null
                                             WebRTCSessionManager.pendingCallLogId = null
                                         }
                                    }
                                }
                            }
                        }
                    onDispose { registration.remove() }
                } else {
                    onDispose {}
                }
            }

            // 🛠️ Notification & Media Permissions Sync
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { perms ->
                android.util.Log.d("WattsHubNotify", "Permissions Granted: $perms")
            }

            LaunchedEffect(currentUserId) {
                if (currentUserId != null) {
                    val permissionsToRequest = mutableListOf<String>()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(activityContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    if (ContextCompat.checkSelfPermission(activityContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
                    }
                    if (ContextCompat.checkSelfPermission(activityContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        permissionsToRequest.add(Manifest.permission.CAMERA)
                    }
                    
                    if (permissionsToRequest.isNotEmpty()) {
                        permissionLauncher.launch(permissionsToRequest.toTypedArray())
                    }

                    com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            db.collection("users").document(currentUserId!!).update("fcmToken", task.result)
                        }
                    }
                }
            }

            WhtsAppCloneTheme(selectedTheme = appTheme) {
                // 🛡️ Security Alert Dialog
                if (isSecurityCompromised && !BuildConfig.DEBUG) {
                    AlertDialog(
                        onDismissRequest = { isSecurityCompromised = false },
                        title = { Text("Security Warning", color = Color(0xFFFF3B30), fontWeight = FontWeight.Bold) },
                        text = { Text(securityMessage, color = Color.White) },
                        confirmButton = {
                            TextButton(onClick = { isSecurityCompromised = false }) {
                                Text("PROCEED", color = SkyBlueAccent)
                            }
                        },
                        containerColor = Color(0xFF1E1E1E)
                    )
                }

                // Update Dialog UI
                if (showUpdateDialog) {
                    AlertDialog(
                        onDismissRequest = { if (!isForceUpdate) showUpdateDialog = false },
                        title = { Text(if (isForceUpdate) "CRITICAL UPDATE" else "Update Available", color = Color.White) },
                        text = { Text(if (isForceUpdate) "A new version is required to continue using WattsHub. Please update now." else "New version with improvements is available. Would you like to update?", color = Color.LightGray) },
                        confirmButton = {
                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, "market://details?id=${activityContext.packageName}".toUri())
                                    activityContext.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AppGreen)
                            ) {
                                Text("UPDATE NOW")
                            }
                        },
                        dismissButton = {
                            if (!isForceUpdate) {
                                TextButton(onClick = { showUpdateDialog = false }) {
                                    Text("LATER", color = Color.Gray)
                                }
                            }
                        },
                        containerColor = Color(0xFF1E1E1E)
                    )
                    if (isForceUpdate) return@WhtsAppCloneTheme // Block app content
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        if (!isAuthLoaded) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            // 🚀 Unified NavHost for consistent state management
                            NavHost(
                            navController = navController, 
                            startDestination = if (currentUserId == null) "onboarding" else "welcome"
                        ) {
                            // ── Public Routes ──
                            composable("onboarding") {
                                OnboardingScreen(
                                    onComplete = { identifier, vid -> navController.safeNavigate("otp/$identifier/$vid") },
                                    onNavigateToLogin = { navController.safeNavigate("login") }
                                )
                            }
                            composable("login") {
                                LoginScreen(
                                    onNavigateToSignUp = { navController.safeNavigate("signup") },
                                    onNavigateToOtp = { id, vid -> navController.safeNavigate("otp/$id/$vid") },
                                    onNavigateToNewDeviceVerification = { email, uid ->
                                        val encodedEmail = java.net.URLEncoder.encode(email, "UTF-8")
                                        val rawAuthId = if (email.contains("@")) email else "${email}@wattshub.com"
                                        val encodedAuthId = java.net.URLEncoder.encode(rawAuthId, "UTF-8")
                                        navController.safeNavigate("new_device_verification/$encodedEmail/$uid/$encodedAuthId")
                                    }
                                )
                            }
                            composable("signup") {
                                SignUpScreen(
                                    onNavigateToLogin = { navController.safeNavigate("login") },
                                    onNavigateToTerms = { navController.safeNavigate("terms") },
                                    onNavigateToOtp = { id, vid -> navController.safeNavigate("otp/$id/$vid") }
                                )
                            }
                            composable(
                                route = "otp/{id}/{vid}",
                                arguments = listOf(
                                    navArgument("id") { type = NavType.StringType },
                                    navArgument("vid") { type = NavType.StringType }
                                )
                            ) { backStack ->
                                val id = backStack.arguments?.getString("id") ?: ""
                                val vid = backStack.arguments?.getString("vid") ?: ""
                                OtpVerificationScreen(
                                    identifier = id,
                                    verificationId = vid,
                                    onVerificationSuccess = { 
                                         lastLoginTime = System.currentTimeMillis()
                                         navController.safeNavigate("home") {
                                             popUpTo("login") { inclusive = true }
                                         }
                                    },
                                    onNewDeviceDetected = { email, uid ->
                                        lastLoginTime = System.currentTimeMillis()
                                        val encodedEmail = java.net.URLEncoder.encode(email, "UTF-8")
                                        val rawAuthId = if (email.contains("@")) email else "${email}@wattshub.com"
                                        val encodedAuthId = java.net.URLEncoder.encode(rawAuthId, "UTF-8")
                                        navController.safeNavigate("new_device_verification/$encodedEmail/$uid/$encodedAuthId") {
                                            popUpTo("otp/$id/$vid") { inclusive = true }
                                        }
                                    }
                                )
                            }
                            composable("terms") { TermsScreen(onBack = { navController.popBackStack() }) }
                            
                            composable(
                                route = "new_device_verification/{identifier}/{userId}/{authId}",
                                arguments = listOf(
                                    navArgument("identifier") { type = NavType.StringType },
                                    navArgument("userId") { type = NavType.StringType },
                                    navArgument("authId") { type = NavType.StringType }
                                )
                            ) { backStack ->
                                val ndvId = java.net.URLDecoder.decode(backStack.arguments?.getString("identifier") ?: "", "UTF-8")
                                val ndvUid = backStack.arguments?.getString("userId") ?: ""
                                val ndvAuthId = java.net.URLDecoder.decode(backStack.arguments?.getString("authId") ?: "", "UTF-8")
                                NewDeviceVerificationScreen(
                                    identifier = ndvId,
                                    userId = ndvUid,
                                    authId = ndvAuthId,
                                    onVerified = { navController.safeNavigate("welcome") { popUpTo(navController.graph.startDestinationId) { inclusive = true } } },
                                    onBack = { navController.safeNavigate("login") { popUpTo(navController.graph.startDestinationId) { inclusive = true }; auth.signOut() } },
                                    onAccept = { vid ->
                                        navController.safeNavigate("otp/$ndvId/${vid}") {
                                            popUpTo("new_device_verification/$ndvId/$ndvUid/$ndvAuthId") { inclusive = true }
                                        }
                                    }
                                )
                            }

                            // ── Authenticated Routes ──
                            composable("welcome") {
                                WelcomeScreen(onTimeout = { 
                                    navController.safeNavigate("home") { popUpTo("welcome") { inclusive = true } }
                                })
                            }
                            composable("home") {
                                HomeScreen(
                                    onLogout = performLogout,
                                    onNavigateToSettings = { navController.safeNavigate("settings") },
                                    onNavigateToEditProfile = { navController.safeNavigate("edit_profile") },
                                    onNavigateToAccount = { navController.safeNavigate("account") },
                                    onNavigateToDevices = { navController.safeNavigate("device_management") },
                                    onNavigateToAdmin = { navController.safeNavigate("admin_panel") },
                                    onChatClick = { id, name, isGroup ->
                                        if (currentUserId != null) {
                                            val cId = if (isGroup) id else {
                                                if (currentUserId!! < id) "${currentUserId}_$id" else "${id}_$currentUserId"
                                            }
                                            db.collection("users").document(currentUserId!!).collection("unread_counts").document(cId).delete()
                                        }
                                        val encodedName = java.net.URLEncoder.encode(name, "UTF-8")
                                        navController.safeNavigate("chat/$id/$encodedName/$isGroup")
                                    },
                                    onCallClick = { isVideo, chatId, name ->
                                        val encodedChatId = java.net.URLEncoder.encode(chatId, "UTF-8")
                                        val encodedName = java.net.URLEncoder.encode(name, "UTF-8")
                                        navController.safeNavigate("call/$encodedChatId/$isVideo/false/$encodedName/false")
                                    },
                                    onQuit = performQuit
                                )
                            }
                            composable("settings") { 
                                SettingsScreen(
                                     onBack = { navController.popBackStack() }, 
                                     onLogout = performLogout,
                                     onNavigateToEditProfile = { navController.safeNavigate("edit_profile") },
                                     onNavigateToAccount = { navController.safeNavigate("account") },
                                     onNavigateToDevices = { navController.safeNavigate("device_management") },
                                     onNavigateToAdmin = { navController.safeNavigate("admin_panel") },
                                     onNavigateToAuthenticator = { navController.safeNavigate("authenticator") },
                                     onNavigateToNotifications = { navController.safeNavigate("notifications") },
                                     onNavigateToStorage = { navController.safeNavigate("storage") },
                                     onNavigateToLanguage = { navController.safeNavigate("language") },
                                     onNavigateToHelp = { navController.safeNavigate("help") },
                                     onQuit = performQuit
                                ) 
                            }
                            composable("authenticator") { AuthenticatorScreen(onBack = { navController.popBackStack() }) }
                            composable("account") {
                                AccountSettingsScreen(onBack = { navController.popBackStack() }, onNavigateToPrivacy = { navController.safeNavigate("privacy") })
                            }
                            composable("privacy") { 
                                PrivacySettingsScreen(
                                    onBack = { navController.popBackStack() },
                                    onNavigateToThemeSelection = { navController.safeNavigate("theme_selection") },
                                    currentTheme = selectedTheme
                                ) 
                            }
                            composable("theme_selection") {
                                ThemeSelectionScreen(
                                    onBack = { navController.popBackStack() },
                                    currentTheme = selectedTheme,
                                    onThemeSelected = { newTheme ->
                                        selectedTheme = newTheme
                                        prefManager.setTheme(newTheme)
                                    }
                                )
                            }
                            composable("edit_profile") { 
                                val capturedImageUri by it.savedStateHandle.getStateFlow<String?>("captured_image_uri", null).collectAsState()
                                EditProfileScreen(
                                    onBack = { navController.popBackStack() },
                                    onNavigateToCamera = { navController.safeNavigate("camera") },
                                    capturedImageUri = capturedImageUri,
                                    onImageCapturedHandled = { it.savedStateHandle.remove<String>("captured_image_uri") }
                                ) 
                            }
                            composable("device_management") { LinkedDevicesScreen(onBack = { navController.popBackStack() }) }
                            composable("admin_panel") { AdminPanelScreen(onBack = { navController.popBackStack() }) }
                            
                            // ── Secondary Settings ──
                            composable("notifications") { NotificationSettingsScreen(onBack = { navController.popBackStack() }) }
                            composable("storage") { StorageDataScreen(onBack = { navController.popBackStack() }) }
                            composable("language") { AppLanguageScreen(onBack = { navController.popBackStack() }) }
                            composable("help") { HelpScreen(onBack = { navController.popBackStack() }) }
                            composable(
                                route = "chat/{contactId}/{contactName}/{isGroup}",
                                arguments = listOf(
                                    navArgument("contactId") { type = NavType.StringType },
                                    navArgument("contactName") { type = NavType.StringType },
                                    navArgument("isGroup") { type = NavType.BoolType }
                                )
                            ) { backStackEntry ->
                                val contactId = backStackEntry.arguments?.getString("contactId") ?: ""
                                val contactName = backStackEntry.arguments?.getString("contactName") ?: ""
                                val isGroup = backStackEntry.arguments?.getBoolean("isGroup") ?: false
                                val capturedImageUri by backStackEntry.savedStateHandle.getStateFlow<String?>("captured_image_uri", null).collectAsState()
                                ChatDetailScreen(
                                    contactId = contactId, contactName = contactName, isGroup = isGroup,
                                    capturedImageUri = capturedImageUri,
                                    onImageUploaded = { backStackEntry.savedStateHandle.remove<String>("captured_image_uri") },
                                    onBack = { 
                                        navController.popBackStack() 
                                    },
                                    onNavigateToCamera = {
                                        navController.safeNavigate("camera")
                                    },
                                    onNavigateToCall =  { isVideo, chatId, name ->
                                        val encodedChatId = java.net.URLEncoder.encode(chatId, "UTF-8")
                                        val encodedName = java.net.URLEncoder.encode(name, "UTF-8")
                                        navController.safeNavigate("call/$encodedChatId/$isVideo/false/$encodedName/false")
                                    }
                                )
                            }
                            composable(
                                route = "call/{chatId}/{isVideo}/{isIncoming}/{callerName}/{autoAnswer}",
                                arguments = listOf(
                                    navArgument("chatId") { type = NavType.StringType },
                                    navArgument("isVideo") { type = NavType.BoolType },
                                    navArgument("isIncoming") { type = NavType.BoolType },
                                    navArgument("callerName") { type = NavType.StringType; defaultValue = "" },
                                    navArgument("autoAnswer") { type = NavType.BoolType; defaultValue = false }
                                )
                            ) { backStackEntry ->
                                val chatId = backStackEntry.arguments?.getString("chatId")?.let { 
                                    kotlin.runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) 
                                } ?: ""
                                val isVideo = backStackEntry.arguments?.getBoolean("isVideo") ?: false
                                val isIncoming = backStackEntry.arguments?.getBoolean("isIncoming") ?: false
                                val callerName = backStackEntry.arguments?.getString("callerName")?.let { 
                                    kotlin.runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) 
                                } ?: "User"
                                val autoAnswer = backStackEntry.arguments?.getBoolean("autoAnswer") ?: false

                                CallScreen(
                                    chatId = chatId,
                                    isVideo = isVideo,
                                    isIncoming = isIncoming,
                                    callerName = callerName,
                                    shouldAutoAnswer = autoAnswer,
                                    onEndCall = { navController.popBackStack() }
                                )
                            }
                            composable(
                                route = "incoming_call/{chatId}/{isVideo}",
                                arguments = listOf(
                                    navArgument("chatId") { type = NavType.StringType },
                                    navArgument("isVideo") { type = NavType.BoolType }
                                )
                            ) { backStackEntry ->
                                val chatId = java.net.URLDecoder.decode(
                                    backStackEntry.arguments?.getString("chatId") ?: "", "UTF-8"
                                )
                                val isVideo = backStackEntry.arguments?.getBoolean("isVideo") ?: false
                                CallScreen(chatId = chatId, isVideo = isVideo, isIncoming = true, onEndCall = { navController.popBackStack() })
                            }

                            composable(
                                route = "video_player/{videoUrl}",
                                arguments = listOf(navArgument("videoUrl") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val encodedUrl = backStackEntry.arguments?.getString("videoUrl") ?: ""
                                val videoUrl = java.net.URLDecoder.decode(encodedUrl, "UTF-8")
                                VideoPlayerScreen(videoUrl = videoUrl, onBack = { navController.popBackStack() })
                            }

                            composable("camera") {
                                CameraScreen(
                                    onImageCaptured = { uri ->
                                        // Find the previous backstack entry and set the result
                                        navController.previousBackStackEntry?.savedStateHandle?.set("captured_image_uri", uri.toString())
                                        navController.popBackStack()
                                    },
                                    onClose = { navController.popBackStack() }
                                )
                            }

                            // ── System Routes ──
                            composable("quit") {
                                LaunchedEffect(Unit) {
                                    performQuit()
                                }
                            }
                        }

                        // 🚀 Global Foreground Notification Banner UI
                        AnimatedVisibility(
                            visible = incomingNotification != null,
                            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                            modifier = Modifier.align(Alignment.TopCenter)
                        ) {
                            incomingNotification?.let { msg ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                        .statusBarsPadding()
                                        .clickable {
                                            val id = msg["senderId"] ?: ""
                                            val name = msg["senderName"] ?: "User"
                                            val isGroupStr = msg["isGroup"] ?: "false"
                                            val isGroup = isGroupStr == "true"
                                            val encodedName = java.net.URLEncoder.encode(name, "UTF-8")
                                            incomingNotification = null
                                            navController.safeNavigate("chat/$id/$encodedName/$isGroup")
                                        },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = SkyBlueAccent)
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text(msg["senderName"] ?: "New Message", fontWeight = FontWeight.Bold, color = Color.White)
                                            Text(msg["text"] ?: "", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, color = Color.LightGray)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

                // --- Global Security Modals ---
                if (showLoginAttemptDialog) {
                        val deviceName = loginAttemptData?.get("deviceName") as? String ?: "Unknown device"
                        
                        AlertDialog(
                            onDismissRequest = { showLoginAttemptDialog = false },
                            title = { 
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Security, contentDescription = null, tint = AppGreen)
                                    Spacer(Modifier.width(8.dp))
                                    Text("New Login Attempt", color = Color.White) 
                                }
                            },
                            text = {
                                Column {
                                    Text("A device named '$deviceName' is trying to log in.", color = Color.Gray)
                                    Spacer(Modifier.height(8.dp))
                                    Text("Do you want to allow this login?", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            },
                            confirmButton = { 
                                Button(
                                    onClick = { 
                                        showLoginAttemptDialog = false
                                        // ✅ Remote Accept: Update status, then delete the attempt to prevent stale re-triggers
                                        db.collection("users").document(currentUserId!!)
                                            .update("loginAttempt.status", "accepted")
                                            .addOnSuccessListener {
                                                // Clear the loginAttempt field entirely so it can't re-fire on future doc updates
                                                db.collection("users").document(currentUserId!!)
                                                    .update("loginAttempt", com.google.firebase.firestore.FieldValue.delete())
                                                // ✅ Logout old device automatically per strict logic
                                                performLogout()
                                            }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AppGreen)
                                ) { Text("ACCEPT", color = Color.Black) } 
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = { 
                                        showLoginAttemptDialog = false
                                        // ❌ Remote Decline: Update status, then delete to prevent stale re-triggers
                                        db.collection("users").document(currentUserId!!)
                                            .update("loginAttempt.status", "declined")
                                            .addOnSuccessListener {
                                                db.collection("users").document(currentUserId!!)
                                                    .update("loginAttempt", com.google.firebase.firestore.FieldValue.delete())
                                            }
                                    }
                                ) {
                                    Text("DECLINE", color = Color.Red)
                                }
                            },
                            containerColor = Color(0xFF1A1A1A)
                        )
                    }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePresence(true)
    }

    override fun onPause() {
        super.onPause()
        updatePresence(false)
    }

    private fun updatePresence(isOnline: Boolean) {
        val user = FirebaseAuth.getInstance().currentUser
        val uid = user?.uid
        if (uid == null) {
            android.util.Log.w("Presence", "Cannot update presence: No authenticated user.")
            return
        }
        
        val db = FirebaseFirestore.getInstance()
        val statusStr = if (isOnline) "online" else "offline"
        val updates = mutableMapOf<String, Any>(
            "status" to statusStr
        )
        
        if (!isOnline) {
            updates["lastSeen"] = com.google.firebase.firestore.FieldValue.serverTimestamp()
        } else {
            // Also ensure basic identity fields exist if it's an 'online' pulse
            updates["lastSeen"] = com.google.firebase.firestore.FieldValue.serverTimestamp() // Keep lastSeen moving even while online
            if (user.email != null) updates["email"] = user.email!!
        }
        
        android.util.Log.d("Presence", "Pushing status '$statusStr' for UID: $uid")
        
        // Use 'set' with merge to ensure the document and field always exist without failing
        db.collection("users").document(uid)
            .set(updates, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                android.util.Log.i("Presence", "Status sync successful: $uid -> $statusStr")
            }
            .addOnFailureListener { e -> 
                android.util.Log.e("Presence", "Status sync failed for $uid: ${e.message}")
            }
    }
}

@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    onNavigateToSignUp: () -> Unit,
    onNavigateToOtp: (String, String) -> Unit,
    onNavigateToNewDeviceVerification: (String, String) -> Unit
) {
    var userIdentifier by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val activityContext = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    
    // 🛡️ Prevent Spam: Track last sent time per identifier
    val lastOtpRequests = remember { mutableMapOf<String, Long>() }
    val cooldownMs = 60000L // 60 seconds

    Box(modifier = modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(60.dp))
            
          
            // Logo Logic
            val logoPainter = painterResource(id = R.drawable.whatsapp_premium_logo)
            Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                Image(painter = logoPainter, contentDescription = "Logo", modifier = Modifier.fillMaxSize())
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            Text(text = "WATTS HUB", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 2.sp)
            Text(text = "SECURE MESSAGING", fontSize = 12.sp, color = SkyBlueAccent, letterSpacing = 4.sp)

            Spacer(modifier = Modifier.height(60.dp))

            Surface(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)),
                color = Color.White.copy(alpha = 0.05f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    OutlinedTextField(
                        value = userIdentifier,
                        onValueChange = { userIdentifier = it },
                        placeholder = { Text("Email or Phone Number", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = SkyBlueAccent,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                        )
                    )

                    Button(
                        onClick = {
                            if (isLoading || userIdentifier.isBlank()) return@Button
                            
                            val isEmailInput = userIdentifier.contains("@")
                            val normalizedId = if (isEmailInput) userIdentifier.trim().lowercase() else PhoneUtils.normalize(userIdentifier)
                            
                            // Check cooldown
                            val lastSent = lastOtpRequests[normalizedId] ?: 0L
                            val now = System.currentTimeMillis()
                            if (now - lastSent < cooldownMs) {
                                val remaining = (cooldownMs - (now - lastSent)) / 1000
                                Toast.makeText(activityContext, "Please wait $remaining seconds before resending", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            
                            isLoading = true
                            lastOtpRequests[normalizedId] = now
                            
                            scope.launch {
                                // 🔍 PRE-CHECK: Is this a new device for an existing user?
                                db.collection("users")
                                    .whereEqualTo(if (isEmailInput) "email" else "phone", normalizedId)
                                    .get()
                                    .addOnSuccessListener { query ->
                                        if (!query.isEmpty) {
                                            val doc = query.documents[0]
                                            val activeDeviceId = doc.getString("activeDeviceId")
                                            val uid = doc.id
                                            val currentDeviceId = getDeviceId(activityContext)

                                            if (!activeDeviceId.isNullOrEmpty() && activeDeviceId != currentDeviceId) {
                                                // 🚨 NEW DEVICE DETECTED
                                                // 1. Signal Attempt (Status: Pending)
                                                db.collection("users").document(uid).update(
                                                    mapOf(
                                                        "loginAttempt" to mapOf(
                                                            "deviceName" to Build.MODEL,
                                                            "timestamp" to com.google.firebase.Timestamp.now(),
                                                            "status" to "pending"
                                                        )
                                                    )
                                                ).addOnSuccessListener {
                                                    // 2. Navigate to NewDeviceVerificationScreen (The waiting screen)
                                                    isLoading = false
                                                    onNavigateToNewDeviceVerification(normalizedId, uid)
                                                }.addOnFailureListener {
                                                    isLoading = false
                                                    Toast.makeText(activityContext, "Security Check Failed. Try again.", Toast.LENGTH_SHORT).show()
                                                }
                                                return@addOnSuccessListener
                                            }
                                        }

                                        // 🛤️ NORMAL FLOW (No existing device or same device)
                                        if (isEmailInput) {
                                            // email logic...
                                            val newOtp = (100000..999999).random().toString()
                                            db.collection("pending_registrations").document(normalizedId)
                                                .set(hashMapOf(
                                                    "identifier" to normalizedId,
                                                    "otp" to newOtp,
                                                    "timestamp" to com.google.firebase.Timestamp.now(),
                                                    "isLogin" to true
                                                ))
                                                .addOnSuccessListener {
                                                    scope.launch {
                                                        try {
                                                            val result = NetworkUtils.sendVerificationCode(normalizedId, "email", newOtp)
                                                            if (result is NetworkResult.Success) {
                                                                Toast.makeText(activityContext, "OTP sent to your email", Toast.LENGTH_SHORT).show()
                                                                onNavigateToOtp(normalizedId, "")
                                                            } else {
                                                                Toast.makeText(activityContext, "Error: ${(result as NetworkResult.Error).message}", Toast.LENGTH_SHORT).show()
                                                            }
                                                        } finally {
                                                            isLoading = false
                                                        }
                                                    }
                                                }
                                                .addOnFailureListener {
                                                    isLoading = false
                                                    Toast.makeText(activityContext, "Cloud Error. Try again.", Toast.LENGTH_SHORT).show()
                                                }
                                        } else {
                                            // phone logic...
                                            val options = PhoneAuthOptions.newBuilder(auth)
                                                .setPhoneNumber(normalizedId)
                                                .setTimeout(60L, java.util.concurrent.TimeUnit.SECONDS)
                                                .setActivity(activityContext as android.app.Activity)
                                                .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                                                    override fun onVerificationCompleted(c: PhoneAuthCredential) {
                                                        auth.signInWithCredential(c).addOnSuccessListener { }
                                                    }
                                                    override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                                                        isLoading = false
                                                        Toast.makeText(activityContext, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                                    }
                                                    override fun onCodeSent(id: String, t: PhoneAuthProvider.ForceResendingToken) {
                                                        isLoading = false
                                                        onNavigateToOtp(normalizedId, id)
                                                    }
                                                }).build()
                                            PhoneAuthProvider.verifyPhoneNumber(options)
                                        }
                                    }
                                    .addOnFailureListener {
                                        isLoading = false
                                        Toast.makeText(activityContext, "Connection Error", Toast.LENGTH_SHORT).show()
                                    }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SkyBlueAccent, contentColor = Color.Black),
                        enabled = !isLoading && userIdentifier.isNotBlank()
                    ) {
                        if (isLoading) CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                        else Text("CONTINUE", fontWeight = FontWeight.Bold)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            TextButton(onClick = onNavigateToSignUp) {
                Text("Don't have an account? Register Now", color = SkyBlueAccent)
            }
        }
    }
}

@Composable
fun OtpVerificationScreen(
    identifier: String,
    verificationId: String,
    onVerificationSuccess: () -> Unit,
    onNewDeviceDetected: (identifier: String, uid: String) -> Unit = { _, _ -> }
) {
    val otpValues = remember { mutableStateListOf("", "", "", "", "", "") }
    val focusRequesters = remember { List(6) { FocusRequester() } }
    var isLoading by remember { mutableStateOf(false) }
    val activityContext = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Verify Code", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(text = "Sent to $identifier", color = Color.Gray, modifier = Modifier.padding(top = 8.dp, bottom = 48.dp))

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            otpValues.forEachIndexed { index, value ->
                OutlinedTextField(
                    value = value,
                    onValueChange = { nv ->
                        // 📋 Handle Pasting of multiple digits
                        if (nv.length > 1 && nv.length <= 6) {
                            val digits = nv.filter { it.isDigit() }
                            digits.forEachIndexed { dIndex, char ->
                                if (index + dIndex < 6) {
                                    otpValues[index + dIndex] = char.toString()
                                }
                            }
                            val nextFocus = (index + digits.length).coerceAtMost(5)
                            focusRequesters[nextFocus].requestFocus()
                            return@OutlinedTextField
                        }

                        // ⌨️ Standard Single Digit Entry
                        if (nv.length <= 1) {
                            if (nv.isNotEmpty()) {
                                if (nv.last().isDigit()) {
                                    otpValues[index] = nv.last().toString()
                                    if (index < 5) focusRequesters[index + 1].requestFocus()
                                }
                            } else {
                                otpValues[index] = ""
                                // Optional: stay on current field when deleting via onValueChange
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                        .focusRequester(focusRequesters[index])
                        .onKeyEvent { keyEvent ->
                            // 🔙 Handle Backward Deletion
                            if (keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DEL && 
                                keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN
                            ) {
                                if (otpValues[index].isEmpty() && index > 0) {
                                    // Field already empty? Move back and clear that one
                                    otpValues[index - 1] = ""
                                    focusRequesters[index - 1].requestFocus()
                                    true
                                } else if (otpValues[index].isNotEmpty()) {
                                    // Just clear current
                                    otpValues[index] = ""
                                    true
                                } else {
                                    false
                                }
                            } else {
                                false
                            }
                        },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color.White, 
                        textAlign = TextAlign.Center, 
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SkyBlueAccent,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        cursorColor = SkyBlueAccent
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = {
                val otp = otpValues.joinToString("")
                if (otp.length < 6) {
                    Toast.makeText(activityContext, "Please enter all 6 digits", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                
                android.util.Log.d("OTP", "Verify clicked for $identifier with OTP: $otp")
                isLoading = true
                
                val currentDeviceId = getDeviceId(activityContext)
                
                if (verificationId.isNotEmpty()) {
                    // ✅ Step 1: Sign in with Firebase first to satisfy Security Rules
                    val credential = PhoneAuthProvider.getCredential(verificationId, otp)
                    auth.signInWithCredential(credential)
                        .addOnSuccessListener { result ->
                            val uid = result.user?.uid ?: ""
                            
                            // ✅ Step 2: Now authenticated, check for different device
                            db.collection("users").document(uid).get()
                                .addOnSuccessListener { userDoc ->
                                    val storedDeviceId = userDoc.getString("activeDeviceId")
                                    
                                    if (!storedDeviceId.isNullOrEmpty() && storedDeviceId != currentDeviceId) {
                                        // ⚠️ Different device - prompt for NDV (STAY LOGGED IN for permissions)
                                        isLoading = false
                                        val linkedEmail = userDoc.getString("email") ?: identifier
                                        onNewDeviceDetected(linkedEmail, uid)
                                    } else {
                                        // ✅ Same device or new user
                                        // 🛡️ DUPLICATE GUARD: Check no OTHER Firestore user already owns this phone
                                        db.collection("users")
                                            .whereEqualTo("phone", identifier)
                                            .get()
                                            .addOnSuccessListener { phoneQuery ->
                                                val duplicateDoc = phoneQuery.documents.firstOrNull { it.id != uid }
                                                if (duplicateDoc != null) {
                                                    // ❌ Another Firestore account already has this phone — block
                                                    auth.signOut()
                                                    isLoading = false
                                                    Toast.makeText(
                                                        activityContext,
                                                        "This phone number is already linked to another account. Please login instead.",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                } else {
                                                    // ✅ Phone is unique — safe to write
                                                    val deviceData = mapOf(
                                                        "uid" to uid,
                                                        "phone" to identifier,
                                                        "activeDeviceId" to currentDeviceId,
                                                        "activeDeviceName" to Build.MODEL,
                                                        "activeDeviceSince" to com.google.firebase.Timestamp.now()
                                                    )
                                                    db.collection("users").document(uid)
                                                        .set(deviceData, com.google.firebase.firestore.SetOptions.merge())
                                                        .addOnSuccessListener {
                                                            isLoading = false
                                                            onVerificationSuccess()
                                                        }
                                                        .addOnFailureListener { e ->
                                                            isLoading = false
                                                            android.util.Log.e("OTP", "User doc update failed: ${e.message}")
                                                            onVerificationSuccess() // Still let them in
                                                        }
                                                }
                                            }
                                            .addOnFailureListener {
                                                // If check fails, still allow (don't block on network error)
                                                val deviceData = mapOf(
                                                    "uid" to uid,
                                                    "phone" to identifier,
                                                    "activeDeviceId" to currentDeviceId,
                                                    "activeDeviceName" to Build.MODEL,
                                                    "activeDeviceSince" to com.google.firebase.Timestamp.now()
                                                )
                                                db.collection("users").document(uid)
                                                    .set(deviceData, com.google.firebase.firestore.SetOptions.merge())
                                                    .addOnSuccessListener { 
                                                        isLoading = false
                                                        onVerificationSuccess() 
                                                    }
                                                    .addOnFailureListener { e ->
                                                        isLoading = false
                                                        android.util.Log.e("OTP", "User doc merge failed: ${e.message}")
                                                        onVerificationSuccess() 
                                                    }
                                            }
                                    }
                                }
                                .addOnFailureListener { e ->
                                    // If we can't even read the user document while authenticated, 
                                    // it's a real permission issue we should report.
                                    isLoading = false
                                    Toast.makeText(activityContext, "Verification Error: ${e.message}", Toast.LENGTH_LONG).show()
                                    android.util.Log.e("OTP", "User fetch error: ${e.message}", e)
                                }
                        }
                        .addOnFailureListener { e ->
                            isLoading = false
                            val msg = e.message ?: "Unknown error"
                            if (msg.contains("Identity Toolkit API", ignoreCase = true)) {
                                Toast.makeText(activityContext, "Setup Error: Enable Identity Toolkit API in Google Cloud.", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(activityContext, "Verification Failed: $msg", Toast.LENGTH_LONG).show()
                            }
                        }
                } else {
                    val normalizedId = identifier.trim().lowercase()
                    // Email path: still need to check pending_registrations unauthenticated
                    // unless we sign in with a dummy/temp first (but email doesn't have an equivalent to phone credential)
                    db.collection("pending_registrations").document(normalizedId).get()
                        .addOnSuccessListener { snapshot ->
                            if (snapshot.exists() && snapshot.getString("otp") == otp) {
                                val name = snapshot.getString("name") ?: ""
                                val dob = snapshot.getString("dob") ?: ""
                                val profilePicUrl = snapshot.getString("profilePicUrl")
                                val isLogin = snapshot.getBoolean("isLogin") ?: false
                                val authId = if (identifier.contains("@")) identifier else "${identifier}@wattshub.com"
                                
                                fun performEmailSignIn(uid: String) {
                                    db.collection("users").document(uid).get().addOnSuccessListener { userDoc ->
                                        val existingSecret = userDoc.getString("secretIdentity")
                                        val deviceData = hashMapOf(
                                            "uid" to uid,
                                            "email" to identifier.lowercase(),
                                            "activeDeviceId" to currentDeviceId,
                                            "activeDeviceName" to Build.MODEL,
                                            "activeDeviceSince" to com.google.firebase.Timestamp.now()
                                        )
                                        if (existingSecret == null) {
                                            deviceData["secretIdentity"] = IdentityUtils.generateSecretIdentity()
                                        }
                                        if (name.isNotEmpty()) deviceData["name"] = name
                                        if (dob.isNotEmpty()) deviceData["dob"] = dob
                                        if (!profilePicUrl.isNullOrEmpty()) deviceData["profilePicUrl"] = profilePicUrl
                                        
                                        db.collection("users").document(uid).set(deviceData, com.google.firebase.firestore.SetOptions.merge())
                                            .addOnSuccessListener {
                                                db.collection("pending_registrations").document(normalizedId).delete()
                                                isLoading = false
                                                onVerificationSuccess()
                                            }
                                            .addOnFailureListener {
                                                isLoading = false
                                                onVerificationSuccess() 
                                            }
                                    }.addOnFailureListener {
                                        // If doc doesn't exist at all
                                        val deviceData = hashMapOf(
                                            "uid" to uid,
                                            "email" to identifier.lowercase(),
                                            "secretIdentity" to IdentityUtils.generateSecretIdentity(),
                                            "activeDeviceId" to currentDeviceId,
                                            "activeDeviceName" to Build.MODEL,
                                            "activeDeviceSince" to com.google.firebase.Timestamp.now()
                                        )
                                        if (name.isNotEmpty()) deviceData["name"] = name
                                        if (dob.isNotEmpty()) deviceData["dob"] = dob
                                        if (!profilePicUrl.isNullOrEmpty()) deviceData["profilePicUrl"] = profilePicUrl
                                        db.collection("users").document(uid).set(deviceData, com.google.firebase.firestore.SetOptions.merge())
                                            .addOnSuccessListener {
                                                db.collection("pending_registrations").document(normalizedId).delete()
                                                isLoading = false
                                                onVerificationSuccess()
                                            }
                                    }
                                }

                                // ✅ Step 1: Sign in first
                                auth.signInWithEmailAndPassword(authId, "otp_no_password")
                                    .addOnSuccessListener { result ->
                                        val uid = result.user?.uid ?: ""
                                        // ✅ Step 2: Check device (now authenticated)
                                        db.collection("users").document(uid).get()
                                            .addOnSuccessListener { userDoc ->
                                                val storedDeviceId = userDoc.getString("activeDeviceId")
                                                if (isLogin && !storedDeviceId.isNullOrEmpty() && storedDeviceId != currentDeviceId) {
                                                    isLoading = false
                                                    db.collection("pending_registrations").document(normalizedId).delete()
                                                    onNewDeviceDetected(identifier, uid)
                                                } else {
                                                    performEmailSignIn(uid)
                                                }
                                            }
                                            .addOnFailureListener {
                                                // If authenticated but fails, check rules
                                                performEmailSignIn(uid)
                                            }
                                    }
                                    .addOnFailureListener { _ ->
                                        // Firebase Auth account doesn't exist yet
                                        // 🛡️ DUPLICATE GUARD: check Firestore before creating new Auth account
                                        db.collection("users")
                                            .whereEqualTo("email", identifier.trim().lowercase())
                                            .get()
                                            .addOnSuccessListener { existingQuery ->
                                                if (!existingQuery.isEmpty) {
                                                    // ❌ Email already in Firestore — block duplicate Auth account
                                                    isLoading = false
                                                    Toast.makeText(
                                                        activityContext,
                                                        "An account with this email already exists. Please login instead.",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                } else {
                                                    // ✅ Email is unique — safe to create
                                                    auth.createUserWithEmailAndPassword(authId, "otp_no_password")
                                                        .addOnSuccessListener { result -> performEmailSignIn(result.user?.uid ?: "") }
                                                        .addOnFailureListener { e2 ->
                                                            isLoading = false
                                                            Toast.makeText(activityContext, "Auth Error: ${e2.message}", Toast.LENGTH_LONG).show()
                                                        }
                                                }
                                            }
                                            .addOnFailureListener { _ ->
                                                // Firestore check failed — proceed cautiously
                                                auth.createUserWithEmailAndPassword(authId, "otp_no_password")
                                                    .addOnSuccessListener { result -> performEmailSignIn(result.user?.uid ?: "") }
                                                    .addOnFailureListener { e2 ->
                                                        isLoading = false
                                                        Toast.makeText(activityContext, "Auth Error: ${e2.message}", Toast.LENGTH_LONG).show()
                                                    }
                                            }
                                    }
                            } else {
                                isLoading = false
                                Toast.makeText(activityContext, "Wrong Code", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .addOnFailureListener { e ->
                            isLoading = false
                            val msg = e.message ?: "Unknown error"
                            if (msg.contains("permission", ignoreCase = true)) {
                                Toast.makeText(activityContext, "Access Denied: Please check Firestore Rules for 'pending_registrations'", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(activityContext, "Network Error: $msg", Toast.LENGTH_LONG).show()
                            }
                        }
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SkyBlueAccent, contentColor = Color.Black),
            enabled = !isLoading
        ) {
            if (isLoading) CircularProgressIndicator(color = Color.Black)
            else Text("VERIFY", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun WelcomeScreen(onTimeout: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(2000)
        onTimeout()
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
             Image(painter = painterResource(id = R.drawable.whatsapp_premium_logo), contentDescription = null, modifier = Modifier.size(150.dp))
             Spacer(modifier = Modifier.height(24.dp))
             Text(stringResource(R.string.welcome_message), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}


@SuppressLint("HardwareIds")
fun getDeviceId(activityContext: Context): String {
    val id = Settings.Secure.getString(activityContext.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"
    android.util.Log.d("DeviceID", "Current device identifier: $id")
    return id
}

/**
 * 🛡️ Helper to safely navigate only if the graph is set.
 */
fun androidx.navigation.NavController.safeNavigate(route: String, builder: androidx.navigation.NavOptionsBuilder.() -> Unit = {}) {
    try {
        // Check if graph is set by accessing its property
        this.graph 
        this.navigate(route, builder)
    } catch (e: IllegalStateException) {
        android.util.Log.w("SafeNav", "Navigation skipped: Graph not ready for route $route")
    } catch (e: Exception) {
        android.util.Log.e("SafeNav", "Navigation error for $route: ${e.message}")
    }
}
