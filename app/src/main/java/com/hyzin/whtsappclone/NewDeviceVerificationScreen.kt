package com.hyzin.whtsappclone

import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.core.*
import kotlinx.coroutines.launch
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.hyzin.whtsappclone.ui.theme.SkyBlueAccent
import com.hyzin.whtsappclone.ui.theme.AppGreen
import com.hyzin.whtsappclone.utils.getActivity

@Composable
fun NewDeviceVerificationScreen(
    identifier: String,
    userId: String,
    authId: String = "",
    onVerified: () -> Unit,
    onBack: () -> Unit,
    onAccept: (String) -> Unit // New callback to navigate to OTP screen
) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val scope = rememberCoroutineScope()

    // Security State
    
    var isLoading by remember { mutableStateOf(false) }
    var existingDeviceName by remember { mutableStateOf("Another device") }
    var recoveryCodes by remember { mutableStateOf<List<String>>(emptyList()) }
    var usingRecoveryCode by remember { mutableStateOf(false) }
    var recoveryInput by remember { mutableStateOf("") }
    var waitStatus by remember { mutableStateOf("Detecting session...") }
    
    val infiniteTransition = rememberInfiniteTransition(label = "security_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f, label = "alpha",
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearOutSlowInEasing), RepeatMode.Reverse)
    )

    // Signal Login Attempt & Listen for Remote Decision
    DisposableEffect(userId) {
        if (userId.isEmpty()) return@DisposableEffect onDispose {}

        // 1. Fetch initial data
        db.collection("users").document(userId).get().addOnSuccessListener { doc ->
            existingDeviceName = doc.getString("activeDeviceName") ?: "Primary device"
            @Suppress("UNCHECKED_CAST")
            recoveryCodes = doc.get("recoveryCodes") as? List<String> ?: emptyList()
            
            val activeDeviceId = doc.getString("activeDeviceId")
            if (activeDeviceId.isNullOrEmpty()) onVerified()
            else waitStatus = "Waiting for $existingDeviceName..."
        }

        // 2. Listen for Response (Accept/Decline)
        val registration = db.collection("users").document(userId).addSnapshotListener { snapshot, _ ->
            val attempt = snapshot?.get("loginAttempt") as? Map<String, Any>
            val status = attempt?.get("status") as? String
            
            if (status == "accepted") {
                isLoading = true
                waitStatus = "Authorized! Sending OTP..."
                
                val isEmail = identifier.contains("@")
                
                if (isEmail) {
                    val newOtp = (100000..999999).random().toString()
                    db.collection("pending_registrations").document(identifier).set(
                        mapOf(
                            "identifier" to identifier,
                            "otp" to newOtp,
                            "timestamp" to com.google.firebase.Timestamp.now(),
                            "isLogin" to true
                        )
                    ).addOnSuccessListener {
                       scope.launch {
                           val result = NetworkUtils.sendVerificationCode(identifier, "email", newOtp)
                           if (result is NetworkResult.Success) {
                               completeVerification(userId, context, authId, {
                                   db.collection("users").document(userId).update("loginAttempt.status", "processed")
                                       .addOnSuccessListener {
                                           db.collection("users").document(userId)
                                               .update("loginAttempt", com.google.firebase.firestore.FieldValue.delete())
                                       }
                                   onAccept("") // VerificationId empty for email
                               }) {
                                   isLoading = false
                                   Toast.makeText(context, "Session Handover Failed", Toast.LENGTH_SHORT).show()
                               }
                           } else {
                               isLoading = false
                               Toast.makeText(context, "OTP Delivery Failed", Toast.LENGTH_SHORT).show()
                           }
                       }
                    }
                } else {
                val activity = context.getActivity()
                if (activity != null) {
                    val options = com.google.firebase.auth.PhoneAuthOptions.newBuilder(auth)
                        .setPhoneNumber(identifier)
                        .setTimeout(60L, java.util.concurrent.TimeUnit.SECONDS)
                        .setActivity(activity)
                        .setCallbacks(object : com.google.firebase.auth.PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                            override fun onVerificationCompleted(c: com.google.firebase.auth.PhoneAuthCredential) {}
                            override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                                isLoading = false
                                Toast.makeText(context, "SMS Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                            override fun onCodeSent(vid: String, t: com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken) {
                                completeVerification(userId, context, authId, {
                                    db.collection("users").document(userId).update("loginAttempt.status", "processed")
                                        .addOnSuccessListener {
                                            db.collection("users").document(userId)
                                                .update("loginAttempt", com.google.firebase.firestore.FieldValue.delete())
                                        }
                                    onAccept(vid)
                                }) {
                                    isLoading = false
                                    Toast.makeText(context, "Session Handover Failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }).build()
                    com.google.firebase.auth.PhoneAuthProvider.verifyPhoneNumber(options)
                } else {
                    isLoading = false
                    Toast.makeText(context, "Authorization Error: Activity context required", Toast.LENGTH_SHORT).show()
                }
                }
            } else if (status == "declined") {
                scope.launch {
                    Toast.makeText(context, "ACCESS DENIED: Declined by primary device.", Toast.LENGTH_LONG).show()
                    onBack()
                }
                // Clear attempt
                db.collection("users").document(userId).update("loginAttempt.status", "blocked")
                    .addOnSuccessListener {
                        db.collection("users").document(userId)
                            .update("loginAttempt", com.google.firebase.firestore.FieldValue.delete())
                    }
            }
        }

        onDispose {
            registration.remove()
        }
    }

    Scaffold(
        containerColor = Color(0xFF0A0A0A)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(Modifier.width(8.dp))
                Text("Security Check", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(32.dp))

            // Pulse Security Icon
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1A1A1A).copy(alpha = pulseAlpha)),
                contentAlignment = Alignment.Center
            ) {
                Icon(if (usingRecoveryCode) Icons.Default.LockReset else Icons.Default.Security, 
                    contentDescription = null, tint = AppGreen, modifier = Modifier.size(54.dp))
            }

            Spacer(Modifier.height(32.dp))

            Text(
                if (usingRecoveryCode) "Account Recovery" else "Device Authorization",
                fontSize = 26.sp,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            
            Text(
                if (usingRecoveryCode) 
                    "Enter one of your 8-digit recovery codes to authorize this login. The code will be disabled after one use."
                else 
                    "A login request has been sent to your primary device ($existingDeviceName). Please tap ✅ ACCEPT on that device to continue.",
                fontSize = 15.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp, bottom = 40.dp)
            )

            if (!usingRecoveryCode) {
                 Surface(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    color = Color.White.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, AppGreen.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = AppGreen, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(waitStatus, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Check your other device for a notification", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            if (usingRecoveryCode) {
                OutlinedTextField(
                    value = recoveryInput,
                    onValueChange = { recoveryInput = it },
                    label = { Text("Recovery Code (e.g. 1234-5678)") },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppGreen,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                        unfocusedLabelColor = Color.Gray,
                        focusedLabelColor = AppGreen,
                        focusedTextColor = Color.White
                    )
                )
            }

            TextButton(
                onClick = { usingRecoveryCode = !usingRecoveryCode },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(
                    if (usingRecoveryCode) "Back to Authorization" else "Lost access to your phone?",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }

            Spacer(Modifier.height(40.dp))

            // Verify Button (Only for Recovery Codes)
            if (usingRecoveryCode) {
                Button(
                    onClick = {
                        if (recoveryInput.isEmpty()) return@Button
                        isLoading = true
                        
                        // Verify recovery code
                        if (recoveryCodes.contains(recoveryInput)) {
                            // Success! Consume the code
                            val newCodes = recoveryCodes.toMutableList().apply { remove(recoveryInput) }
                            db.collection("users").document(userId).update("recoveryCodes", newCodes)
                                .addOnSuccessListener {
                                    completeVerification(userId, context, authId, { 
                                        isLoading = false
                                        onVerified() 
                                    }) { 
                                        isLoading = false
                                        Toast.makeText(context, "Error updating session", Toast.LENGTH_SHORT).show()
                                    }
                                }
                        } else {
                            isLoading = false
                            Toast.makeText(context, "Invalid recovery code", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    shape = RoundedCornerShape(30.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppGreen, contentColor = Color.Black),
                    enabled = !isLoading && recoveryInput.isNotEmpty()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                    } else {
                        Text("USE RECOVERY CODE", fontWeight = FontWeight.Black, fontSize = 16.sp)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            
            if (!usingRecoveryCode) {
                Surface(
                    color = Color(0xFF1A1A1A),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "This session will be authorized once you tap Accept on your other device.",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

fun completeVerification(userId: String, context: android.content.Context, authId: String, onVerified: () -> Unit, onFail: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    val newDeviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"
    val deviceName = android.os.Build.MODEL
    
    db.collection("users").document(userId).update(
        mapOf(
            "activeDeviceId" to newDeviceId,
            "activeDeviceName" to deviceName,
            "activeDeviceSince" to com.google.firebase.Timestamp.now()
        )
    ).addOnSuccessListener {
        android.util.Log.d("NDV", "Device verified successfully. Session taken over.")
        onVerified()
    }.addOnFailureListener { e -> 
        android.util.Log.e("NDV", "Final device update failed: ${e.message}")
        onFail() 
    }
}
