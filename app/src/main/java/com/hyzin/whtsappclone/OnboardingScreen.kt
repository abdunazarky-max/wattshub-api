package com.hyzin.whtsappclone

import android.Manifest
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.BorderStroke
import android.widget.Toast
import com.google.firebase.firestore.FirebaseFirestore
import com.hyzin.whtsappclone.ui.theme.*
import com.hyzin.whtsappclone.utils.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun OnboardingScreen(onComplete: (String, String) -> Unit, onNavigateToLogin: () -> Unit) {
    var currentStep by remember { mutableIntStateOf(1) }
    
    // User Data
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Futuristic 3D Animated Background ────────────────────────────────
        AnimatedBackground()

        // ── Step Content ─────────────────────────────────────────────────────
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
                } else {
                    (slideInHorizontally { -it } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
                }.using(SizeTransform(clip = false))
            },
            label = "step_transition"
        ) { step ->
            when (step) {
                1 -> WelcomeStep(onNext = { currentStep = 2 })
                2 -> AuthChoiceStep(onLogin = onNavigateToLogin, onSignup = { currentStep = 3 })
                3 -> NameSetupStep(name, onNameChange = { name = it }, onNext = { currentStep = 4 })
                4 -> AdvancedSignupStep(
                    email, mobile, dob, name,
                    onEmailChange = { email = it },
                    onMobileChange = { mobile = it },
                    onDobChange = { dob = it },
                    onCreate = onComplete
                )
            }
        }
    }
}

@Composable
fun WelcomeStep(onNext: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "welcome_anim")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearOutSlowInEasing), RepeatMode.Reverse),
        label = "scale"
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.scale(scale)
        ) {
            Text(
                text = "Welcome to WattsHub",
                fontSize = 42.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                textAlign = TextAlign.Center,
                lineHeight = 48.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Connect. Chat. Call. Instantly.",
                fontSize = 18.sp,
                color = SkyBlueAccent.copy(alpha = 0.8f),
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp
            )
        }

        Spacer(modifier = Modifier.height(120.dp))

        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(64.dp)
                .graphicsLayer { shadowElevation = 10f },
            colors = ButtonDefaults.buttonColors(containerColor = VibrantGreenAction),
            shape = RoundedCornerShape(32.dp)
        ) {
            Text("Get Started", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.Black)
        }
    }
}

