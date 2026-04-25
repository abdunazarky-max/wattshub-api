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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.hyzin.whtsappclone.ui.theme.AppGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettingsScreen(
    onBack: () -> Unit,
    onNavigateToThemeSelection: () -> Unit,
    currentTheme: String
) {
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val currentUserId = auth.currentUser?.uid
    
    var privacySettings by remember { mutableStateOf<Map<String, Any>>(emptyMap()) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("") }
    var selectedTitle by remember { mutableStateOf("") }
    
    val sheetState = rememberModalBottomSheetState()

    // Listen to Firestore
    LaunchedEffect(currentUserId) {
        if (currentUserId != null) {
            db.collection("users").document(currentUserId)
                .addSnapshotListener { snapshot, _ ->
                    val data = snapshot?.get("privacy") as? Map<String, Any>
                    if (data != null) {
                        privacySettings = data
                    }
                }
        }
    }

    val updatePrivacy = { category: String, value: Any ->
        if (currentUserId != null) {
            db.collection("users").document(currentUserId)
                .update("privacy.$category", value)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Control", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            SectionHeader("Visibility")
            SectionCard {
                PrivacyListItem(
                    Icons.Default.Visibility, 
                    "Last seen and online", 
                    privacySettings["lastSeen"] as? String ?: "Everyone"
                ) {
                    selectedCategory = "lastSeen"
                    selectedTitle = "Last seen and online"
                    showBottomSheet = true
                }
                PrivacyListItem(
                    Icons.Default.AccountCircle, 
                    "Profile photo", 
                    privacySettings["profilePhoto"] as? String ?: "My contacts"
                ) {
                    selectedCategory = "profilePhoto"
                    selectedTitle = "Profile photo"
                    showBottomSheet = true
                }
                PrivacyListItem(
                    Icons.Default.Info, 
                    "About", 
                    privacySettings["about"] as? String ?: "Everyone"
                ) {
                    selectedCategory = "about"
                    selectedTitle = "About"
                    showBottomSheet = true
                }
                PrivacyListItem(Icons.Default.Palette, "Theme", currentTheme) { onNavigateToThemeSelection() }
                PrivacyListItem(
                    Icons.Default.History, 
                    "Status", 
                    privacySettings["status"] as? String ?: "My contacts"
                ) {
                    selectedCategory = "status"
                    selectedTitle = "Status"
                    showBottomSheet = true
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader("Messaging")
            SectionCard {
                PrivacyListItem(
                    Icons.Default.CheckCircle, 
                    "Read receipts", 
                    "If turned off, you won't send or receive read receipts. Read receipts are always sent for group chats.",
                    isSwitch = true,
                    checked = privacySettings["readReceipts"] as? Boolean ?: true,
                    onCheckedChange = { updatePrivacy("readReceipts", it) }
                )
                PrivacyListItem(
                    Icons.Default.Timer, 
                    "Default message timer", 
                    privacySettings["defaultTimer"] as? String ?: "Off"
                ) {
                    selectedCategory = "defaultTimer"
                    selectedTitle = "Default message timer"
                    showBottomSheet = true
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader("Advanced & Security")
            SectionCard {
                PrivacyListItem(
                    Icons.Default.Group, 
                    "Groups", 
                    privacySettings["groups"] as? String ?: "Everyone"
                ) {
                    selectedCategory = "groups"
                    selectedTitle = "Groups"
                    showBottomSheet = true
                }
                PrivacyListItem(
                    Icons.Default.LocationOn, 
                    "Live location", 
                    privacySettings["liveLocation"] as? String ?: "None"
                ) {
                    selectedCategory = "liveLocation"
                    selectedTitle = "Live location"
                    showBottomSheet = true
                }
                PrivacyListItem(Icons.Default.Block, "Blocked contacts", "0")
                PrivacyListItem(Icons.Default.Fingerprint, "App lock", "Disabled")
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }

        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState,
                containerColor = Color(0xFF1A1A1A),
                dragHandle = { BottomSheetDefaults.DragHandle(color = Color.DarkGray) }
            ) {
                PrivacySelectionSheet(
                    title = selectedTitle,
                    currentValue = privacySettings[selectedCategory] as? String ?: "",
                    options = when (selectedCategory) {
                        "defaultTimer" -> listOf("24 hours", "7 days", "90 days", "Off")
                        "liveLocation" -> listOf("None")
                        else -> listOf("Everyone", "My contacts", "Nobody")
                    },
                    onSelect = {
                        updatePrivacy(selectedCategory, it)
                        showBottomSheet = false
                    }
                )
            }
        }
    }
}

@Composable
fun PrivacySelectionSheet(
    title: String,
    currentValue: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp)
        )
        options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(option) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = (option == currentValue),
                    onClick = { onSelect(option) },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = AppGreen,
                        unselectedColor = Color.Gray
                    )
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(option, color = Color.White, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
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
fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF1A1A1A),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp), content = content)
    }
}

@Composable
fun PrivacyListItem(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    isSwitch: Boolean = false,
    checked: Boolean = true,
    onCheckedChange: (Boolean) -> Unit = {},
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (!isSwitch) onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color.White.copy(alpha = 0.05f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = AppGreen, modifier = Modifier.size(20.dp))
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
        
        if (isSwitch) {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = AppGreen,
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                )
            )
        } else {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.DarkGray, modifier = Modifier.size(20.dp))
        }
    }
}
