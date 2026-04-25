package com.hyzin.whtsappclone

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.hyzin.whtsappclone.utils.TotpUtils
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticatorScreen(onBack: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val uid = auth.currentUser?.uid ?: ""
    
    var secretIdentity by remember { mutableStateOf("") }
    var currentOtp by remember { mutableStateOf("000000") }
    var timeLeftSeconds by remember { mutableStateOf(30) }
    var isLoading by remember { mutableStateOf(true) }
    var recoveryCodes by remember { mutableStateOf<List<String>>(emptyList()) }
    var isGeneratingCodes by remember { mutableStateOf(false) }

    fun generateNewRecoveryCodes() {
        val codes = (1..6).map { 
            val p1 = (1000..9999).random()
            val p2 = (1000..9999).random()
            "$p1-$p2"
        }
        isGeneratingCodes = true
        db.collection("users").document(uid).update("recoveryCodes", codes)
            .addOnSuccessListener {
                recoveryCodes = codes
                isGeneratingCodes = false
            }
            .addOnFailureListener { isGeneratingCodes = false }
    }

    // Fetch secret identity once
    LaunchedEffect(uid) {
        if (uid.isNotEmpty()) {
            db.collection("users").document(uid).get().addOnSuccessListener { doc ->
                secretIdentity = doc.getString("secretIdentity") ?: ""
                @Suppress("UNCHECKED_CAST")
                recoveryCodes = doc.get("recoveryCodes") as? List<String> ?: emptyList()
                isLoading = false
            }
        }
    }

    // Update OTP every second to handle countdown and refresh
    LaunchedEffect(secretIdentity) {
        if (secretIdentity.isNotEmpty()) {
            while (true) {
                val now = System.currentTimeMillis()
                timeLeftSeconds = (30 - ((now / 1000) % 30)).toInt()
                currentOtp = TotpUtils.generateTOTP(secretIdentity)
                delay(1000)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Authenticator", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF161616))
            )
        },
        containerColor = Color(0xFF0F0F0F)
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color(0xFF00FF9D))
            } else {
                Text(
                    "Security Verification Code",
                    color = Color.Gray,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Fancy OTP Display
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    color = Color(0xFF1E1E1E),
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Display 6 digits with a space in the middle
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val firstPart = currentOtp.substring(0, 3)
                            val secondPart = currentOtp.substring(3, 6)
                            
                            Text(
                                firstPart,
                                color = Color(0xFF00FF9D),
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 8.sp
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                secondPart,
                                color = Color(0xFF00FF9D),
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 8.sp
                            )
                        }
                        
                        // Countdown Circle
                        Box(modifier = Modifier.padding(top = 24.dp), contentAlignment = Alignment.Center) {
                            val progress by animateFloatAsState(targetValue = timeLeftSeconds / 30f)
                            CircularProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.size(60.dp),
                                color = if (timeLeftSeconds < 5) Color.Red else Color(0xFF00FF9D),
                                strokeWidth = 5.dp,
                                trackColor = Color.White.copy(alpha = 0.1f)
                            )
                            Text(
                                timeLeftSeconds.toString(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Info Card
                Surface(
                    color = Color(0xFF141414),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Color.Gray)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            "Use this code for new device logins. It refreshes every 30 seconds for maximum security.",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(40.dp))
                
                // 🔐 RECOVERY CODES SECTION
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Recovery Codes", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    if (recoveryCodes.isEmpty()) {
                        TextButton(onClick = { generateNewRecoveryCodes() }, enabled = !isGeneratingCodes) {
                            Text("Generate", color = Color(0xFF00FF9D))
                        }
                    } else {
                        IconButton(onClick = { generateNewRecoveryCodes() }, enabled = !isGeneratingCodes) {
                            Icon(Icons.Default.Refresh, contentDescription = "Regenerate", tint = Color.Gray)
                        }
                    }
                }
                
                Text(
                    "Store these codes safely. They can be used to log in if you lose access to this phone.",
                    color = Color.Gray, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                )

                if (recoveryCodes.isNotEmpty()) {
                    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                        modifier = Modifier.height(180.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(recoveryCodes.size) { index ->
                            Surface(
                                color = Color(0xFF121212),
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.05f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.padding(4.dp)
                            ) {
                                Text(
                                    recoveryCodes[index],
                                    color = Color.LightGray,
                                    fontSize = 15.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    modifier = Modifier.padding(12.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                } else if (!isGeneratingCodes) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(100.dp).background(Color(0xFF121212), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No recovery codes yet", color = Color.DarkGray)
                    }
                }

                if (isGeneratingCodes) {
                    CircularProgressIndicator(color = Color(0xFF00FF9D), modifier = Modifier.size(24.dp))
                }
                
                Spacer(modifier = Modifier.height(48.dp))
                
                Text(
                    "Secret Key: $secretIdentity",
                    color = Color.DarkGray,
                    fontSize = 10.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
