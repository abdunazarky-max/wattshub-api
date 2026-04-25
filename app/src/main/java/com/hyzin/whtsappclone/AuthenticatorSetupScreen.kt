package com.hyzin.whtsappclone

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.hyzin.whtsappclone.ui.theme.AppGreen
import com.hyzin.whtsappclone.ui.theme.GreenSage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticatorSetupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val uid = auth.currentUser?.uid ?: ""
    val email = auth.currentUser?.email ?: ""
    
    var step by remember { mutableIntStateOf(1) }
    var secretKey by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }
    var userDisplayName by remember { mutableStateOf("") }
    var isEnabled by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var isVerifying by remember { mutableStateOf(false) }

    LaunchedEffect(uid) {
        db.collection("users").document(uid).get().addOnSuccessListener { snapshot ->
            isEnabled = snapshot.getBoolean("isAuthenticatorEnabled") ?: false
            secretKey = snapshot.getString("authenticatorSecret") ?: ""
            // Get user's identifier for the authenticator label
            userDisplayName = snapshot.getString("email") ?: snapshot.getString("phone") ?: "User"
            isLoading = false
        }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AppGreen)
        }
        return
    }

    Scaffold(
        containerColor = Color(0xFF0F0F0F),
        topBar = {
            TopAppBar(
                title = { Text("Authenticator Setup", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isEnabled) {
                StatusView(onDisable = {
                    db.collection("users").document(uid).update(
                        mapOf(
                            "isAuthenticatorEnabled" to false,
                            "authenticatorSecret" to ""
                        )
                    ).addOnSuccessListener {
                        isEnabled = false
                        secretKey = ""
                        Toast.makeText(context, "Authenticator disabled", Toast.LENGTH_SHORT).show()
                    }
                })
            } else {
                when (step) {
                    1 -> IntroductionStep(onNext = {
                        if (secretKey.isEmpty()) {
                            secretKey = generateBase32Secret()
                        }
                        step = 2
                    })
                    2 -> SetupStep(
                        identifier = userDisplayName,
                        secret = secretKey,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(secretKey))
                            Toast.makeText(context, "Secret copied", Toast.LENGTH_SHORT).show()
                        },
                        onNext = { step = 3 }
                    )
                    3 -> VerificationStep(
                        code = verificationCode,
                        onCodeChange = { verificationCode = it },
                        isVerifying = isVerifying,
                        onVerify = {
                            if (verificationCode.length == 6) {
                                isVerifying = true
                                // Verify with Cloud Function
                                verifyAuthenticator(uid, verificationCode, secretKey) { success ->
                                    isVerifying = false
                                    if (success) {
                                        db.collection("users").document(uid).update(
                                            mapOf(
                                                "isAuthenticatorEnabled" to true,
                                                "authenticatorSecret" to secretKey
                                            )
                                        ).addOnSuccessListener {
                                            isEnabled = true
                                            Toast.makeText(context, "Authenticator enabled!", Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "Invalid code. Please try again.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun StatusView(onDisable: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = AppGreen, modifier = Modifier.size(80.dp))
        Spacer(Modifier.height(24.dp))
        Text("Authenticator is Active", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(
            "Your account is protected by Google/Microsoft Authenticator. You will need a code from the app for new logins and recovery.",
            fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp)
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onDisable,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30)),
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text("DISABLE AUTHENTICATOR", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun IntroductionStep(onNext: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.VpnKey, contentDescription = null, tint = GreenSage, modifier = Modifier.size(80.dp))
        Spacer(Modifier.height(24.dp))
        Text("Protect Your Account", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(
            "Use an authenticator app like Google Authenticator or Microsoft Authenticator to generate verification codes. This works even if you lose your phone signal or email access.",
            fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 16.dp)
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppGreen)
        ) {
            Text("BEGIN SETUP", fontWeight = FontWeight.Bold, color = Color.Black)
        }
    }
}

 @Composable
fun SetupStep(identifier: String, secret: String, onCopy: () -> Unit, onNext: () -> Unit) {
    // Standard TOTP URI: otpauth://totp/Issuer:AccountName?secret=SECRET&issuer=Issuer
    val label = "WattsHub:$identifier"
    val issuer = "WattsHub"
    val otpUri = "otpauth://totp/${java.net.URLEncoder.encode(label, "UTF-8")}?secret=$secret&issuer=${java.net.URLEncoder.encode(issuer, "UTF-8")}"
    // Double-encode the whole thing for the Google Chart URL
    val qrUrl = "https://chart.googleapis.com/chart?chs=250x250&cht=qr&chl=${java.net.URLEncoder.encode(otpUri, "UTF-8")}"
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Scan QR Code", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(16.dp))
        
        Surface(
            modifier = Modifier.size(220.dp),
            color = Color.White,
            shape = RoundedCornerShape(12.dp)
        ) {
            AsyncImage(
                model = qrUrl,
                contentDescription = "Authenticator QR Code",
                modifier = Modifier.padding(12.dp)
            )
        }
        
        Spacer(Modifier.height(24.dp))
        Text("Can't scan? Use this key:", fontSize = 14.sp, color = Color.Gray)
        
        Row(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(secret, modifier = Modifier.weight(1f), color = AppGreen, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            IconButton(onClick = onCopy) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.LightGray)
            }
        }
        
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppGreen)
        ) {
            Text("I'VE SCANNED IT", fontWeight = FontWeight.Bold, color = Color.Black)
        }
    }
}

@Composable
fun VerificationStep(code: String, onCodeChange: (String) -> Unit, isVerifying: Boolean, onVerify: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Verify Code", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Enter the 6-digit code from your authenticator app", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp, bottom = 24.dp))
        
        OutlinedTextField(
            value = code,
            onValueChange = { if (it.length <= 6) onCodeChange(it) },
            modifier = Modifier.fillMaxWidth(0.7f),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 24.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, letterSpacing = 8.sp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppGreen,
                unfocusedBorderColor = Color.Gray,
                focusedTextColor = Color.White
            )
        )
        
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onVerify,
            enabled = code.length == 6 && !isVerifying,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppGreen)
        ) {
            if (isVerifying) {
                CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
            } else {
                Text("VERIFY & FINISH", fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }
    }
}

fun generateBase32Secret(): String {
    val allowedChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    return (1..16).map { allowedChars.random() }.joinToString("")
}

/**
 * Real verification call via NetworkUtils.
 */
fun verifyAuthenticator(userId: String, code: String, secret: String, onResult: (Boolean) -> Unit) {
    kotlinx.coroutines.MainScope().launch {
        val result = NetworkUtils.verifyAuthenticator(userId, code, secret)
        onResult(result is NetworkResult.Success)
    }
}
