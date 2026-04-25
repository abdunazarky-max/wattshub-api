package com.hyzin.whtsappclone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.MobileFriendly
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.hyzin.whtsappclone.ui.theme.AppGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkedDevicesScreen(onBack: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val uid = auth.currentUser?.uid ?: ""
    
    var activeDeviceName by remember { mutableStateOf("Primary Device") }
    var activeDeviceId by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(uid) {
        if (uid.isNotEmpty()) {
            db.collection("users").document(uid).get().addOnSuccessListener { doc ->
                activeDeviceName = doc.getString("activeDeviceName") ?: "Unknown Device"
                activeDeviceId = doc.getString("activeDeviceId") ?: ""
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Linked Devices", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A))
            )
        },
        containerColor = Color.Black
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Devices, contentDescription = null, tint = AppGreen.copy(alpha = 0.5f), modifier = Modifier.size(120.dp))
            }

            Text("DEVICE STATUS", color = AppGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF1E1E1E),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.MobileFriendly, contentDescription = null, tint = AppGreen)
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(activeDeviceName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("Active Now", color = AppGreen, fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            Text("Use WattsHub on other devices by linking them to your account.", color = Color.Gray, fontSize = 14.sp)
            
            Spacer(Modifier.weight(1f))
            
            Button(
                onClick = { /* In a real app, this would show QR scanner */ },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppGreen),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
            ) {
                Text("LINK A DEVICE", color = Color.Black, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
