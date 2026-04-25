package com.hyzin.whtsappclone

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpCenter
import androidx.compose.material.icons.automirrored.filled.ContactSupport
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyzin.whtsappclone.ui.theme.AppGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefManager = remember { com.hyzin.whtsappclone.utils.PreferenceManager(context) }
    
    var notificationsEnabled by remember { mutableStateOf(prefManager.getNotificationsEnabled()) }
    var soundsEnabled by remember { mutableStateOf(prefManager.getSoundsEnabled()) }

    Scaffold(
        topBar = { SettingsHeader("Notifications", onBack) },
        containerColor = Color.Black
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).verticalScroll(rememberScrollState())) {
            
            // Global Notifications Toggle
            SettingsToggle(
                "Enable Notifications", 
                "Receive alerts for incoming messages and calls", 
                notificationsEnabled,
                onCheckedChange = { 
                    notificationsEnabled = it
                    prefManager.setNotificationsEnabled(it)
                }
            )

            // Conversation Tones Toggle
            SettingsToggle(
                "Conversation tones", 
                "Play sounds for incoming and outgoing messages", 
                soundsEnabled,
                onCheckedChange = {
                    soundsEnabled = it
                    prefManager.setSoundsEnabled(it)
                }
            )
            
            HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)
            Text("MESSAGES", color = AppGreen, modifier = Modifier.padding(16.dp), fontSize = 14.sp)
            SettingsMenu("Notification tone", "Default (Chime)")
            SettingsMenu("Vibrate", "Default")
            
            Text("GROUPS", color = AppGreen, modifier = Modifier.padding(16.dp), fontSize = 14.sp)
            SettingsMenu("Notification tone", "Default (Chime)")
            SettingsMenu("Vibrate", "Default")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageDataScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = { SettingsHeader("Storage and Data", onBack) },
        containerColor = Color.Black
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).verticalScroll(rememberScrollState())) {
            SettingsMenu("Network usage", "4.2 GB sent • 12.1 GB received", Icons.Default.DataUsage)
            SettingsToggle("Use less data for calls", "", false)
            HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))
            Text("MEDIA AUTO-DOWNLOAD", color = Color.Gray, modifier = Modifier.padding(16.dp), fontSize = 13.sp)
            SettingsMenu("When using mobile data", "Photos")
            SettingsMenu("When connected on Wi-Fi", "All media")
            SettingsMenu("When roaming", "No media")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLanguageScreen(onBack: () -> Unit) {
    val languages = listOf("English", "Hindi", "Spanish", "French", "Arabic", "Russian", "German")
    var selected by remember { mutableStateOf("English") }
    
    Scaffold(
        topBar = { SettingsHeader("App Language", onBack) },
        containerColor = Color.Black
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            languages.forEach { lang ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { selected = lang }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = (lang == selected), onClick = { selected = lang }, colors = RadioButtonDefaults.colors(selectedColor = AppGreen))
                    Spacer(Modifier.width(16.dp))
                    Text(lang, color = Color.White, fontSize = 16.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = { SettingsHeader("Help", onBack) },
        containerColor = Color.Black
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            SettingsMenu("Help Center", "Get answers to common questions", Icons.AutoMirrored.Filled.HelpCenter)
            SettingsMenu("Contact us", "Questions? Need help?", Icons.AutoMirrored.Filled.ContactSupport)
            SettingsMenu("Terms and Privacy Policy", "", Icons.Default.Description)
            SettingsMenu("App info", "", Icons.Default.Info)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHeader(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title, color = Color.White) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A))
    )
}

@Composable
fun SettingsMenu(title: String, subtitle: String = "", icon: ImageVector? = null) {
    Row(modifier = Modifier.fillMaxWidth().clickable {}.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.Gray)
            Spacer(Modifier.width(16.dp))
        }
        Column {
            Text(title, color = Color.White, fontSize = 16.sp)
            if (subtitle.isNotEmpty()) Text(subtitle, color = Color.Gray, fontSize = 13.sp)
        }
    }
}

@Composable
fun SettingsToggle(
    title: String, 
    subtitle: String, 
    initial: Boolean, 
    onCheckedChange: ((Boolean) -> Unit)? = null
) {
    var checked by remember(initial) { mutableStateOf(initial) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { 
                checked = !checked 
                onCheckedChange?.invoke(checked)
            }
            .padding(16.dp), 
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp)
            if (subtitle.isNotEmpty()) Text(subtitle, color = Color.Gray, fontSize = 13.sp)
        }
        Switch(
            checked = checked, 
            onCheckedChange = { 
                checked = it
                onCheckedChange?.invoke(it)
            }, 
            colors = SwitchDefaults.colors(checkedThumbColor = AppGreen)
        )
    }
}