@Composable
fun AuthChoiceStep(onLogin: () -> Unit, onSignup: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Join WattsHub", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Chat, Call & Connect Securely.", fontSize = 16.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 48.dp))

        Button(
            onClick = onSignup,
            modifier = Modifier.fillMaxWidth(0.8f).height(60.dp),
            shape = RoundedCornerShape(30.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VibrantGreenAction)
        ) {
            Text("Create Account", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedButton(
            onClick = onLogin,
            modifier = Modifier.fillMaxWidth(0.8f).height(60.dp),
            shape = RoundedCornerShape(30.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        ) {
            Text("Log In", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
fun NameSetupStep(name: String, onNameChange: (String) -> Unit, onNext: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Let’s get to know you", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Enter your name to continue", fontSize = 16.sp, color = Color.LightGray, modifier = Modifier.padding(top = 8.dp))
        
        Spacer(modifier = Modifier.height(48.dp))

        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            placeholder = { Text("Your Name", color = Color.Gray) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().graphicsLayer { translationY = -20f },
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White.copy(alpha = 0.05f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.03f),
                focusedBorderColor = AppGreen,
                unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 20.sp, textAlign = TextAlign.Center)
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onNext,
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(0.6f).height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AppGreen,
                disabledContainerColor = Color.White.copy(alpha = 0.1f)
            )
        ) {
            Text("Continue", fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSignupStep(
    email: String, mobile: String, dob: String, name: String,
    onEmailChange: (String) -> Unit,
    onMobileChange: (String) -> Unit,
    onDobChange: (String) -> Unit,
    onCreate: (String, String) -> Unit
) {
    val scrollState = rememberScrollState()
    var showDatePicker by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val selectedDate = datePickerState.selectedDateMillis
                    if (selectedDate != null) {
                        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                        onDobChange(sdf.format(java.util.Date(selectedDate)))
                    }
                    showDatePicker = false
                }) { Text("OK", color = AppGreen) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel", color = AppGreen) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(scrollState)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(32.dp)),
            color = Color.Black.copy(alpha = 0.4f)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Complete Registration", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Spacer(modifier = Modifier.height(24.dp))

                // Fields
                SignupField(
                    label = "Email Address ${if (mobile.isNotBlank()) "(Optional)" else ""}",
                    value = email,
                    onValueChange = onEmailChange,
                    icon = Icons.Default.Email,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email)
                )
                Spacer(modifier = Modifier.height(16.dp))
                SignupField(
                    label = "Mobile Number ${if (email.isNotBlank()) "(Optional)" else ""}",
                    value = mobile,
                    onValueChange = onMobileChange,
                    icon = Icons.Default.Phone,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                    Box(modifier = Modifier.fillMaxWidth()) {
                        SignupField(
                            label = "Date of Birth",
                            value = dob,
                            onValueChange = {},
                            icon = Icons.Default.CalendarToday,
                            readOnly = true
                        )
                        Box(modifier = Modifier.matchParentSize().clickable { showDatePicker = true })
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    var acceptedTerms by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = acceptedTerms,
                            onCheckedChange = { acceptedTerms = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = VibrantGreenAction,
                                uncheckedColor = Color.Gray,
                                checkmarkColor = Color.Black
                            )
                        )
                        Text(
                            text = "I agree to the Terms & Conditions",
                            color = Color.LightGray,
                            fontSize = 14.sp,
                            modifier = Modifier.clickable { /* Could navigate to terms */ }
                        )
                    }

                Spacer(modifier = Modifier.height(40.dp))

                    Button(
                        onClick = {
                            val isEmailSelected = email.isNotBlank()
                            val identifier = if (isEmailSelected) email else mobile
                            val normalizedId = if (isEmailSelected) identifier.trim().lowercase() else PhoneUtils.normalize(identifier)
                            
                            isLoading = true
                            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            
                            // Check if already registered
                            val field = if (isEmailSelected) "email" else "phone"
                            db.collection("users").whereEqualTo(field, normalizedId).get()
                                .addOnSuccessListener { query ->
                                    if (!query.isEmpty()) {
                                        isLoading = false
                                        Toast.makeText(context, "Account already exists. Please login.", Toast.LENGTH_LONG).show()
                                        return@addOnSuccessListener
                                    }

                                    val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                                    if (isEmailSelected) {
                                        val newOtp = (100000..999999).random().toString()
                                        val pendingData = hashMapOf(
                                            "name" to name,
                                            "identifier" to normalizedId,
                                            "otp" to newOtp,
                                            "dob" to dob,
                                            "email" to normalizedId,
                                            "timestamp" to com.google.firebase.Timestamp.now()
                                        )
                                        
                                        db.collection("pending_registrations").document(normalizedId).set(pendingData)
                                            .addOnSuccessListener {
                                                scope.launch {
                                                    val result = NetworkUtils.sendVerificationCode(normalizedId, "email", newOtp)
                                                    isLoading = false
                                                    if (result is NetworkResult.Success) {
                                                        onCreate(normalizedId, "")
                                                    } else {
                                                        Toast.makeText(context, "Error: ${(result as NetworkResult.Error).message}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                    } else {
                                            val activity = context.getActivity()
                                            if (activity == null) {
                                                isLoading = false
                                                Toast.makeText(context, "Error: Activity context required", Toast.LENGTH_SHORT).show()
                                                return@addOnSuccessListener
                                            }
                                            val phoneOptions = com.google.firebase.auth.PhoneAuthOptions.newBuilder(auth)
                                                .setPhoneNumber(normalizedId)
                                                .setTimeout(60L, java.util.concurrent.TimeUnit.SECONDS)
                                                .setActivity(activity)
                                            .setCallbacks(object : com.google.firebase.auth.PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                                                override fun onVerificationCompleted(credential: com.google.firebase.auth.PhoneAuthCredential) {
                                                    auth.signInWithCredential(credential).addOnSuccessListener { onCreate(normalizedId, "COMPLETED") }
                                                }
                                                override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                                                    isLoading = false
                                                    Toast.makeText(context, "Verification failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                                override fun onCodeSent(id: String, token: com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken) {
                                                    isLoading = false
                                                    onCreate(normalizedId, id)
                                                }
                                            }).build()
                                        com.google.firebase.auth.PhoneAuthProvider.verifyPhoneNumber(phoneOptions)
                                    }
                                }
                                .addOnFailureListener {
                                    isLoading = false
                                    Toast.makeText(context, "Network error. Please try again.", Toast.LENGTH_SHORT).show()
                                }
                        },
                        enabled = !isLoading && (email.isNotBlank() || mobile.isNotBlank()) && dob.isNotBlank() && acceptedTerms,
                    modifier = Modifier.fillMaxWidth().height(60.dp).graphicsLayer { 
                        shadowElevation = 20f
                    },
                    shape = RoundedCornerShape(30.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VibrantGreenAction,
                        disabledContainerColor = Color.White.copy(alpha = 0.1f)
                    )
                ) {
                    if (isLoading) CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                    else Text("Create Account", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
        }
    }
}

@Composable
fun SignupField(label: String, value: String, onValueChange: (String) -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector, readOnly: Boolean = false, keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default) {
    var isFocused by remember { mutableStateOf(false) }
    
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        readOnly = readOnly,
        keyboardOptions = keyboardOptions,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = if (isFocused) AppGreen else Color.Gray) },
        modifier = Modifier.fillMaxWidth().onFocusChanged { isFocused = it.isFocused },
        shape = RoundedCornerShape(16.dp),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.Black.copy(alpha = 0.3f),
            unfocusedContainerColor = Color.Black.copy(alpha = 0.2f),
            focusedBorderColor = AppGreen,
            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
            focusedLabelColor = AppGreen,
            unfocusedLabelColor = Color.Gray,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
        )
    )
}

