package com.hyzin.whtsappclone

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.hyzin.whtsappclone.ui.theme.*
import com.hyzin.whtsappclone.utils.AdminUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit = {},
    onNavigateToEditProfile: () -> Unit = {},
    onNavigateToAccount: () -> Unit = {},
    onNavigateToDevices: () -> Unit = {},
    onNavigateToAdmin: () -> Unit = {},
    onNavigateToAuthenticator: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToStorage: () -> Unit = {},
    onNavigateToLanguage: () -> Unit = {},
    onNavigateToHelp: () -> Unit = {},
    onQuit: () -> Unit = {},
    onDeactivate: () -> Unit = {}
) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    
    val currentUser = auth.currentUser
    val uid = currentUser?.uid ?: ""
    
    var name by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("Available") }
    var profilePicUrl by remember { mutableStateOf("") }
    var coverPicUrl by remember { mutableStateOf("") }
    var showFullCoverImage by remember { mutableStateOf<String?>(null) }
    var showFullProfileImage by remember { mutableStateOf<String?>(null) }

    var statusPrivacy by remember { mutableStateOf("My Contacts") }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(uid) {
        if (uid.isNotEmpty()) {
            db.collection("users").document(uid).addSnapshotListener { snapshot, _ ->
                snapshot?.let {
                    name = it.getString("name") ?: ""
                    bio = it.getString("bio") ?: "Available"
                    profilePicUrl = it.getString("profilePicUrl") ?: ""
                    coverPicUrl = it.getString("coverPicUrl") ?: ""
                    statusPrivacy = it.getString("statusPrivacy") ?: "My Contacts"
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(SkyNightDeep)) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            
            // ── PREMIUM HEADER ──────────────────────────────────────────
            Box(modifier = Modifier.fillMaxWidth().height(260.dp)) {
                // Cover Image / Gradient
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp).background(
                        Brush.verticalGradient(listOf(SkyNightLight, SkyNightDeep))
                    )
                ) {
                    if (coverPicUrl.isNotEmpty()) {
                        AsyncImage(
                            model = coverPicUrl, 
                            contentDescription = null, 
                            contentScale = ContentScale.Crop, 
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(0.5f)
                                .clickable { showFullCoverImage = coverPicUrl }
                        )
                    }
                    
                    // Top Bar Actions
                    Row(
                        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack, modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.1f))) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        IconButton(onClick = { /* Search */ }, modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.1f))) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = Color.White)
                        }
                    }
                }

                // Profile Summary Card (Glassmorphism)
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp).fillMaxWidth().height(100.dp).clickable { onNavigateToEditProfile() },
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(64.dp).clip(CircleShape).clickable { showFullProfileImage = profilePicUrl }, 
                            color = Color.Gray
                        ) {
                            if (profilePicUrl.isNotEmpty()) AsyncImage(model = profilePicUrl, contentDescription = null, contentScale = ContentScale.Crop)
                            else Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.padding(16.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(bio, color = Color.Gray, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = SkyBlueAccent)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── SETTINGS CATEGORIES ──────────────────────────────────────
            SettingsGroup("Account & Security") {

                PremiumSettingsItem(
                    Icons.Default.PrivacyTip, 
                    "Status Privacy", 
                    "Currently: $statusPrivacy", 
                    { showPrivacyDialog = true }
                )

                if (showPrivacyDialog) {
                    AlertDialog(
                        onDismissRequest = { showPrivacyDialog = false },
                        containerColor = Color(0xFF1E1E1E),
                        title = { Text("Who can see my status?", color = Color.White) },
                        text = {
                            Column {
                                listOf("My Contacts", "Public", "Nobody").forEach { option ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { 
                                                statusPrivacy = option
                                                db.collection("users").document(uid).update("statusPrivacy", option)
                                                showPrivacyDialog = false
                                            }
                                            .padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(selected = statusPrivacy == option, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = SkyBlueAccent))
                                        Spacer(Modifier.width(8.dp))
                                        Text(option, color = Color.White)
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showPrivacyDialog = false }) { Text("CANCEL", color = Color.Gray) }
                        }
                    )
                }

                PremiumSettingsItem(Icons.Default.Security, "Privacy Control", "Secure your personal data", onNavigateToAccount)
                PremiumSettingsItem(Icons.Default.Devices, "Linked Devices", "Active sessions and management", onNavigateToDevices)
                PremiumSettingsItem(Icons.Default.VpnKey, "Two-Step Verification", "Highest level of protection", onNavigateToAuthenticator)
                if (AdminUtils.isCurrentUserAdmin()) {
                    PremiumSettingsItem(Icons.Default.AdminPanelSettings, "Admin Panel", "System management and stats", onNavigateToAdmin)
                }
            }

            SettingsGroup("Customization") {
                PremiumSettingsItem(Icons.Default.Palette, "Theme & Appearance", "Custom colors and dark mode", { /* Theme dialog */ })
                PremiumSettingsItem(Icons.Default.Notifications, "Notifications", "Alerts and sound settings", onNavigateToNotifications)
            }

            SettingsGroup("More") {
                PremiumSettingsItem(Icons.Default.BugReport, "Test Notification", "Trigger a manual system alert", {
                    com.hyzin.whtsappclone.utils.NotificationHelper.showNotification(
                        context,
                        "system_test",
                        "WattsHub Test",
                        "Hello! Your notification system is working perfectly! 🚀",
                        "test_chat_id"
                    )
                })
                PremiumSettingsItem(Icons.AutoMirrored.Filled.Help, "Help & Support", "FAQs and contact us", onNavigateToHelp)
                PremiumSettingsItem(Icons.Default.Share, "Invite a Friend", "Share WattsHub with others", {
                    val shareIntent = android.content.Intent().apply {
                        action = android.content.Intent.ACTION_SEND
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, "Join me on WattsHub! Next-gen privacy-first messaging.")
                    }
                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Invite via"))
                })
                PremiumSettingsItem(Icons.AutoMirrored.Filled.ExitToApp, "Logout", "Exit your current session", onLogout, tint = Color(0xFFFF5252))
                PremiumSettingsItem(Icons.Default.Block, "Deactivate Account", "Temporarily disable your profile", onDeactivate, tint = Color(0xFFFF5252))
                PremiumSettingsItem(Icons.Default.PowerSettingsNew, "Quit App", "Close the application", onQuit, tint = Color.Gray)
            }

            Spacer(modifier = Modifier.height(40.dp))
            
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("WattsHub Elite", color = SkyBlueAccent.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Text("v3.1.0 • from Hyzin", color = Color.Gray, fontSize = 11.sp)
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }

        // ── FULL SCREEN IMAGE OVERLAY ──
        val fullScreenUrl = showFullCoverImage ?: showFullProfileImage
        if (fullScreenUrl != null) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { showFullCoverImage = null; showFullProfileImage = null }) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.95f))
                        .clickable { showFullCoverImage = null; showFullProfileImage = null },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = fullScreenUrl,
                        contentDescription = "Full Size View",
                        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f),
                        contentScale = ContentScale.Fit
                    )
                    
                    IconButton(
                        onClick = { showFullCoverImage = null; showFullProfileImage = null },
                        modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).statusBarsPadding()
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title.uppercase(), color = SkyBlueAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
        Surface(shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.03f), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))) {
            Column { content() }
        }
    }
}

@Composable
fun PremiumSettingsItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit, tint: Color = SkyBlueAccent) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color.Gray, fontSize = 12.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.DarkGray)
    }
}
