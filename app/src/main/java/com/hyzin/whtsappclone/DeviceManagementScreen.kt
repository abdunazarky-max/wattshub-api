
package com.hyzin.whtsappclone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceManagementScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    var devices by remember { mutableStateOf<List<DeviceInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(userId) {
        devices = NetworkUtils.getActiveDevices(userId)
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Active Devices", color = Color.White) },
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
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF00FF9D))
                }
            } else {
                LazyColumn(modifier = Modifier.padding(16.dp)) {
                    item {
                        Text(
                            "Login history and active sessions",
                            color = Color.Gray,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }

                    items(devices) { device ->
                        DeviceItem(
                            device = device,
                            onLogout = {
                                scope.launch {
                                    val success = NetworkUtils.removeDevice(userId, device.id)
                                    if (success) {
                                        devices = devices.filter { it.id != device.id }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceItem(device: DeviceInfo, onLogout: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        color = Color(0xFF1E1E1E),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (device.status == "online") Color(0xFF00FF9D).copy(alpha = 0.2f) else Color.Gray.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Devices,
                    contentDescription = null,
                    tint = if (device.status == "online") Color(0xFF00FF9D) else Color.Gray
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(device.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    if (device.status == "online") "Active now" else "Last active: ${device.lastActive}",
                    color = if (device.status == "online") Color(0xFF00FF9D) else Color.Gray,
                    fontSize = 12.sp
                )
            }

            IconButton(onClick = onLogout) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout", tint = Color.Red.copy(alpha = 0.8f))
            }
        }
    }
}
