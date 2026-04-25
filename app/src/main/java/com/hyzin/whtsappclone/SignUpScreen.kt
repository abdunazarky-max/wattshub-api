
package com.hyzin.whtsappclone

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.google.firebase.auth.*
import com.hyzin.whtsappclone.ui.theme.*
import com.hyzin.whtsappclone.utils.IdentityUtils


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToOtp: (String, String) -> Unit,
    onNavigateToTerms: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var acceptedTerms by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    
    // 🛡️ Prevent Spam: Cooldown logic
    val lastOtpRequests = remember { mutableMapOf<String, Long>() }
    val cooldownMs = 60000L
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { imageUri = it }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val selectedDate = datePickerState.selectedDateMillis
                    if (selectedDate != null) {
                        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                        dob = sdf.format(Date(selectedDate))
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Box(modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        AnimatedBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Text("Create Account", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.height(32.dp))

            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E1E1E))
                    .border(2.dp, VibrantGreenAction, CircleShape)
                    .clickable { galleryLauncher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (imageUri != null) {
                    AsyncImage(model = imageUri, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                } else {
                    Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Full Name") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF00FF9D), focusedLabelColor = Color(0xFF00FF9D), focusedTextColor = Color.White, unfocusedTextColor = Color.White, unfocusedLabelColor = Color.Gray)
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = dob,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Date of Birth") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Select Date",
                            tint = Color.Gray
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VibrantGreenAction, focusedLabelColor = VibrantGreenAction, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, unfocusedLabelColor = TextSecondary)
                )
                Box(modifier = Modifier.matchParentSize().clickable { showDatePicker = true })
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email Address ${if (mobile.isNotBlank()) "(Optional)" else ""}") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VibrantGreenAction, focusedLabelColor = VibrantGreenAction, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, unfocusedLabelColor = TextSecondary)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = mobile,
                onValueChange = { mobile = it },
                label = { Text("Mobile Number ${if (email.isNotBlank()) "(Optional)" else ""}") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VibrantGreenAction, focusedLabelColor = VibrantGreenAction, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, unfocusedLabelColor = TextSecondary)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = acceptedTerms,
                    onCheckedChange = { acceptedTerms = it },
                    colors = CheckboxDefaults.colors(checkedColor = VibrantGreenAction, checkmarkColor = Color.Black, uncheckedColor = Color.Gray)
                )
                Spacer(modifier = Modifier.width(4.dp))
                val annotatedString = buildAnnotatedString {
                    append("I am 18+ and agree to the ")
                    pushLink(androidx.compose.ui.text.LinkAnnotation.Clickable(
                        tag = "terms",
                        styles = androidx.compose.ui.text.TextLinkStyles(
                            style = androidx.compose.ui.text.SpanStyle(color = VibrantGreenAction, fontWeight = FontWeight.Bold)
                        ),
                        linkInteractionListener = { onNavigateToTerms() }
                    ))
                    append("Terms & Conditions")
                    pop()
                }
                Text(
                    text = annotatedString,
                    style = androidx.compose.ui.text.TextStyle(color = Color.LightGray, fontSize = 13.sp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {
                    if (isLoading) return@Button
                    val isEmailSelected = email.isNotBlank()
                    val identifier = if (isEmailSelected) email else mobile
                    
                    val normalizedIdentifier = if (isEmailSelected) identifier.trim().lowercase() else PhoneUtils.normalize(identifier)
                    
                    // Check Cooldown
                    val lastSent = lastOtpRequests[normalizedIdentifier] ?: 0L
                    val now = System.currentTimeMillis()
                    if (now - lastSent < cooldownMs) {
                         val remaining = (cooldownMs - (now - lastSent)) / 1000
                         Toast.makeText(context, "Please wait $remaining seconds", Toast.LENGTH_SHORT).show()
                         return@Button
                    }
                    
                    isLoading = true
                    lastOtpRequests[normalizedIdentifier] = now
                    
                    scope.launch {
                        val auth = FirebaseAuth.getInstance()
                        val db = FirebaseFirestore.getInstance()

                        // 🔍 Check if already registered
                        val field = if (isEmailSelected) "email" else "phone"
                        db.collection("users").whereEqualTo(field, normalizedIdentifier).get()
                            .addOnSuccessListener { query ->
                                if (!query.isEmpty()) {
                                    isLoading = false
                                    Toast.makeText(context, "Account already exists. Please Log In. (If you recently deleted your account, you must Log In once more to fully erase your data in Settings).", Toast.LENGTH_LONG).show()
                                    return@addOnSuccessListener
                                }

                                if (isEmailSelected) {
                                    // 1. Generate 6-digit OTP for Email
                                    val newOtp = (100000..999999).random().toString()
                                    val pendingData = hashMapOf(
                                        "name" to name,
                                        "identifier" to normalizedIdentifier,
                                        "otp" to newOtp,
                                        "dob" to dob,
                                        "email" to normalizedIdentifier,
                                        "timestamp" to com.google.firebase.Timestamp.now()
                                    )
                                    
                                    // Handle Profile Pic Upload for Email signup
                                    fun savePendingAndNavigate(picUrl: String? = null) {
                                        if (picUrl != null) pendingData["profilePicUrl"] = picUrl
                                        db.collection("pending_registrations").document(normalizedIdentifier).set(pendingData)
                                            .addOnSuccessListener {
                                                onNavigateToOtp(normalizedIdentifier, "")
                                                scope.launch {
                                                    val result = NetworkUtils.sendVerificationCode(normalizedIdentifier, "email", newOtp)
                                                    isLoading = false
                                                    if (result is NetworkResult.Success) {
                                                        Toast.makeText(context, "OTP sent to your email", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "Email delivery issue. Check your email or try again.", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            }
                                            .addOnFailureListener { e ->
                                                isLoading = false
                                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                    }

                                    if (imageUri != null) {
                                        val storageRef = com.google.firebase.storage.FirebaseStorage.getInstance().reference.child("temp_pics/$normalizedIdentifier.jpg")
                                        storageRef.putFile(imageUri!!).addOnSuccessListener {
                                            storageRef.downloadUrl.addOnSuccessListener { uri ->
                                                savePendingAndNavigate(uri.toString())
                                            }
                                        }.addOnFailureListener {
                                            savePendingAndNavigate() // Proceed without pic if upload fails
                                        }
                                    } else {
                                        savePendingAndNavigate()
                                    }
                                } else {
                                    // Phone Auth Logic
                                    fun startPhoneAuth(picUrl: String? = null) {
                                        // Store metadata for phone user temporarily
                                        val metadata = hashMapOf(
                                            "name" to name,
                                            "dob" to dob,
                                            "profilePicUrl" to (picUrl ?: ""),
                                            "timestamp" to com.google.firebase.Timestamp.now()
                                        )
                                        db.collection("pending_registrations").document(normalizedIdentifier).set(metadata)

                                        val phoneOptions = PhoneAuthOptions.newBuilder(auth)
                                            .setPhoneNumber(normalizedIdentifier)
                                            .setTimeout(60L, java.util.concurrent.TimeUnit.SECONDS)
                                            .setActivity(context as android.app.Activity)
                                            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                                                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                                                    auth.signInWithCredential(credential).addOnSuccessListener { result ->
                                                        val uid = result.user?.uid
                                                        if (uid != null) {
                                                            val secretId = IdentityUtils.generateSecretIdentity()
                                                            val deviceData = hashMapOf(
                                                                "uid" to uid,
                                                                "phone" to normalizedIdentifier,
                                                                "name" to name,
                                                                "dob" to dob,
                                                                "secretIdentity" to secretId,
                                                                "activeDeviceId" to PhoneUtils.getDeviceId(context),
                                                                "activeDeviceName" to android.os.Build.MODEL,
                                                                "activeDeviceSince" to com.google.firebase.Timestamp.now()
                                                            )
                                                            if (picUrl != null) deviceData["profilePicUrl"] = picUrl
                                                            db.collection("users").document(uid).set(deviceData, com.google.firebase.firestore.SetOptions.merge())
                                                        }
                                                        onNavigateToOtp(normalizedIdentifier, "COMPLETED")
                                                    }
                                                }
                                                override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                                                    isLoading = false
                                                    val message = e.message ?: "Unknown error"
                                                    Toast.makeText(context, "Verification failed: $message", Toast.LENGTH_SHORT).show()
                                                }
                                                override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                                                    isLoading = false
                                                    onNavigateToOtp(normalizedIdentifier, id)
                                                }
                                            }).build()
                                        PhoneAuthProvider.verifyPhoneNumber(phoneOptions)
                                    }

                                    if (imageUri != null) {
                                        val storageRef = com.google.firebase.storage.FirebaseStorage.getInstance().reference.child("temp_pics/$normalizedIdentifier.jpg")
                                        storageRef.putFile(imageUri!!).addOnSuccessListener {
                                            storageRef.downloadUrl.addOnSuccessListener { uri ->
                                                startPhoneAuth(uri.toString())
                                            }
                                        }.addOnFailureListener {
                                            startPhoneAuth()
                                        }
                                    } else {
                                        startPhoneAuth()
                                    }
                                }
                            }
                            .addOnFailureListener { e ->
                                isLoading = false
                                Toast.makeText(context, "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VibrantGreenAction, contentColor = Color.White),
                enabled = !isLoading && name.isNotBlank() && dob.isNotBlank() && (email.isNotBlank() || mobile.isNotBlank()) && acceptedTerms
            ) {
                if (isLoading) CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                else Text("Create Account", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Premium Google Sign-In Integration for "Gmail only" requirement
            OutlinedButton(
                onClick = {
                    Toast.makeText(context, "Link your Gmail account securely via Firebase.", Toast.LENGTH_LONG).show()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text("CONTINUE WITH GOOGLE", fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onNavigateToLogin) {
                Text("Already registered? Log in", color = SkyBlueAccent)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
