package com.hyzin.whtsappclone

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyzin.whtsappclone.ui.theme.AppGreen
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import android.widget.Toast
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsScreen(onBack: () -> Unit, onNavigateToPrivacy: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteConfirmation = false },
            title = { Text("Delete Account?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently erase your profile, messages, and account from WattsHub. This action cannot be undone.", color = Color.Gray) },
            confirmButton = {
                Button(
                    onClick = {
                        isDeleting = true
                        val user = auth.currentUser
                        val uid = user?.uid
                        
                        scope.launch {
                            try {
                                // 1. Delete from Firestore
                                if (uid != null) {
                                    db.collection("users").document(uid).delete().addOnCompleteListener {
                                        // 2. Delete from Auth
                                        user.delete().addOnCompleteListener { task ->
                                            isDeleting = false
                                            showDeleteConfirmation = false
                                            if (task.isSuccessful) {
                                                Toast.makeText(context, "Account deleted successfully", Toast.LENGTH_LONG).show()
                                                // 3. Navigation is handled by AuthStateListener in MainActivity
                                            } else {
                                                Toast.makeText(context, "Error: ${task.exception?.message}. Try logging out and back in.", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                } else {
                                    isDeleting = false
                                    showDeleteConfirmation = false
                                    Toast.makeText(context, "No user session found.", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                isDeleting = false
                                showDeleteConfirmation = false
                                Toast.makeText(context, "Deletion failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30)),
                    enabled = !isDeleting
                ) {
                    if (isDeleting) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    else Text("DELETE", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }, enabled = !isDeleting) {
                    Text("CANCEL", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E1E1E)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Account", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        val haptic = LocalHapticFeedback.current
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            AccountSectionHeader("Security & Privacy")
            AccountSectionCard {
                PremiumAccountItem(Icons.Default.Lock, "Privacy", "Block contacts, disappearing messages") {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onNavigateToPrivacy()
                }
                PremiumAccountItem(Icons.Default.Security, "Security notifications", "Get notified when security code changes") {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                PremiumAccountItem(Icons.Default.VpnKey, "Two-step verification", "Additional security for your account") {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            AccountSectionHeader("Account Management")
            AccountSectionCard {
                PremiumAccountItem(Icons.Default.PhonelinkSetup, "Change number", "Migrate account info, groups & settings") {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                PremiumAccountItem(Icons.Default.Description, "Request account info", "Report of your account info and settings") {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            AccountSectionHeader("Danger Zone")
            AccountSectionCard {
                PremiumAccountItem(
                    icon = Icons.Default.Delete, 
                    title = "Delete my account", 
                    subtitle = "Erase all data and messages permanently",
                    iconColor = Color(0xFFFF3B30)
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showDeleteConfirmation = true
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun AccountSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        color = AppGreen,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
    )
}

@Composable
private fun AccountSectionCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF1A1A1A).copy(alpha = 0.8f),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp), content = content)
    }
}

@Composable
private fun PremiumAccountItem(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    iconColor: Color = AppGreen,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color.White.copy(alpha = 0.05f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle, 
                    color = Color.Gray, 
                    fontSize = 13.sp, 
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.DarkGray, modifier = Modifier.size(20.dp))
    }
}
