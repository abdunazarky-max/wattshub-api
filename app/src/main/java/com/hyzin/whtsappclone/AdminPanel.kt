package com.hyzin.whtsappclone

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.hyzin.whtsappclone.ui.theme.AppGreen
import java.text.SimpleDateFormat
import java.util.*

// ── Data Models ────────────────────────────────────────────────────────────────

data class AdminUserItem(
    val uid: String,
    val name: String,
    val email: String,
    val phone: String = "",
    val status: String = "offline",
    val isDeactivated: Boolean = false,
    val createdAt: Timestamp? = null
)

data class AdminGroupItem(
    val gid: String,
    val name: String,
    val memberCount: Int,
    val createdAt: Timestamp? = null
)

// Represents a set of duplicate accounts sharing the same email or phone
data class DuplicateGroupItem(
    val key: String,          // the shared email or phone
    val type: String,         // "email" or "phone"
    val accounts: List<AdminUserItem>  // sorted oldest first
)

data class AdminUserEarnings(
    val uid: String,
    val name: String,
    val balance: Double = 0.0,
    val totalEarnings: Double = 0.0,
    val lastPayout: Timestamp? = null
)

// ── Main Screen ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(onBack: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current

    // ── State ──
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Overview", "Users", "Groups", "Controls", "Earnings")

    var users by remember { mutableStateOf<List<AdminUserItem>>(emptyList()) }
    var groups by remember { mutableStateOf<List<AdminGroupItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Controls state
    var showBroadcastDialog by remember { mutableStateOf(false) }
    var broadcastMessage by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    var showMotdDialog by remember { mutableStateOf(false) }
    var motdText by remember { mutableStateOf("") }
    var motdEnabled by remember { mutableStateOf(false) }

    var minVersionText by remember { mutableStateOf("") }
    var latestVersionText by remember { mutableStateOf("") }
    var showVersionDialog by remember { mutableStateOf(false) }

    // Duplicate accounts state
    var showDuplicatesDialog by remember { mutableStateOf(false) }
    var duplicateGroups by remember { mutableStateOf<List<DuplicateGroupItem>>(emptyList()) }
    var isDuplicateScanRunning by remember { mutableStateOf(false) }

    fun scanForDuplicates() {
        isDuplicateScanRunning = true
        // 1. Group by Email
        val byEmail = users
            .filter { it.email.isNotBlank() }
            .groupBy { it.email.trim().lowercase() }
            .filter { it.value.size > 1 }
            .map { (k, v) ->
                DuplicateGroupItem(
                    key = k, type = "email",
                    accounts = v.sortedWith(compareBy({ it.createdAt?.seconds ?: Long.MAX_VALUE }, { it.uid }))
                )
            }
        // 2. Group by Phone
        val byPhone = users
            .filter { it.phone.isNotBlank() }
            .groupBy { it.phone.trim() }
            .filter { it.value.size > 1 }
            .map { (k, v) ->
                DuplicateGroupItem(
                    key = k, type = "phone",
                    accounts = v.sortedWith(compareBy({ it.createdAt?.seconds ?: Long.MAX_VALUE }, { it.uid }))
                )
            }
        // Merge and deduplicate group keys (email or phone)
        duplicateGroups = (byEmail + byPhone).distinctBy { it.key }
        isDuplicateScanRunning = false
        showDuplicatesDialog = true
    }

    // User tab state
    var userSearch by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf<AdminUserItem?>(null) }
    var showUserDetail by remember { mutableStateOf<AdminUserItem?>(null) }

    // Group tab state
    var showDeleteGroupConfirm by remember { mutableStateOf<AdminGroupItem?>(null) }

    var userEarnings by remember { mutableStateOf<List<AdminUserEarnings>>(emptyList()) }
    var showEditEarnings by remember { mutableStateOf<AdminUserEarnings?>(null) }

    // ── Realtime Data ──
    LaunchedEffect(Unit) {
        db.collection("users").addSnapshotListener { snap, _ ->
            if (snap != null) {
                val userList = snap.documents.map {
                    AdminUserItem(
                        uid = it.id,
                        name = it.getString("name") ?: "Unknown",
                        email = it.getString("email") ?: "",
                        phone = it.getString("phone") ?: "",
                        status = it.getString("status") ?: "offline",
                        isDeactivated = it.getBoolean("isDeactivated") ?: false,
                        createdAt = it.getTimestamp("createdAt")
                    )
                }
                users = userList
                
                // Also update earnings state
                userEarnings = snap.documents.map {
                    AdminUserEarnings(
                        uid = it.id,
                        name = it.getString("name") ?: "Unknown",
                        balance = it.getDouble("balance") ?: 0.0,
                        totalEarnings = it.getDouble("totalEarnings") ?: 0.0,
                        lastPayout = it.getTimestamp("lastPayout")
                    )
                }.sortedByDescending { it.balance }
            }
        }
        db.collection("groups").addSnapshotListener { snap, _ ->
            if (snap != null) {
                groups = snap.documents.map {
                    AdminGroupItem(
                        gid = it.id,
                        name = it.getString("name") ?: "Unnamed Group",
                        memberCount = (it.get("members") as? List<*>)?.size ?: 0,
                        createdAt = it.getTimestamp("timestamp")
                    )
                }
            }
            isLoading = false
        }
        // Load app config
        db.collection("app_config").document("settings").get().addOnSuccessListener { doc ->
            motdText = doc.getString("motd") ?: ""
            motdEnabled = doc.getBoolean("motd_enabled") ?: false
            minVersionText = doc.getString("min_version") ?: ""
            latestVersionText = doc.getString("latest_version") ?: ""
        }
    }

    // ── Computed Stats ──
    val activeUsers = users.count { it.status == "online" }
    val bannedUsers = users.count { it.isDeactivated }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            tint = AppGreen,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Admin Panel", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0D0D0D))
            )
        },
        containerColor = Color(0xFF0D0D0D)
    ) { innerPadding ->

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppGreen)
            }
            return@Scaffold
        }

        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {

            // ── Tab Row ──
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF1A1A1A),
                contentColor = AppGreen,
                edgePadding = 0.dp,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = AppGreen
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                color = if (selectedTab == index) AppGreen else Color.Gray,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        }
                    )
                }
            }

            // ── Tab Content ──
            when (selectedTab) {
                0 -> OverviewTab(users, groups, activeUsers, bannedUsers)
                1 -> UsersTab(
                    users = users,
                    search = userSearch,
                    onSearchChange = { userSearch = it },
                    onToggleBan = { user ->
                        db.collection("users").document(user.uid)
                            .update("isDeactivated", !user.isDeactivated)
                            .addOnSuccessListener {
                                Toast.makeText(context, if (user.isDeactivated) "User Unbanned" else "User Banned", Toast.LENGTH_SHORT).show()
                            }
                    },
                    onDelete = { showDeleteConfirm = it },
                    onViewDetail = { showUserDetail = it }
                )
                2 -> GroupsTab(
                    groups = groups,
                    onDeleteGroup = { showDeleteGroupConfirm = it }
                )
                3 -> ControlsTab(
                    onBroadcast = { showBroadcastDialog = true },
                    motdText = motdText,
                    motdEnabled = motdEnabled,
                    onMotdEdit = { showMotdDialog = true },
                    onToggleMotd = { enabled ->
                        motdEnabled = enabled
                        db.collection("app_config").document("settings")
                            .update("motd_enabled", enabled)
                    },
                    minVersion = minVersionText,
                    latestVersion = latestVersionText,
                    onVersionEdit = { showVersionDialog = true },
                    userCount = users.size,
                    bannedCount = bannedUsers,
                    onFindDuplicates = { scanForDuplicates() },
                    isDuplicateScanRunning = isDuplicateScanRunning
                )
                4 -> EarningsTab(
                    earnings = userEarnings,
                    onEdit = { showEditEarnings = it }
                )
            }
        }
    }

    // ── Edit Earnings Dialog ──
    showEditEarnings?.let { earning ->
        var editBalance by remember { mutableStateOf(earning.balance.toString()) }
        var isSavingEarnings by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showEditEarnings = null },
            containerColor = Color(0xFF1E1E1E),
            title = { Text("Manage Earnings: ${earning.name}", color = Color.White) },
            text = {
                Column {
                    Text("Current Balance: $${"%.2f".format(earning.balance)}", color = Color.Gray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = editBalance,
                        onValueChange = { editBalance = it },
                        label = { Text("New Balance ($)", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = AppGreen, unfocusedBorderColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newBal = editBalance.toDoubleOrNull()
                        if (newBal != null) {
                            isSavingEarnings = true
                            db.collection("users").document(earning.uid)
                                .update("balance", newBal)
                                .addOnSuccessListener {
                                    isSavingEarnings = false
                                    showEditEarnings = null
                                    Toast.makeText(context, "Balance updated!", Toast.LENGTH_SHORT).show()
                                }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppGreen),
                    enabled = !isSavingEarnings
                ) {
                    Text("Update", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditEarnings = null }) { Text("Cancel", color = Color.Gray) }
            }
        )
    }

    // ── Broadcast Dialog ──
    if (showBroadcastDialog) {
        AlertDialog(
            onDismissRequest = { showBroadcastDialog = false },
            containerColor = Color(0xFF1E1E1E),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Campaign, contentDescription = null, tint = AppGreen, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Broadcast Message", color = Color.White)
                }
            },
            text = {
                OutlinedTextField(
                    value = broadcastMessage,
                    onValueChange = { broadcastMessage = it },
                    placeholder = { Text("Enter message for all users...", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = AppGreen,
                        unfocusedBorderColor = Color.Gray
                    ),
                    modifier = Modifier.fillMaxWidth().height(130.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (broadcastMessage.isNotBlank()) {
                            isSending = true
                            db.collection("system_messages").add(
                                mapOf(
                                    "message" to broadcastMessage,
                                    "timestamp" to Timestamp.now(),
                                    "type" to "broadcast"
                                )
                            ).addOnSuccessListener {
                                isSending = false
                                showBroadcastDialog = false
                                broadcastMessage = ""
                                Toast.makeText(context, "Broadcast Sent to All Users!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppGreen),
                    enabled = !isSending && broadcastMessage.isNotBlank()
                ) { Text(if (isSending) "Sending..." else "Send to All", color = Color.Black, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showBroadcastDialog = false }) { Text("Cancel", color = Color.Gray) }
            }
        )
    }

    // ── MOTD Dialog ──
    if (showMotdDialog) {
        var editMotd by remember { mutableStateOf(motdText) }
        AlertDialog(
            onDismissRequest = { showMotdDialog = false },
            containerColor = Color(0xFF1E1E1E),
            title = { Text("Edit Announcement / MOTD", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = editMotd,
                    onValueChange = { editMotd = it },
                    placeholder = { Text("e.g. WattsHub v2.0 coming soon!", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = AppGreen, unfocusedBorderColor = Color.Gray
                    ),
                    modifier = Modifier.fillMaxWidth().height(110.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        motdText = editMotd
                        db.collection("app_config").document("settings")
                            .update("motd", editMotd)
                            .addOnSuccessListener {
                                showMotdDialog = false
                                Toast.makeText(context, "Announcement Updated!", Toast.LENGTH_SHORT).show()
                            }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppGreen)
                ) { Text("Save", color = Color.Black, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showMotdDialog = false }) { Text("Cancel", color = Color.Gray) }
            }
        )
    }

    // ── Version Dialog ──
    if (showVersionDialog) {
        var editMin by remember { mutableStateOf(minVersionText) }
        var editLatest by remember { mutableStateOf(latestVersionText) }
        AlertDialog(
            onDismissRequest = { showVersionDialog = false },
            containerColor = Color(0xFF1E1E1E),
            title = { Text("App Version Control", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Min Required Version (force update threshold):", color = Color.Gray, fontSize = 12.sp)
                    OutlinedTextField(
                        value = editMin,
                        onValueChange = { editMin = it },
                        placeholder = { Text("e.g. 1.2.0", color = Color.Gray) },
                        label = { Text("Min Version", color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = AppGreen, unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = AppGreen
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editLatest,
                        onValueChange = { editLatest = it },
                        placeholder = { Text("e.g. 1.5.0", color = Color.Gray) },
                        label = { Text("Latest Version", color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = AppGreen, unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = AppGreen
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        minVersionText = editMin
                        latestVersionText = editLatest
                        db.collection("app_config").document("settings")
                            .update(mapOf("min_version" to editMin, "latest_version" to editLatest))
                            .addOnSuccessListener {
                                showVersionDialog = false
                                Toast.makeText(context, "Version Config Updated!", Toast.LENGTH_SHORT).show()
                            }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppGreen)
                ) { Text("Save", color = Color.Black, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showVersionDialog = false }) { Text("Cancel", color = Color.Gray) }
            }
        )
    }

    // ── Delete User Confirm ──
    showDeleteConfirm?.let { user ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            containerColor = Color(0xFF1E1E1E),
            title = { Text("Delete User?", color = Color.White) },
            text = { Text("Permanently delete \"${user.name}\"? This cannot be undone.", color = Color.LightGray) },
            confirmButton = {
                Button(
                    onClick = {
                        db.collection("users").document(user.uid).delete()
                            .addOnSuccessListener {
                                showDeleteConfirm = null
                                Toast.makeText(context, "User deleted.", Toast.LENGTH_SHORT).show()
                            }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) { Text("Delete", color = Color.White, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancel", color = Color.Gray) }
            }
        )
    }

    // ── Delete Group Confirm ──
    showDeleteGroupConfirm?.let { group ->
        AlertDialog(
            onDismissRequest = { showDeleteGroupConfirm = null },
            containerColor = Color(0xFF1E1E1E),
            title = { Text("Delete Group?", color = Color.White) },
            text = { Text("Permanently delete group \"${group.name}\"? All messages will be lost.", color = Color.LightGray) },
            confirmButton = {
                Button(
                    onClick = {
                        db.collection("groups").document(group.gid).delete()
                            .addOnSuccessListener {
                                showDeleteGroupConfirm = null
                                Toast.makeText(context, "Group deleted.", Toast.LENGTH_SHORT).show()
                            }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) { Text("Delete", color = Color.White, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteGroupConfirm = null }) { Text("Cancel", color = Color.Gray) }
            }
        )
    }

    // ── User Detail Sheet ──
    showUserDetail?.let { user ->
        val fmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        AlertDialog(
            onDismissRequest = { showUserDetail = null },
            containerColor = Color(0xFF1E1E1E),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape)
                            .background(AppGreen.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(user.name.take(1).uppercase(), color = AppGreen, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(user.name, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(if (user.isDeactivated) "BANNED" else user.status.uppercase(),
                            color = if (user.isDeactivated) Color.Red else AppGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    UserDetailRow(Icons.Default.Email, "Email", user.email.ifBlank { "—" })
                    UserDetailRow(Icons.Default.Phone, "Phone", user.phone.ifBlank { "—" })
                    UserDetailRow(Icons.Default.Tag, "UID", user.uid)
                    UserDetailRow(Icons.Default.CalendarMonth, "Joined",
                        user.createdAt?.toDate()?.let { fmt.format(it) } ?: "Unknown")
                }
            },
            confirmButton = {
                TextButton(onClick = { showUserDetail = null }) { Text("Close", color = AppGreen) }
            }
        )
    }

    // ── Duplicate Accounts Dialog ──
    if (showDuplicatesDialog) {
        val fmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        AlertDialog(
            onDismissRequest = { showDuplicatesDialog = false },
            containerColor = Color(0xFF1A1A1A),
            modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFFFFB74D), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (duplicateGroups.isEmpty()) "No Duplicates Found" else "${duplicateGroups.size} Duplicate Group(s)",
                        color = Color.White, fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                if (duplicateGroups.isEmpty()) {
                    Text("All accounts are unique. No duplicates detected.", color = Color.LightGray)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(duplicateGroups, key = { it.key }) { group ->
                            Surface(
                                color = Color(0xFF111111),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            if (group.type == "email") Icons.Default.Email else Icons.Default.Phone,
                                            contentDescription = null,
                                            tint = Color(0xFFFFB74D),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            group.key,
                                            color = Color(0xFFFFB74D),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    // First account = KEEP (oldest)
                                    group.accounts.forEachIndexed { index, account ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                color = if (index == 0) AppGreen.copy(0.12f) else Color.Red.copy(0.12f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    if (index == 0) "KEEP" else "DUPE",
                                                    color = if (index == 0) AppGreen else Color(0xFFEF5350),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(account.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                                Text(
                                                    account.createdAt?.toDate()?.let { fmt.format(it) } ?: "Unknown date",
                                                    color = Color.Gray, fontSize = 11.sp
                                                )
                                            }
                                            if (index != 0) {
                                                IconButton(
                                                    onClick = {
                                                        db.collection("users").document(account.uid).delete()
                                                            .addOnSuccessListener {
                                                                Toast.makeText(context, "Duplicate removed: ${account.name}", Toast.LENGTH_SHORT).show()
                                                                // Refresh duplicates from updated user list
                                                                duplicateGroups = duplicateGroups.map { g ->
                                                                    g.copy(accounts = g.accounts.filter { it.uid != account.uid })
                                                                }.filter { it.accounts.size > 1 }
                                                            }
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.DeleteForever, contentDescription = "Delete duplicate", tint = Color(0xFFEF5350), modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (duplicateGroups.isNotEmpty()) {
                    Button(
                        onClick = {
                            // Auto-delete ALL duplicates at once (keep index 0 of each group)
                            val toDelete = duplicateGroups.flatMap { it.accounts.drop(1) }.map { it.uid }
                            var done = 0
                            toDelete.forEach { uid ->
                                db.collection("users").document(uid).delete()
                                    .addOnSuccessListener {
                                        done++
                                        if (done == toDelete.size) {
                                            duplicateGroups = emptyList()
                                            Toast.makeText(context, "Removed ${toDelete.size} duplicate(s)!", Toast.LENGTH_LONG).show()
                                        }
                                    }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Remove All Duplicates", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                } else {
                    TextButton(onClick = { showDuplicatesDialog = false }) {
                        Text("Close", color = AppGreen)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDuplicatesDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }
}

// ── Overview Tab ────────────────────────────────────────────────────────────────

@Composable
fun OverviewTab(
    users: List<AdminUserItem>,
    groups: List<AdminGroupItem>,
    activeNow: Int,
    bannedCount: Int
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text("📊 Live Statistics", color = AppGreen, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminStatCard("Total Users", users.size.toString(), Icons.Default.People, AppGreen, Modifier.weight(1f))
                AdminStatCard("Groups", groups.size.toString(), Icons.Default.Group, Color(0xFF64B5F6), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminStatCard("Online Now", activeNow.toString(), Icons.Default.FiberManualRecord, Color(0xFF66BB6A), Modifier.weight(1f))
                AdminStatCard("Banned", bannedCount.toString(), Icons.Default.Block, Color(0xFFEF5350), Modifier.weight(1f))
            }
        }
        item { Spacer(modifier = Modifier.height(4.dp)) }
        item {
            Text("👥 Recently Joined", color = AppGreen, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        val fmt = SimpleDateFormat("dd MMM", Locale.getDefault())
        items(users.sortedByDescending { it.createdAt?.seconds ?: 0 }.take(5)) { user ->
            Surface(
                color = Color(0xFF1A1A1A),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(AppGreen.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(user.name.take(1).uppercase(), color = AppGreen, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(user.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(user.email.ifBlank { user.phone }, color = Color.Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        user.createdAt?.toDate()?.let { fmt.format(it) } ?: "",
                        color = Color.Gray, fontSize = 11.sp
                    )
                }
            }
        }
    }
}

// ── Users Tab ────────────────────────────────────────────────────────────────

@Composable
fun UsersTab(
    users: List<AdminUserItem>,
    search: String,
    onSearchChange: (String) -> Unit,
    onToggleBan: (AdminUserItem) -> Unit,
    onDelete: (AdminUserItem) -> Unit,
    onViewDetail: (AdminUserItem) -> Unit
) {
    val filtered = remember(search, users) {
        if (search.isBlank()) users
        else users.filter {
            it.name.contains(search, ignoreCase = true) ||
            it.email.contains(search, ignoreCase = true) ||
            it.phone.contains(search, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar
        OutlinedTextField(
            value = search,
            onValueChange = onSearchChange,
            placeholder = { Text("Search users...", color = Color.Gray, fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp)) },
            trailingIcon = {
                if (search.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedBorderColor = AppGreen, unfocusedBorderColor = Color(0xFF333333),
                focusedContainerColor = Color(0xFF1A1A1A), unfocusedContainerColor = Color(0xFF1A1A1A)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
        )

        Text(
            "${filtered.size} user(s)",
            color = Color.Gray, fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(filtered, key = { it.uid }) { user ->
                AdminUserRowEnhanced(
                    user = user,
                    onToggleBan = { onToggleBan(user) },
                    onDelete = { onDelete(user) },
                    onViewDetail = { onViewDetail(user) }
                )
                HorizontalDivider(color = Color(0xFF222222), thickness = 0.5.dp)
            }
        }
    }
}

// ── Groups Tab ────────────────────────────────────────────────────────────────

@Composable
fun GroupsTab(
    groups: List<AdminGroupItem>,
    onDeleteGroup: (AdminGroupItem) -> Unit
) {
    val fmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    if (groups.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No groups found.", color = Color.Gray)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("${groups.size} group(s)", color = Color.Gray, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(4.dp))
        }
        items(groups, key = { it.gid }) { group ->
            Surface(
                color = Color(0xFF1A1A1A),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(42.dp).clip(CircleShape)
                            .background(Color(0xFF64B5F6).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Group, contentDescription = null, tint = Color(0xFF64B5F6), modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(group.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "${group.memberCount} members · ${group.createdAt?.toDate()?.let { fmt.format(it) } ?: "Unknown"}",
                            color = Color.Gray, fontSize = 12.sp
                        )
                    }
                    IconButton(onClick = { onDeleteGroup(group) }) {
                        Icon(Icons.Default.DeleteForever, contentDescription = "Delete Group", tint = Color(0xFFEF5350))
                    }
                }
            }
        }
    }
}

// ── Controls Tab ────────────────────────────────────────────────────────────────

@Composable
fun ControlsTab(
    onBroadcast: () -> Unit,
    motdText: String,
    motdEnabled: Boolean,
    onMotdEdit: () -> Unit,
    onToggleMotd: (Boolean) -> Unit,
    minVersion: String,
    latestVersion: String,
    onVersionEdit: () -> Unit,
    userCount: Int,
    bannedCount: Int,
    onFindDuplicates: () -> Unit,
    isDuplicateScanRunning: Boolean
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // --- Broadcast ---
        item {
            AdminControlSection(title = "📢 Broadcast", subtitle = "Send message to all ${userCount} users") {
                Button(
                    onClick = onBroadcast,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppGreen),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Campaign, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Send System Broadcast", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }

        // --- MOTD ---
        item {
            AdminControlSection(title = "📋 Announcement (MOTD)", subtitle = "Shown to all users at login") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable MOTD", color = Color.White, modifier = Modifier.weight(1f))
                    Switch(
                        checked = motdEnabled,
                        onCheckedChange = onToggleMotd,
                        colors = SwitchDefaults.colors(checkedThumbColor = AppGreen, checkedTrackColor = AppGreen.copy(alpha = 0.4f))
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = Color(0xFF111111),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        motdText.ifBlank { "No announcement set." },
                        color = if (motdText.isBlank()) Color.Gray else Color.LightGray,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onMotdEdit,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppGreen)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = AppGreen, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Edit Announcement", color = AppGreen)
                }
            }
        }

        // --- App Version ---
        item {
            AdminControlSection(title = "🚀 App Version Control", subtitle = "Control force-update and latest version") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VersionChip("Min Version", minVersion.ifBlank { "—" }, Color(0xFFFFB74D), Modifier.weight(1f))
                    VersionChip("Latest", latestVersion.ifBlank { "—" }, Color(0xFF66BB6A), Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onVersionEdit,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF66BB6A))
                ) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = Color(0xFF66BB6A), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Update Version Config", color = Color(0xFF66BB6A))
                }
            }
        }

        // --- Quick Stats ----
        item {
            AdminControlSection(title = "👁 Quick Stats", subtitle = null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickStatItem("Total", userCount.toString(), Color.White, Modifier.weight(1f))
                    QuickStatItem("Banned", bannedCount.toString(), Color(0xFFEF5350), Modifier.weight(1f))
                    QuickStatItem("Active", (userCount - bannedCount).toString(), AppGreen, Modifier.weight(1f))
                }
            }
        }

        // --- Duplicate Accounts ---
        item {
            AdminControlSection(
                title = "🔍 Duplicate Accounts",
                subtitle = "Find and remove users registered with the same email or phone"
            ) {
                Text(
                    "Scans all registered users for duplicate email or phone entries. The oldest account is kept; newer duplicates are marked for removal.",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onFindDuplicates,
                    enabled = !isDuplicateScanRunning,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB74D)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isDuplicateScanRunning) {
                        CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scanning...", color = Color.Black, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan for Duplicates", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ── Helper Composables ────────────────────────────────────────────────────────

@Composable
fun AdminControlSection(title: String, subtitle: String?, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = Color(0xFF1A1A1A),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            if (subtitle != null) {
                Text(subtitle, color = Color.Gray, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun VersionChip(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
    ) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = Color.Gray, fontSize = 11.sp)
            Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
fun QuickStatItem(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(color = Color(0xFF111111), shape = RoundedCornerShape(8.dp), modifier = modifier) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            Text(label, color = Color.Gray, fontSize = 12.sp)
        }
    }
}

@Composable
fun AdminUserRowEnhanced(
    user: AdminUserItem,
    onToggleBan: () -> Unit,
    onDelete: () -> Unit,
    onViewDetail: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val banColor by animateColorAsState(
        targetValue = if (user.isDeactivated) Color(0xFFEF5350) else AppGreen,
        animationSpec = tween(300), label = "banColor"
    )

    Column(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape)
                    .background(if (user.isDeactivated) Color.Red.copy(0.15f) else AppGreen.copy(0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(user.name.take(1).uppercase(), color = if (user.isDeactivated) Color.Red else AppGreen, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(user.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (user.isDeactivated) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(color = Color.Red.copy(0.15f), shape = RoundedCornerShape(4.dp)) {
                            Text("BANNED", color = Color.Red, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                    }
                }
                Text(
                    user.email.ifBlank { user.phone.ifBlank { "No contact info" } },
                    color = Color.Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            // Status dot
            Box(
                modifier = Modifier.size(8.dp).clip(CircleShape)
                    .background(if (user.status == "online") Color(0xFF66BB6A) else Color.Gray)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp)
            )
        }

        // Expanded actions
        AnimatedVisibility(visible = expanded) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 68.dp, end = 12.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onViewDetail,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray),
                    modifier = Modifier.weight(1f).height(34.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("View Info", color = Color.Gray, fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onToggleBan,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, banColor),
                    modifier = Modifier.weight(1f).height(34.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(if (user.isDeactivated) "Unban" else "Ban", color = banColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onDelete,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD32F2F)),
                    modifier = Modifier.weight(1f).height(34.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Delete", color = Color(0xFFEF5350), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun UserDetailRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = AppGreen, modifier = Modifier.size(16.dp).padding(top = 2.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(label, color = Color.Gray, fontSize = 11.sp)
            Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun AdminStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFF1A1A1A),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text(title, color = Color.Gray, fontSize = 12.sp)
        }
    }
}

// ── Earnings Tab ────────────────────────────────────────────────────────────────

@Composable
fun EarningsTab(
    earnings: List<AdminUserEarnings>,
    onEdit: (AdminUserEarnings) -> Unit
) {
    val totalSystemBalance = earnings.sumOf { it.balance }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Summary Card
        Surface(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            color = Color(0xFF1A1A1A),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = AppGreen, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Total Distributed Balance", color = Color.Gray, fontSize = 14.sp)
                }
                Text("$${"%.2f".format(totalSystemBalance)}", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Total users earning: ${earnings.count { it.balance > 0 }}", color = AppGreen, fontSize = 12.sp)
            }
        }

        Text(
            "User Earnings Leaderboard",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(earnings, key = { it.uid }) { earning ->
                AdminUserEarningsRow(earning = earning, onEdit = { onEdit(earning) })
                HorizontalDivider(color = Color(0xFF222222), thickness = 0.5.dp)
            }
        }
    }
}

@Composable
fun AdminUserEarningsRow(
    earning: AdminUserEarnings,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(AppGreen.copy(0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Text(earning.name.take(1).uppercase(), color = AppGreen, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(earning.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text("Total: $${"%.2f".format(earning.totalEarnings)}", color = Color.Gray, fontSize = 12.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("$${"%.2f".format(earning.balance)}", color = AppGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Current", color = Color.Gray, fontSize = 10.sp)
        }
        Spacer(modifier = Modifier.width(16.dp))
        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Edit, contentDescription = "Edit Balance", tint = Color.Gray, modifier = Modifier.size(18.dp))
        }
    }
}

