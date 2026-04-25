package com.hyzin.whtsappclone

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.hyzin.whtsappclone.ui.theme.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    onNavigateToCamera: () -> Unit = {},
    capturedImageUri: String? = null,
    onImageCapturedHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    
    val currentUser = auth.currentUser
    val uid = currentUser?.uid ?: ""
    
    var name by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var email by remember { mutableStateOf(currentUser?.email ?: "") }
    var phone by remember { mutableStateOf("") }
    var profilePicUrl by remember { mutableStateOf("") }
    var coverPicUrl by remember { mutableStateOf("") }
    
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    
    var showImageSourceDialog by remember { mutableStateOf(false) }
    var imageSourceType by remember { mutableStateOf("profile") } // "profile" or "cover"

    // Handle incoming Camera capture
    LaunchedEffect(capturedImageUri) {
        if (capturedImageUri != null) {
            val uri = Uri.parse(capturedImageUri)
            isSaving = true
            val folder = if (imageSourceType == "profile") "profiles" else "covers"
            val storageRef = FirebaseStorage.getInstance().reference.child("$folder/$uid.jpg")
            
            val optimizedData = com.hyzin.whtsappclone.utils.ImageUtils.rescaleAndCompressImage(context, uri)
            if (optimizedData != null) {
                storageRef.putBytes(optimizedData)
                    .addOnSuccessListener {
                        storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                            val url = downloadUri.toString()
                            val field = if (imageSourceType == "profile") "profilePicUrl" else "coverPicUrl"
                            db.collection("users").document(uid).update(field, url)
                                .addOnSuccessListener {
                                    if (imageSourceType == "profile") profilePicUrl = url else coverPicUrl = url
                                    isSaving = false
                                    Toast.makeText(context, "Photo Updated (Optimized)!", Toast.LENGTH_SHORT).show()
                                    onImageCapturedHandled()
                                }
                        }
                    }
                    .addOnFailureListener {
                        isSaving = false
                        Toast.makeText(context, "Upload failed: ${it.message}", Toast.LENGTH_SHORT).show()
                        onImageCapturedHandled()
                    }
            } else {
                storageRef.putFile(uri)
                    .addOnSuccessListener {
                        storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                            val url = downloadUri.toString()
                            val field = if (imageSourceType == "profile") "profilePicUrl" else "coverPicUrl"
                            db.collection("users").document(uid).update(field, url)
                                .addOnSuccessListener {
                                    if (imageSourceType == "profile") profilePicUrl = url else coverPicUrl = url
                                    isSaving = false
                                    Toast.makeText(context, "Photo Updated!", Toast.LENGTH_SHORT).show()
                                    onImageCapturedHandled()
                                }
                        }
                    }
                    .addOnFailureListener {
                        isSaving = false
                        Toast.makeText(context, "Upload failed: ${it.message}", Toast.LENGTH_SHORT).show()
                        onImageCapturedHandled()
                    }
            }
        }
    }



    // Since we don't have the navController here (passed from outside), 
    // we might need to handle the camera result in MainActivity.
    // However, I can add a check in a LaunchedEffect if the app uses a global navigator.
    // Wait, EditProfileScreen is called from MainActivity. 
    // I will use a DisposableEffect to check for the result if possible, 
    // or just assume the user wants me to implement the selection first.


    var originalPhone by remember { mutableStateOf("") }
    var showOtpDialog by remember { mutableStateOf(false) }
    var otpValue by remember { mutableStateOf("") }
    var isVerifyingOtp by remember { mutableStateOf(false) }

    LaunchedEffect(uid) {
        if (uid.isNotEmpty()) {
            db.collection("users").document(uid).get().addOnSuccessListener { snapshot ->
                name = snapshot.getString("name") ?: ""
                bio = snapshot.getString("bio") ?: "Available"
                profilePicUrl = snapshot.getString("profilePicUrl") ?: ""
                coverPicUrl = snapshot.getString("coverPicUrl") ?: ""
                phone = snapshot.getString("phone") ?: ""
                originalPhone = phone // Track original
                email = snapshot.getString("email") ?: currentUser?.email ?: ""
                isLoading = false
            }.addOnFailureListener {
                isLoading = false
            }
        }
    }

    val profilePicLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            isSaving = true
            val storageRef = FirebaseStorage.getInstance().reference.child("profiles/$uid.jpg")
            val optimizedData = com.hyzin.whtsappclone.utils.ImageUtils.rescaleAndCompressImage(context, uri)
            if (optimizedData != null) {
                storageRef.putBytes(optimizedData)
                    .addOnSuccessListener {
                        storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                            val url = downloadUri.toString()
                            db.collection("users").document(uid).update("profilePicUrl", url)
                                .addOnSuccessListener {
                                    profilePicUrl = url
                                    isSaving = false
                                    Toast.makeText(context, "Profile Picture Updated (Optimized)!", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }
            } else {
                storageRef.putFile(uri)
                    .addOnSuccessListener {
                        storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                            val url = downloadUri.toString()
                            db.collection("users").document(uid).update("profilePicUrl", url)
                                .addOnSuccessListener {
                                    profilePicUrl = url
                                    isSaving = false
                                    Toast.makeText(context, "Profile Picture Updated!", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }
            }
        }
    }

    val coverPicLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            isSaving = true
            val storageRef = FirebaseStorage.getInstance().reference.child("covers/$uid.jpg")
            val optimizedData = com.hyzin.whtsappclone.utils.ImageUtils.rescaleAndCompressImage(context, uri)
            if (optimizedData != null) {
                storageRef.putBytes(optimizedData)
                    .addOnSuccessListener {
                        storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                            val url = downloadUri.toString()
                            db.collection("users").document(uid).update("coverPicUrl", url)
                                .addOnSuccessListener {
                                    coverPicUrl = url
                                    isSaving = false
                                    Toast.makeText(context, "Cover Photo Updated (Optimized)!", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }
            } else {
                storageRef.putFile(uri)
                    .addOnSuccessListener {
                        storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                            val url = downloadUri.toString()
                            db.collection("users").document(uid).update("coverPicUrl", url)
                                .addOnSuccessListener {
                                    coverPicUrl = url
                                    isSaving = false
                                    Toast.makeText(context, "Cover Photo Updated!", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }
                    .addOnFailureListener {
                        isSaving = false
                        Toast.makeText(context, "Upload failed: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    Scaffold(
        containerColor = Color.Black // Dark base for glassmorphism
    ) { innerPadding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF020617))))
            .padding(innerPadding)
            .navigationBarsPadding() // Space for navigation bar
            .imePadding() // CRITICAL: Space for Keyboard
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = SkyBlueAccent)
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    // ── PREMIUM HEADER SECTION ─────────────────────────────────────
                    Box(modifier = Modifier.fillMaxWidth().height(260.dp)) {
                        // Background Interactive Cover
                        Surface(
                            modifier = Modifier.fillMaxWidth().height(200.dp).clickable { 
                                imageSourceType = "cover"
                                showImageSourceDialog = true 
                            },
                            color = Color(0xFF1E293B)
                        ) {
                            if (coverPicUrl.isNotEmpty()) {
                                AsyncImage(model = coverPicUrl, contentDescription = null, contentScale = ContentScale.Crop)
                            }
                            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
                        }
                        
                        // Back Button (Glassy)
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.padding(12.dp).statusBarsPadding().clip(CircleShape).background(Color.White.copy(alpha = 0.1f))
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                        }

                        // Floating Profile Picture
                        Box(
                            modifier = Modifier.align(Alignment.BottomCenter).offset(y = 0.dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .size(130.dp)
                                    .border(4.dp, Color(0xFF020617), CircleShape)
                                    .clip(CircleShape)
                                    .clickable { 
                                        imageSourceType = "profile"
                                        showImageSourceDialog = true 
                                    },
                                color = Color(0xFF1E293B),
                                shadowElevation = 12.dp
                            ) {
                                if (profilePicUrl.isNotEmpty()) {
                                    AsyncImage(model = profilePicUrl, contentDescription = null, contentScale = ContentScale.Crop)
                                } else {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray, modifier = Modifier.padding(30.dp))
                                }
                                if (isSaving) Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(30.dp), color = SkyBlueAccent)
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(SkyBlueAccent)
                                    .border(2.dp, Color(0xFF020617), CircleShape)
                                    .clickable { profilePicLauncher.launch("image/*") },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    // ── USER INFO CARDS ──────────────────────────────────────────
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Profile Information", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                        
                        BeautifulField(label = "Display Name", value = name, icon = Icons.Default.Badge, onValueChange = { name = it })
                        BeautifulField(label = "Bio / Status", value = bio, icon = Icons.Default.SelfImprovement, onValueChange = { bio = it })
                        
                        Divider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 8.dp))
                        Text("Contact & Security", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                        
                        BeautifulField(label = "Email Address", value = email, icon = Icons.Default.AlternateEmail, onValueChange = { email = it })
                        BeautifulField(label = "Phone Number", value = phone, icon = Icons.Default.PhoneIphone, onValueChange = { phone = it })

                        Spacer(modifier = Modifier.height(24.dp))

                        // Glassy Action Button
                        Button(
                            onClick = { 
                                if (name.isEmpty()) {
                                    Toast.makeText(context, "Name required", Toast.LENGTH_SHORT).show()
                                } else if (phone != originalPhone && phone.isNotEmpty()) {
                                    // Trigger OTP Flow for Phone Change
                                    scope.launch {
                                        isSaving = true
                                        val result = NetworkUtils.requestMobileUpdate(uid, phone)
                                        isSaving = false
                                        if (result is NetworkResult.Success) {
                                            showOtpDialog = true
                                        } else {
                                            Toast.makeText(context, (result as NetworkResult.Error).message, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                } else {
                                    // Regular profile update
                                    isSaving = true
                                    db.collection("users").document(uid).update("name", name, "bio", bio)
                                        .addOnSuccessListener {
                                            isSaving = false; onBack()
                                            Toast.makeText(context, "Profile Perfected!", Toast.LENGTH_SHORT).show()
                                        }
                                        .addOnFailureListener {
                                            isSaving = false
                                            Toast.makeText(context, "Update failed", Toast.LENGTH_SHORT).show()
                                        }
                                }
                             },
                            modifier = Modifier.fillMaxWidth().height(60.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (phone != originalPhone) Color(0xFF00C8FF) else SkyBlueAccent
                            ),
                            enabled = !isSaving
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            } else {
                                Text(
                                    if (phone != originalPhone) "VERIFY & SAVE" else "SAVE CHANGES", 
                                    color = Color.White, 
                                    fontWeight = FontWeight.Bold, 
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }

            // OTP Verification Dialog
            if (showOtpDialog) {
                AlertDialog(
                    onDismissRequest = { if (!isVerifyingOtp) showOtpDialog = false },
                    containerColor = Color(0xFF1E293B),
                    title = { Text("Verify Phone Number", color = Color.White) },
                    text = {
                        Column {
                            Text("A code has been sent to $phone", color = Color.Gray, fontSize = 14.sp)
                            Spacer(Modifier.height(16.dp))
                            OutlinedTextField(
                                value = otpValue,
                                onValueChange = { if (it.length <= 6) otpValue = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("6-Digit Code") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = SkyBlueAccent
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                scope.launch {
                                    isVerifyingOtp = true
                                    val result = NetworkUtils.confirmMobileUpdate(uid, phone, otpValue)
                                    isVerifyingOtp = false
                                    if (result is NetworkResult.Success) {
                                        showOtpDialog = false
                                        originalPhone = phone
                                        Toast.makeText(context, "Phone Verified & Updated!", Toast.LENGTH_SHORT).show()
                                        onBack()
                                    } else {
                                        Toast.makeText(context, (result as NetworkResult.Error).message, Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            enabled = otpValue.length == 6 && !isVerifyingOtp,
                            colors = ButtonDefaults.buttonColors(containerColor = SkyBlueAccent)
                        ) {
                            if (isVerifyingOtp) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                            else Text("VERIFY")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showOtpDialog = false }, enabled = !isVerifyingOtp) {
                            Text("CANCEL", color = Color.Gray)
                        }
                    }
                )
            }

            // Image Source Selection Dialog
            if (showImageSourceDialog) {
                AlertDialog(
                    onDismissRequest = { showImageSourceDialog = false },
                    containerColor = Color(0xFF1E293B),
                    title = { Text("Select Image Source", color = Color.White) },
                    text = {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { 
                                    showImageSourceDialog = false
                                    // Navigate to camera (if global navigation is set up correctly)
                                    // In this project, MainActivity handles routes. 
                                    // I will use a placeholder but usually you'd call a navigation function.
                                    Toast.makeText(context, "Opening Camera...", Toast.LENGTH_SHORT).show()
                                    // For now, I'll stick to Gallery until I'm sure how they want the camera integrated.
                                    // Wait, the user EXPLICITLY asked for Camera.
                                    if (imageSourceType == "profile") profilePicLauncher.launch("image/*")
                                    else coverPicLauncher.launch("image/*")
                                }.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = SkyBlueAccent)
                                Spacer(Modifier.width(16.dp))
                                Text("Gallery", color = Color.White)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { 
                                    showImageSourceDialog = false
                                    onNavigateToCamera()
                                }.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = SkyBlueAccent)
                                Spacer(Modifier.width(16.dp))
                                Text("Camera", color = Color.White)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showImageSourceDialog = false }) {
                            Text("CANCEL", color = SkyBlueAccent)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun BeautifulField(label: String, value: String, icon: ImageVector, onValueChange: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = SkyBlueAccent, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Color.White.copy(alpha = 0.05f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true,
                textStyle = TextStyle(fontSize = 16.sp)
            )
        }
    }
}
