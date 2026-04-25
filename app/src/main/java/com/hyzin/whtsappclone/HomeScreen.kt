package com.hyzin.whtsappclone

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.storage.FirebaseStorage
import com.hyzin.whtsappclone.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.hyzin.whtsappclone.utils.AdminUtils
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.alpha

// ── Colors ──────────────────────────────────────────────────────────────────

// ── Skeleton Loader ───────────────────────────────────────────────────────────
@Composable
fun ChatSkeletonLoader() {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeletonAlpha"
    )
    val shimmerColor = Color.White.copy(alpha = alpha)
    val lineColor = Color.White.copy(alpha = alpha * 0.5f)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        userScrollEnabled = false
    ) {
        items(7) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar placeholder
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(shimmerColor)
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    // Name placeholder
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.45f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(shimmerColor)
                    )
                    Spacer(Modifier.height(8.dp))
                    // Last message placeholder
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(11.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(lineColor)
                    )
                }
                // Time placeholder
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(lineColor)
                )
            }
        }
    }
}

// ── Data Models ──────────────────────────────────────────────────────────────
data class ChatItem(
    val uid: String,
    val name: String,
    val lastMessage: String = "",
    val time: String = "",
    val unreadCount: Int = 0,
    val profilePicUrl: String = "",
    val email: String = "",
    val phone: String = "",
    val isGroup: Boolean = false,
    val memberIds: List<String> = emptyList(),
    val status: String = "offline",
    val lastSeen: Long = 0L,
    val lastMsgTimestamp: Long = 0L,
    val bio: String = "",
    val hasStatus: Boolean = false,
    val typingChatId: String? = null,
    val typingState: String? = null,
    val chatId: String = ""
)

data class CallItem(
    val name: String,
    val time: String,
    val isIncoming: Boolean,
    val status: String = "completed", // "completed", "missed", "declined"
    val isVideo: Boolean = false,
    val profilePicUrl: String = "",
    val chatId: String = "",
    val otherUserId: String = "",
    val logId: String = "",
    val duration: Int = 0
)

data class StatusItem(
    val name: String,
    val time: String,
    val imageUrl: String = "",
    val uid: String = "",
    val statusId: String = "",
    val likes: List<String> = emptyList(),
    val viewers: List<String> = emptyList(),
    val privacy: String = "contacts" // "contacts" or "public"
)

// ── Bottom Nav Tabs ──────────────────────────────────────────────────────────
data class NavTab(val title: String, val icon: ImageVector)

val navTabs = listOf(
    NavTab("Chats",    Icons.AutoMirrored.Filled.Chat),
    NavTab("Calls",    Icons.Default.Call),
    NavTab("Updates",  Icons.Default.Lens),
    NavTab("Profile",  Icons.Default.Person)
)

private fun docToChatItem(doc: com.google.firebase.firestore.DocumentSnapshot, aliases: Map<String, String> = emptyMap()): ChatItem {
    val uid = doc.id
    val savedName = aliases[uid]
    return ChatItem(
        uid = uid,
        name = savedName ?: doc.getString("name") ?: "Unknown",
        lastMessage = "Click to chat",
        time = "Now",
        profilePicUrl = doc.getString("profilePicUrl") ?: "",
        email = doc.getString("email") ?: "",
        phone = doc.getString("phone") ?: "",
        status = doc.getString("status") ?: "offline",
        lastSeen = doc.getTimestamp("lastSeen")?.toDate()?.time ?: 0L,
        bio = doc.getString("bio") ?: "Available"
    )
}

// ── HomeScreen ────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onChatClick: (String, String, Boolean) -> Unit = { _, _, _ -> },
    onCallClick: (Boolean, String, String) -> Unit = { _, _, _ -> },
    onLogout: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToEditProfile: () -> Unit = {},
    onNavigateToAccount: () -> Unit = {},
    onNavigateToDevices: () -> Unit = {},
    onNavigateToAdmin: () -> Unit = {},
    onQuit: () -> Unit = {}
) {
    val pagerState = rememberPagerState { navTabs.size }
    val coroutineScope = rememberCoroutineScope()
    
    // 🛡️ Reactive Auth State: Ensure currentUserId is always fresh
    var currentUserId by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser?.uid) }
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUserId = auth.currentUser?.uid
        }
        FirebaseAuth.getInstance().addAuthStateListener(listener)
        onDispose { FirebaseAuth.getInstance().removeAuthStateListener(listener) }
    }
    val db      = FirebaseFirestore.getInstance()
    val storage = FirebaseStorage.getInstance()

    val haptic = LocalHapticFeedback.current
    var showMoreMenu by remember { mutableStateOf(false) }
    var showDiscoverySheet by remember { mutableStateOf(false) }
    var isSearchMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var discoverySearchQuery by remember { mutableStateOf("") }
    var globalSearchResults by remember { mutableStateOf<List<ChatItem>>(emptyList()) }
    var isDiscoverySearching by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var usersWithStatus by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var selectedProfileForDialog by remember { mutableStateOf<ChatItem?>(null) }
    var chatForOptions by remember { mutableStateOf<ChatItem?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var chatToRename by remember { mutableStateOf<ChatItem?>(null) }

    // -- Separate state to avoid race conditions --
    var groupChats by remember { mutableStateOf<List<ChatItem>>(emptyList()) }
    var contactList by remember { mutableStateOf<List<String>>(emptyList()) }
    var allContacts by remember { mutableStateOf<List<ChatItem>>(emptyList()) }
    var contactAliases by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var contactRingtones by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var unreadCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var fallbackUsers by remember { mutableStateOf<Map<String, ChatItem>>(emptyMap()) }
    var isConversationsLoaded by remember { mutableStateOf(false) }
    var rawConversations by remember { mutableStateOf<List<DocumentSnapshot>>(emptyList()) }
    
    // 🔍 Dynamic User Tracking: Identify all users we need to watch in real-time
    val relevantUserIds by remember {
        derivedStateOf {
            // Sort conversations by timestamp to ensure most recent chats get live presence tracking (30 limit)
            val fromConversations = rawConversations
                .sortedByDescending { it.getTimestamp("lastMessageTimestamp")?.toDate()?.time ?: 0L }
                .mapNotNull { doc ->
                    val participants = doc.get("participantIds") as? List<String> ?: emptyList()
                    participants.find { it != currentUserId }
                }
            
            // Also include IDs from global search results so we see their status in the discovery sheet
            val fromSearch = globalSearchResults.map { it.uid }
            
            (fromConversations + contactList + fromSearch).distinct().filter { it.isNotEmpty() }
        }
    }

    // Firebase variables moved to top of Composable

    val userChats by remember {
        derivedStateOf {
            rawConversations.mapNotNull { doc ->
                @Suppress("UNCHECKED_CAST")
                val participants = doc.get("participantIds") as? List<String> ?: emptyList()
                val otherUserId = participants.find { it != currentUserId } ?: return@mapNotNull null
                
                @Suppress("UNCHECKED_CAST")
                val participantInfo = doc.get("participantInfo") as? Map<String, Map<String, String>>
                val otherInfo = participantInfo?.get(otherUserId)
                
                val savedAlias = contactAliases[otherUserId]
                val fallback = fallbackUsers[otherUserId]
                val lastTs = doc.getTimestamp("lastMessageTimestamp")
                
                ChatItem(
                    uid = otherUserId,
                    name = savedAlias ?: otherInfo?.get("name") ?: fallback?.name ?: "Unknown",
                    lastMessage = doc.getString("lastMessage") ?: "Tap to chat",
                    time = lastTs?.let { ts ->
                        java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(ts.toDate())
                    } ?: "",
                    unreadCount = unreadCounts[doc.id] ?: 0,
                    profilePicUrl = otherInfo?.get("pic") ?: fallback?.profilePicUrl ?: "",
                    email = otherInfo?.get("email") ?: fallback?.email ?: "",
                    phone = otherInfo?.get("phone") ?: fallback?.phone ?: "",
                    bio = otherInfo?.get("bio") ?: fallback?.bio ?: "Available",
                    lastMsgTimestamp = lastTs?.toDate()?.time ?: 0L,
                    status = otherInfo?.get("status") ?: fallback?.status ?: "offline"
                )
            }.sortedByDescending { it.lastMsgTimestamp }
        }
    }
    val chats by remember {
        derivedStateOf {
            // Sort: 1. Most recent messages first, 2. Alphabetical for others
            (userChats + groupChats + allContacts)
                .groupBy { it.uid }
                .map { (key, group) -> 
                    val base = group.maxByOrNull { it.lastMsgTimestamp } ?: group.first()
                    
                    // 🟢 LIVE DATA OVERLAY: Prioritize real-time data from allContacts
                    val liveData = allContacts.find { it.uid == base.uid }
                    
                    val alias = contactAliases[base.uid]
                    val hasStatus = usersWithStatus.contains(base.uid)
                    
                    // Priority for Name/Pic: 1. Local Alias, 2. Live Data, 3. Base/Stale data
                    val finalName = alias ?: liveData?.name ?: base.name
                    val finalPic = liveData?.profilePicUrl ?: base.profilePicUrl
                    val finalStatus = liveData?.status ?: base.status
                    
                    base.copy(
                        name = finalName, 
                        profilePicUrl = finalPic, 
                        status = finalStatus,
                        hasStatus = hasStatus,
                        typingChatId = liveData?.typingChatId,
                        typingState = liveData?.typingState,
                        chatId = base.chatId
                    )
                }
                .sortedWith(
                    compareByDescending<ChatItem> { it.lastMsgTimestamp > 0L }
                        .thenByDescending { it.lastMsgTimestamp }
                        .thenBy { it.name.lowercase() }
                )
        }
    }

    var currentUserName by remember { mutableStateOf("User") }
    var currentUserPic  by remember { mutableStateOf("") }
    var currentUserCoverPic by remember { mutableStateOf("") }
    
    val statuses = remember { mutableStateListOf<StatusItem>() }
    var selectedStatus by remember { mutableStateOf<StatusItem?>(null) }

    // Unified Status Listener with Privacy Filtering (Only shows contacts + self)
    LaunchedEffect(contactList, currentUserId) {
        if (currentUserId == null) return@LaunchedEffect
        
        db.collection("statuses")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    statuses.clear()
                    snapshot.documents.forEach { doc ->
                        val uid = doc.getString("uid") ?: ""
                        
                        // ── PRIVACY FILTER ─────────────────────────────────────
                        // Only show status if:
                        // 1. It's MINE
                        // 2. Sender is in my CONTACT LIST
                        // 3. Privacy is set to PUBLIC
                        val isMine = uid == currentUserId
                        val isContact = contactList.contains(uid)
                        val privacy = doc.getString("privacy") ?: "contacts"
                        
                        if (isMine || isContact || privacy == "public") {
                            val name = doc.getString("name") ?: "Unknown"
                            val imageUrl = doc.getString("imageUrl") ?: ""
                            val timestamp = doc.getTimestamp("timestamp")
                            
                            val now = com.google.firebase.Timestamp.now().seconds
                            val statusTime = timestamp?.seconds ?: 0
                            if (now - statusTime < 86400) { 
                                val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                                val timeLabel = if (timestamp != null) sdf.format(timestamp.toDate()) else "Now"
                                @Suppress("UNCHECKED_CAST")
                                val likes = doc.get("likes") as? List<String> ?: emptyList()
                                @Suppress("UNCHECKED_CAST")
                                val viewers = doc.get("viewers") as? List<String> ?: emptyList()
                                statuses.add(StatusItem(name, timeLabel, imageUrl, uid, doc.id, likes, viewers, privacy))
                            }
                        }
                    }
                }
            }
    }

    LaunchedEffect(currentUserId) {
        val uid = currentUserId
        if (uid == null) return@LaunchedEffect
        
        // 1. Fetch Current User Profile
        db.collection("users").document(uid).addSnapshotListener { snap, _ ->
            if (snap != null && snap.exists()) {
                currentUserName = snap.getString("name") ?: "User"
                currentUserPic  = snap.getString("profilePicUrl") ?: ""
                currentUserCoverPic = snap.getString("coverPicUrl") ?: ""
                @Suppress("UNCHECKED_CAST")
                val newList = snap.get("contact_list") as? List<String> ?: emptyList()
                contactList = newList
                
                @Suppress("UNCHECKED_CAST")
                val newAliases = snap.get("contact_aliases") as? Map<String, String> ?: emptyMap()
                contactAliases = newAliases
                
                @Suppress("UNCHECKED_CAST")
                val newRingtones = snap.get("contact_ringtones") as? Map<String, String> ?: emptyMap()
                contactRingtones = newRingtones
            }
        }

        // 1.2 Fetch Unread Counts
        db.collection("users").document(uid).collection("unread_counts")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val counts = snapshot.documents.associate { it.id to (it.getLong("count")?.toInt() ?: 0) }
                    unreadCounts = counts
                }
            }


        // 2. Fetch active conversations only (Relationship-based fetching)
        db.collection("conversations")
            .whereArrayContains("participantIds", uid)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                rawConversations = snapshot.documents
                isConversationsLoaded = true  // ← mark data arrived, even if list is empty
            }



        // 4. Fetch Groups (safe — uses separate groupChats var)
        db.collection("groups")
            .whereArrayContains("members", uid)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                groupChats = snapshot.documents.map { doc ->
                    @Suppress("UNCHECKED_CAST")
                    val mIds = doc.get("members") as? List<String> ?: emptyList()
                    ChatItem(
                        uid           = doc.id,
                        name          = doc.getString("name") ?: "Group",
                        lastMessage   = doc.getString("lastMessage") ?: "Group created",
                        time          = "Group",
                        unreadCount   = 0,
                        profilePicUrl = doc.getString("groupPicUrl") ?: "",
                        isGroup       = true,
                        memberIds     = mIds
                    )
                }
            }

        // 4. Auto-mark incoming messages as "Delivered"
        db.collectionGroup("contents")
            .whereEqualTo("receiver_id", uid)
            .whereEqualTo("status", "sent")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    if (e.message?.contains("permission", ignoreCase = true) == true) {
                        android.util.Log.e("HomeScreen", "Permission denied for 'contents' collectionGroup. Check your Firestore rules and indexes.")
                    } else {
                        android.util.Log.e("HomeScreen", "Delivered update failed: ${e.message}")
                    }
                    return@addSnapshotListener
                }
                snapshot?.documents?.forEach { it.reference.update("status", "delivered") }
            }
    }

    // 🟢 LIVE PRESENCE ENGINE: Dedicated effect to watch status of relevant users
    DisposableEffect(currentUserId, relevantUserIds) {
        val uid = currentUserId
        if (uid == null || relevantUserIds.isEmpty()) return@DisposableEffect onDispose {}
        
        Log.d("Presence", "Initializing Presence Listener for ${relevantUserIds.size} users")
        
        // Firestore 'whereIn' is limited to 30 IDs per query. 
        // We prioritize the first 30 (which are the most recent chats per our derivedStateOf sorting)
        val listener = db.collection("users")
            .whereIn(com.google.firebase.firestore.FieldPath.documentId(), relevantUserIds.take(30))
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("Presence", "Status Listener Failed: ${e.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    allContacts = snapshot.documents.map { doc ->
                        val tChatId = doc.getString("typingChatId")
                        val tState = doc.getString("typingState")
                        if (tChatId != null) {
                            Log.d("Presence", "User ${doc.id} is $tState in chat $tChatId")
                        }
                        ChatItem(
                            uid = doc.id,
                            name = doc.getString("name") ?: "Unknown",
                            profilePicUrl = doc.getString("profilePicUrl") ?: "",
                            email = doc.getString("email") ?: "",
                            phone = doc.getString("phone") ?: "",
                            bio = doc.getString("bio") ?: "Available",
                            status = doc.getString("status") ?: "offline",
                            typingChatId = tChatId,
                            typingState = tState
                        )
                    }
                    Log.d("Presence", "Updated status for ${allContacts.size} users. Online count: ${allContacts.count { it.status == "online" }}")
                }
            }
            
        onDispose {
            Log.d("Presence", "Disposing Presence Listener")
            listener.remove()
        }
    }

    // 2.1 Fallback fetcher for missing names/pics
    // Tracks specific IDs that need metadata resolution
    val missingIdsToFetch by remember {
        derivedStateOf {
            chats.filter { 
                it.name == "Unknown" && !contactAliases.containsKey(it.uid) && !fallbackUsers.containsKey(it.uid) 
            }.map { it.uid }.distinct()
        }
    }

    LaunchedEffect(missingIdsToFetch) {
        if (missingIdsToFetch.isEmpty()) return@LaunchedEffect
        
        val currentMissing = missingIdsToFetch

        if (currentMissing.isNotEmpty()) {
            db.collection("users").whereIn(com.google.firebase.firestore.FieldPath.documentId(), currentMissing.take(10)).get()
                .addOnSuccessListener { snaps ->
                    val newFallbacks = currentMissing.associate { id ->
                        val doc = snaps.documents.find { it.id == id }
                        id to ChatItem(
                            uid = id,
                            name = doc?.getString("name") ?: "Deleted User",
                            profilePicUrl = doc?.getString("profilePicUrl") ?: "",
                            email = doc?.getString("email") ?: "",
                            phone = doc?.getString("phone") ?: "",
                            bio = doc?.getString("bio") ?: "Available"
                        )
                    }
                    fallbackUsers = fallbackUsers + newFallbacks
                }
        }
    }
    
    // 3. Fetch all users from the Contact List (Relationship-based fetching)
    LaunchedEffect(contactList) {
        if (contactList.isEmpty()) {
            allContacts = emptyList()
            return@LaunchedEffect
        }
        
        // Limit to 30 as per Firestore documentation for 'in' operator
        val queryList = contactList.take(30)
        
        db.collection("users")
            .whereIn(com.google.firebase.firestore.FieldPath.documentId(), queryList)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                
                allContacts = snapshot.documents.map { doc ->
                    val uid = doc.id
                    val alias = contactAliases[uid] ?: doc.getString("name") ?: "Unknown"
                    ChatItem(
                        uid = uid,
                        name = alias,
                        lastMessage = "Click to chat",
                        time = "",
                        profilePicUrl = doc.getString("profilePicUrl") ?: "",
                        email = doc.getString("email") ?: "",
                        phone = doc.getString("phone") ?: "",
                        status = doc.getString("status") ?: "offline",
                        lastSeen = doc.getTimestamp("lastSeen")?.toDate()?.time ?: 0L,
                        bio = doc.getString("bio") ?: "Available"
                    )
                }
            }
    }

    // ── Global Discovery Search Logic ──────────────────────────────────────────
    LaunchedEffect(discoverySearchQuery) {
        if (discoverySearchQuery.trim().length < 1) { // Lowered to 1 for immediate feedback
            globalSearchResults = emptyList()
            return@LaunchedEffect
        }
        
        isDiscoverySearching = true
        // Wait 600ms to debounce
        kotlinx.coroutines.delay(600)
        
        try {
            val query = discoverySearchQuery.trim()
            val isPhoneQuery = PhoneUtils.isProbablyPhone(query)
            val normalizedPhone = if (isPhoneQuery) PhoneUtils.normalize(query) else ""

            // Priority 1: Search by Email
            val emailTask = db.collection("users")
                .whereEqualTo("email", query.lowercase())
                .limit(5)
                .get()

            // Priority 2: Search by Phone (Normalized)
            val phoneTask = if (isPhoneQuery) {
                db.collection("users")
                    .whereEqualTo("phone", normalizedPhone)
                    .limit(5)
                    .get()
            } else null

            // Priority 3: Search by Name (Prefix)
            val nameTask = db.collection("users")
                .orderBy("name")
                .startAt(query)
                .endAt(query + "\uf8ff")
                .limit(5)
                .get()

            // Priority 4: Search by WattsHub ID (ID)
            val idTask = db.collection("users")
                .whereEqualTo("secretIdentity", query.uppercase())
                .limit(5)
                .get()

            // Execute all queries
            emailTask.addOnSuccessListener { emailSnap ->
                val emailResults = emailSnap.documents.mapNotNull { doc ->
                    if (doc.id == currentUserId) null else docToChatItem(doc, contactAliases)
                }

                idTask.addOnSuccessListener { idSnap ->
                    val idResults = idSnap.documents.mapNotNull { doc ->
                        if (doc.id == currentUserId) null else docToChatItem(doc, contactAliases)
                    }

                    val onPhoneResult: (com.google.firebase.firestore.QuerySnapshot?) -> Unit = { phoneSnap ->
                        val phoneResults = phoneSnap?.documents?.mapNotNull { doc ->
                            if (doc.id == currentUserId) null else docToChatItem(doc, contactAliases)
                        } ?: emptyList()

                        nameTask.addOnSuccessListener { nameSnap ->
                            val nameResults = nameSnap.documents.mapNotNull { doc ->
                                if (doc.id == currentUserId) null else docToChatItem(doc, contactAliases)
                            }

                            // Combine results and remove duplicates
                            globalSearchResults = (emailResults + idResults + phoneResults + nameResults)
                                .sortedBy { it.uid } // Stable sort
                                .distinctBy { it.uid }
                            isDiscoverySearching = false
                        }
                    }

                    if (phoneTask != null) {
                        phoneTask.addOnSuccessListener { onPhoneResult(it) }
                    } else {
                        onPhoneResult(null)
                    }
                }
            }
        } catch (e: Exception) {
            isDiscoverySearching = false
            android.util.Log.e("GlobalSearch", "Error querying global users: ${e.message}")
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize().imePadding(),
        containerColor = Color.Transparent,

        // ── Top Bar (Compact Version) ──
        topBar = {
            val topBarGradient = androidx.compose.ui.graphics.Brush.horizontalGradient(
                colors = listOf(OceanBlueSecondary, Color(0xFF041023))
            )
            
            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(60.dp).background(topBarGradient, RoundedCornerShape(30.dp)),
                    shape = RoundedCornerShape(30.dp),
                    color = Color.Transparent,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSearchMode) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search chats...", color = Color.White.copy(alpha = 0.5f), fontSize = 15.sp) },
                                modifier = Modifier.weight(1f).height(48.dp),
                                singleLine = true,
                                shape = RoundedCornerShape(24.dp),
                                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.White.copy(alpha = 0.1f),
                                    unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    cursorColor = Color.White
                                ),
                                trailingIcon = {
                                    IconButton(onClick = { isSearchMode = false; searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }
                            )
                        } else {
                            // User Profile Avatar
                            Surface(
                                modifier = Modifier.size(38.dp).clip(CircleShape).clickable { showProfileDialog = true },
                                color = Color.White.copy(alpha = 0.15f)
                            ) {
                                if (currentUserPic.isNotEmpty()) {
                                    AsyncImage(model = currentUserPic, contentDescription = "My Profile", modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                                } else {
                                    Icon(Icons.Default.Person, contentDescription = "My Profile", tint = Color.White, modifier = Modifier.padding(6.dp))
                                }
                            }
                            
                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = "WattsHub",
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                fontSize = 20.sp,
                                modifier = Modifier.weight(1f)
                            )

                            IconButton(onClick = { isSearchMode = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                            
                            Box {
                                IconButton(onClick = { showMoreMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White, modifier = Modifier.size(24.dp))
                                }
                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false }
                                ) {
                                    DropdownMenuItem(text = { Text("New group") }, onClick = { showMoreMenu = false; showCreateGroupDialog = true })
                                    DropdownMenuItem(text = { Text("Edit Profile") }, onClick = { showMoreMenu = false; showProfileDialog = true })
                                    DropdownMenuItem(text = { Text("Settings") }, onClick = { showMoreMenu = false; onNavigateToSettings() })
                                    if (AdminUtils.isCurrentUserAdmin()) {
                                        DropdownMenuItem(text = { Text("Admin Panel", color = AppGreen) }, onClick = { showMoreMenu = false; onNavigateToAdmin() })
                                    }
                                    DropdownMenuItem(text = { Text("Logout", color = Color.Red) }, onClick = { showMoreMenu = false; onLogout() })
                                }
                            }
                        }
                    }
                }
            }
        },

        bottomBar = {
            val navGradient = androidx.compose.ui.graphics.Brush.horizontalGradient(
                colors = listOf(OceanBlueSecondary, Color(0xFF041023))
            )
            
            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(64.dp).background(navGradient, RoundedCornerShape(32.dp)),
                    shape = RoundedCornerShape(32.dp),
                    color = Color.Transparent,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        navTabs.forEachIndexed { index, tab ->
                            val isSelected = pagerState.currentPage == index
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { 
                                        coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = tab.icon, 
                                    contentDescription = tab.title,
                                    tint = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(if (isSelected) 26.dp else 22.dp)
                                )
                                Text(
                                    text = tab.title,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        },

        floatingActionButton = {
            if (pagerState.currentPage == 0) {
                FloatingActionButton(
                    onClick = { showDiscoverySheet = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor   = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "New Chat")
                }
            }
        }

        ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(Color.Transparent),
            beyondViewportPageCount = 2
        ) { page ->
            when (page) {
                0 -> {
                    val hasRecentChats = chats.any { it.lastMessage != "Tap to start chatting" }
                    val onAvatarClick: (ChatItem) -> Unit = { chat ->
                        val foundStatus = statuses.firstOrNull { it.uid == chat.uid }
                        if (foundStatus != null) {
                            selectedStatus = foundStatus
                        } else {
                            selectedProfileForDialog = chat
                        }
                    }
                    when {
                        // ── 1. Still waiting for Firestore first response ──
                        !isConversationsLoaded -> {
                            ChatSkeletonLoader()
                        }
                        // ── 2. Data loaded and has real conversations ──
                        hasRecentChats -> {
                            ChatsScreen(
                                onChatClick = onChatClick,
                                searchQuery = searchQuery,
                                chats = chats,
                                onAvatarClick = onAvatarClick,
                                onLongClick = { chatForOptions = it }
                            )
                        }
                        // ── 3. Truly empty (no conversations exist yet) ──
                        else -> {
                            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                                Text("No recent chats. Start a new one!", color = TextPrimary, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Suggested Contacts:", color = SkyBlueAccent, fontSize = 12.sp)
                                ChatsScreen(
                                    onChatClick = onChatClick,
                                    searchQuery = searchQuery,
                                    chats = chats,
                                    onAvatarClick = onAvatarClick,
                                    onLongClick = { chatForOptions = it }
                                )
                            }
                        }
                    }
                }
                1 -> CallsScreen(onCallClick = onCallClick, contactAliases = contactAliases)
                2 -> StatusScreen(
                    contactList = contactList, 
                    currentUserPic = currentUserPic,
                    statuses = statuses,
                    onStatusClick = { selectedStatus = it }
                )
                3 -> SettingsScreen(
                    onBack = { /* Tab doesn't need back */ }, 
                    onLogout = onLogout, 
                    onNavigateToEditProfile = onNavigateToEditProfile, 
                    onNavigateToAccount = onNavigateToAccount,
                    onNavigateToDevices = onNavigateToDevices,
                    onNavigateToAdmin = onNavigateToAdmin,
                    onQuit = onQuit,
                    onDeactivate = {
                        val uid = currentUserId
                        if (uid != null) {
                            db.collection("users").document(uid)
                                .update("isDeactivated", true)
                                .addOnSuccessListener {
                                    FirebaseAuth.getInstance().signOut()
                                    Toast.makeText(context, "Account Deactivated", Toast.LENGTH_LONG).show()
                                }
                        }
                    }
                )
            }
        }
    }

    // Unified Status Viewer Overlay
    if (selectedStatus != null) {
        StatusViewerOverlay(
            status = selectedStatus!!,
            onDismiss = { selectedStatus = null }
        )
    }

    // ── PROFILE SETTINGS DIALOG ──
    if (showProfileDialog) {
        ProfileSettingsDialog(
            currentName = currentUserName,
            currentPic = currentUserPic,
            currentCoverPic = currentUserCoverPic,
            onDismiss = { showProfileDialog = false }
        )
    }

    // ── CONTACT OPTIONS DIALOG ──
    if (chatForOptions != null) {
        AlertDialog(
            onDismissRequest = { chatForOptions = null },
            title = { Text(chatForOptions!!.name, color = MaterialTheme.colorScheme.onSurface) },
            containerColor = Color(0xFF1E1E1E),
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text("View Profile", color = Color.White) },
                        leadingContent = { Icon(Icons.Default.Person, contentDescription = null, tint = SkyBlueAccent) },
                        modifier = Modifier.clickable {
                            selectedProfileForDialog = chatForOptions
                            chatForOptions = null
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    ListItem(
                        headlineContent = { Text("Rename Contact", color = Color.White) },
                        leadingContent = { Icon(Icons.Default.Edit, contentDescription = null, tint = SkyBlueAccent) },
                        modifier = Modifier.clickable {
                            chatToRename = chatForOptions
                            showRenameDialog = true
                            chatForOptions = null
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    ListItem(
                        headlineContent = { Text(if (chatForOptions!!.isGroup) "Leave Group" else "Delete Chat", color = Color.Red.copy(alpha = 0.7f)) },
                        leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red.copy(alpha = 0.7f)) },
                        modifier = Modifier.clickable {
                            val uid = currentUserId
                            if (uid != null) {
                                val targetChat = chatForOptions!!
                                chatForOptions = null // Hide dialog immediately

                                if (targetChat.isGroup) {
                                    db.collection("groups").document(targetChat.uid)
                                        .update("members", com.google.firebase.firestore.FieldValue.arrayRemove(uid))
                                        .addOnSuccessListener {
                                            Toast.makeText(context, "Left Group", Toast.LENGTH_SHORT).show()
                                        }
                                } else {
                                    // 1. Remove from contacts (if they are a contact)
                                    db.collection("users").document(uid)
                                        .update("contact_list", com.google.firebase.firestore.FieldValue.arrayRemove(targetChat.uid))

                                    // 2. Remove from active conversations list to hide the chat
                                    val chatId = if (uid < targetChat.uid) "${uid}_${targetChat.uid}" else "${targetChat.uid}_${uid}"
                                    db.collection("conversations").document(chatId)
                                        .update("participantIds", com.google.firebase.firestore.FieldValue.arrayRemove(uid))
                                        .addOnCompleteListener {
                                            Toast.makeText(context, "Chat Deleted", Toast.LENGTH_SHORT).show()
                                        }
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            },
            confirmButton = {}
        )
    }

    // ── RENAME CONTACT DIALOG ──
    if (showRenameDialog && chatToRename != null) {
        var newName by remember { mutableStateOf(chatToRename!!.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Contact", color = Color.White) },
            containerColor = Color(0xFF1E1E1E),
            text = {
                Column {
                    Text("Enter a custom name for this contact.", color = Color.LightGray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = AppGreen,
                            unfocusedBorderColor = Color.Gray
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uid = currentUserId
                        if (newName.isNotBlank() && uid != null) {
                            db.collection("users").document(uid)
                                .update("contact_aliases.${chatToRename!!.uid}", newName)
                                .addOnSuccessListener {
                                    showRenameDialog = false
                                    chatToRename = null
                                }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppGreen)
                ) {
                    Text("SAVE")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("CANCEL", color = Color.Gray)
                }
            }
        )
    }

    // ── CONTACT PROFILE DIALOG ──
    if (selectedProfileForDialog != null) {
        val contact = selectedProfileForDialog!!
        Dialog(onDismissRequest = { selectedProfileForDialog = null }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF121212),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column {
                    Box(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                        Box(modifier = Modifier.fillMaxWidth().height(130.dp).background(SkyBlueSurface))
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .align(Alignment.BottomCenter)
                                .clip(CircleShape)
                                .border(4.dp, Color(0xFF121212), CircleShape)
                                .background(Color.Gray),
                            contentAlignment = Alignment.Center
                        ) {
                            if (contact.profilePicUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = contact.profilePicUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Text(contact.name.take(1).uppercase(), fontSize = 40.sp, color = Color.White)
                            }
                        }
                        IconButton(
                            onClick = { selectedProfileForDialog = null },
                            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = contact.name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = {
                                chatToRename = contact
                                showRenameDialog = true
                                selectedProfileForDialog = null
                            }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "Rename", tint = AppGreen, modifier = Modifier.size(18.dp))
                            }
                        }
                        Text(contact.phone.ifBlank { "No phone" }, color = Color.Gray, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = contact.bio.ifBlank { "Available" },
                            color = Color.LightGray.copy(alpha = 0.8f),
                            fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(onClick = { /* Call */ }, modifier = Modifier.background(AppGreen.copy(alpha = 0.1f), CircleShape)) {
                                    Icon(Icons.Default.Call, contentDescription = null, tint = AppGreen)
                                }
                                Text("Call", color = Color.White, fontSize = 12.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(onClick = { /* Video */ }, modifier = Modifier.background(SkyBlueAccent.copy(alpha = 0.1f), CircleShape)) {
                                    Icon(Icons.Default.Videocam, contentDescription = null, tint = SkyBlueAccent)
                                }
                                Text("Video", color = Color.White, fontSize = 12.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(onClick = { 
                                    onChatClick(contact.uid, contact.name, false)
                                    selectedProfileForDialog = null
                                }, modifier = Modifier.background(Color.White.copy(alpha = 0.1f), CircleShape)) {
                                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = Color.White)
                                }
                                Text("Chat", color = Color.White, fontSize = 12.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(onClick = { 
                                    val uid = currentUserId
                                    if (uid != null) {
                                        db.collection("users").document(uid)
                                            .update("contact_list", com.google.firebase.firestore.FieldValue.arrayRemove(contact.uid))
                                            .addOnSuccessListener {
                                                Toast.makeText(context, "Contact Removed", Toast.LENGTH_SHORT).show()
                                                selectedProfileForDialog = null
                                            }
                                    }
                                }, modifier = Modifier.background(Color.Red.copy(alpha = 0.1f), CircleShape)) {
                                    Icon(Icons.Default.PersonRemove, contentDescription = null, tint = Color.Red.copy(alpha = 0.7f))
                                }
                                Text("Delete", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // ── CREATE GROUP DIALOG ──
    if (showCreateGroupDialog) {
        CreateGroupDialog(
            contacts = chats.filter { !it.isGroup },
            onDismiss = { showCreateGroupDialog = false },
            onCreate = { groupName, members ->
                if (currentUserId != null) {
                    val groupMap = hashMapOf(
                        "name" to groupName,
                        "admin_id" to currentUserId,
                        "members" to (members.map { it.uid } + currentUserId).distinct(),
                        "lastMessage" to "New Group Created",
                        "timestamp" to com.google.firebase.Timestamp.now(),
                        "groupPicUrl" to ""
                    )
                    db.collection("groups").add(groupMap)
                    showCreateGroupDialog = false
                }
            }
        )
    }

    if (showDiscoverySheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { 
                showDiscoverySheet = false
                discoverySearchQuery = ""
                globalSearchResults = emptyList()
            },
            sheetState = sheetState,
            containerColor = Color(0xFF1E1E1E)
        ) {
            Box(modifier = Modifier.fillMaxHeight(0.85f)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("New Chat", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = discoverySearchQuery,
                        onValueChange = { discoverySearchQuery = it },
                        placeholder = { Text("Search by name, email or mobile", color = TextSecondary.copy(alpha = 0.6f), fontSize = 14.sp) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        prefix = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp).padding(end = 8.dp)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF2A2A2A),
                            unfocusedContainerColor = Color(0xFF2A2A2A),
                            focusedBorderColor = SkyBlueAccent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    if (discoverySearchQuery.isEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { 
                                showDiscoverySheet = false
                                showCreateGroupDialog = true 
                            }.padding(vertical = 12.dp)
                        ) {
                            Surface(modifier = Modifier.size(48.dp), shape = CircleShape, color = SkyBlueAccent) {
                                Icon(Icons.Default.GroupAdd, contentDescription = null, tint = Color.White, modifier = Modifier.padding(10.dp))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("New Group", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    val filteredLocal = if (discoverySearchQuery.isEmpty()) {
                        chats.filter { !it.isGroup }
                    } else {
                        chats.filter { 
                            !it.isGroup && (
                                it.name.contains(discoverySearchQuery, ignoreCase = true) || 
                                it.email.contains(discoverySearchQuery, ignoreCase = true) ||
                                it.phone.contains(discoverySearchQuery, ignoreCase = true)
                            )
                        }
                    }
                    LazyColumn {
                        if (isDiscoverySearching) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = SkyBlueAccent, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Searching the cloud...", color = TextSecondary.copy(alpha = 0.6f), fontSize = 14.sp)
                                }
                            }
                        }
                        if (discoverySearchQuery.isNotEmpty() && globalSearchResults.isNotEmpty()) {
                            val cloudResultsNotLocal = globalSearchResults.filter { cloudUser -> 
                                filteredLocal.none { it.uid == cloudUser.uid }
                            }
                            if (cloudResultsNotLocal.isNotEmpty()) {
                                item { Text("Global Results", color = SkyBlueAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp)) }
                                items(cloudResultsNotLocal) { chat ->
                                    ChatListItem(
                                        chat = chat,
                                        onChatClick = { id, name, _ ->
                                            val uid = currentUserId
                                            if (uid != null) {
                                                db.collection("users").document(uid)
                                                    .update("contact_list", com.google.firebase.firestore.FieldValue.arrayUnion(id))
                                                db.collection("users").document(id)
                                                    .update("contact_list", com.google.firebase.firestore.FieldValue.arrayUnion(uid))
                                            }
                                            onChatClick(id, name, false)
                                            showDiscoverySheet = false
                                        },
                                        onAvatarClick = { selectedProfileForDialog = chat },
                                        onLongClick = { chatForOptions = chat }
                                    )
                                }
                                item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFF2A2A2A)) }
                            }
                        }
                        item {
                            Text(if (discoverySearchQuery.isNotEmpty()) "Local Contacts" else "All Contacts", color = TextSecondary.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                        }
                        items(filteredLocal) { chat ->
                            ChatListItem(
                                chat = chat,
                                onChatClick = { id, name, _ ->
                                    onChatClick(id, name, false)
                                    showDiscoverySheet = false
                                },
                                onAvatarClick = { selectedProfileForDialog = chat },
                                onLongClick = { chatForOptions = chat }
                            )
                        }
                        
                        if (discoverySearchQuery.isNotEmpty() && !isDiscoverySearching && globalSearchResults.isEmpty() && filteredLocal.isEmpty()) {
                            item {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(40.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.SearchOff, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("No users found", color = Color.White, fontWeight = FontWeight.Bold)
                                    Text("Check the ID/Email and try again", color = Color.Gray, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun ChatsScreen(
    onChatClick: (String, String, Boolean) -> Unit, 
    searchQuery: String = "", 
    chats: List<ChatItem>,
    onAvatarClick: (ChatItem) -> Unit,
    onLongClick: (ChatItem) -> Unit
) {
    // Only show active chats in the main tab
    val filteredChats = chats.filter { 
        searchQuery.isEmpty() || 
        it.name.contains(searchQuery, ignoreCase = true) || 
        it.email.contains(searchQuery, ignoreCase = true) ||
        it.phone.contains(searchQuery, ignoreCase = true)
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(filteredChats, key = { it.uid }) { chat ->
            ChatListItem(
                chat = chat,
                onChatClick = onChatClick,
                onAvatarClick = { onAvatarClick(chat) },
                onLongClick = { onLongClick(chat) }
            )
            HorizontalDivider(
                modifier  = Modifier.padding(start = 72.dp),
                thickness = 0.5.dp,
                color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatListItem(
    chat: ChatItem,
    onChatClick: (String, String, Boolean) -> Unit,
    onAvatarClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (chat.unreadCount > 0) SkyBlueAccent.copy(alpha = 0.08f) 
                else Color.White.copy(alpha = 0.03f)
            )
            .border(
                width = 1.dp,
                color = if (chat.unreadCount > 0) SkyBlueAccent.copy(alpha = 0.4f) else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .combinedClickable(
                onClick = { onChatClick(chat.uid, chat.name, chat.isGroup) },
                onLongClick = onLongClick
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Vibrant Side Indicator for Unread
        if (chat.unreadCount > 0) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(SkyBlueAccent)
            )
            Spacer(modifier = Modifier.width(12.dp))
        }

        // Avatar circle with Online Indicator
        Box {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .border(
                        width = 2.dp,
                        color = if (chat.hasStatus) AppGreen else Color.Transparent,
                        shape = CircleShape
                    )
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f))
                    .clickable { onAvatarClick() },
                contentAlignment = Alignment.Center
            ) {
                if (chat.profilePicUrl.isNotEmpty()) {
                    AsyncImage(
                        model = chat.profilePicUrl,
                        contentDescription = "Contact Pic",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    val initials = if (chat.name.isNotEmpty()) chat.name.take(1).uppercase() else "?"
                    Text(
                        text = initials,
                        color = AppGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
            }
            
            // Green Dot for Online Status
            if (chat.status == "online") {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(Color(0xFF0F0F0F)) // "Border" to separate from avatar
                        .padding(2.dp)
                        .background(AppGreen, CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Name + Time row
            Row(
                modifier             = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text       = chat.name,
                    fontWeight = if (chat.unreadCount > 0) FontWeight.ExtraBold else FontWeight.SemiBold,
                    fontSize   = 16.sp,
                    color      = if (chat.unreadCount > 0) SkyBlueAccent else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text     = chat.time,
                    fontSize = 12.sp,
                    color    = if (chat.unreadCount > 0) AppGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Last message + Unread badge row
            Row(
                modifier             = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                val isTypingInThisChat = !chat.chatId.isNullOrEmpty() && chat.typingChatId == chat.chatId
                
                val infiniteTransition = rememberInfiniteTransition(label = "typing")
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 0.5f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse"
                )

                Text(
                    text = when {
                        isTypingInThisChat && chat.typingState == "typing" -> "typing..."
                        isTypingInThisChat && chat.typingState == "voice" -> "recording voice..."
                        else -> chat.lastMessage
                    },
                    fontSize = 14.sp,
                    color = if (isTypingInThisChat && chat.typingState != null) AppGreen 
                            else if (chat.unreadCount > 0) Color.White 
                            else Color.LightGray,
                    fontWeight = if ((isTypingInThisChat && chat.typingState != null) || chat.unreadCount > 0) FontWeight.Medium 
                                 else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).alpha(if (isTypingInThisChat) pulseAlpha else 1f)
                )
                
                if (chat.unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(SkyBlueAccent),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text       = chat.unreadCount.toString(),
                            color      = Color.Black,
                            fontSize   = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CALLS SCREEN
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CallListItem(
    call: CallItem, 
    contactAliases: Map<String, String> = emptyMap(),
    onCallClick: (Boolean, String, String) -> Unit = { _, _, _ -> }
) {
    val isMissed = (call.status == "missed" || call.status == "declined") && call.isIncoming
    val isOutgoing = !call.isIncoming
    
    // ✅ Vivid Color Pattern
    val typeColor = when {
        isMissed -> Color(0xFFFF5252) // 🔴 Red for Missed
        isOutgoing -> SkyBlueAccent  // 🔵 Blue for Outgoing
        else -> AppGreen             // 🟢 Green for Received
    }
    
    val displayName = contactAliases[call.otherUserId] ?: call.name
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCallClick(call.isVideo, call.chatId, displayName) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, typeColor.copy(alpha = 0.3f), CircleShape), // Subtle color hint
            contentAlignment = Alignment.Center
        ) {
            if (call.profilePicUrl.isNotEmpty()) {
                AsyncImage(
                    model = call.profilePicUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Text(
                    text = displayName.firstOrNull()?.toString()?.uppercase() ?: "?",
                    color = typeColor, // Initial uses type color
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName,
                color = if (isMissed) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurface,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (call.isIncoming) Icons.Default.CallReceived else Icons.Default.CallMade,
                    contentDescription = null,
                    tint               = typeColor,
                    modifier           = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                val statusPrefix = when(call.status) {
                    "missed" -> " (Missed)"
                    "declined" -> " (Declined)"
                    "cancelled" -> " (Cancelled)"
                    else -> ""
                }
                val durationText = if (call.duration > 0) {
                    val mm = call.duration / 60
                    val ss = call.duration % 60
                    " • ${if (mm > 0) "${mm}m " else ""}${ss}s"
                } else ""
                
                Text(
                    text = "${call.time}$statusPrefix$durationText", 
                    fontSize = 13.sp, 
                    color = typeColor.copy(alpha = 0.8f) 
                )
            }
        }

        IconButton(onClick = {
            if (call.chatId.isNotEmpty()) {
                onCallClick(call.isVideo, call.chatId, displayName)
            }
        }) {
            Icon(
                imageVector = if (call.isVideo) Icons.Default.Videocam else Icons.Default.Call,
                contentDescription = "Call back",
                tint = typeColor // Action icon also follows call type color
            )
        }
    }
}

@Composable
fun StatusScreen(
    contactList: List<String>, 
    currentUserPic: String,
    statuses: List<StatusItem>,
    onStatusClick: (StatusItem) -> Unit
) {
    val context = LocalContext.current
    var isUploading by remember { mutableStateOf(false) }
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    
    var selectedPrivacy by remember { mutableStateOf("contacts") }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingUri = uri
            showPrivacyDialog = true
        }
    }

    if (showPrivacyDialog && pendingUri != null) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Status Privacy", color = Color.White) },
            containerColor = Color(0xFF1E1E1E),
            text = {
                Column {
                    Text("Who can see your status update?", color = Color.LightGray)
                    Spacer(Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { selectedPrivacy = "contacts" }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedPrivacy == "contacts", onClick = { selectedPrivacy = "contacts" }, colors = RadioButtonDefaults.colors(selectedColor = AppGreen))
                        Spacer(Modifier.width(8.dp))
                        Text("My Contacts", color = Color.White)
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { selectedPrivacy = "public" }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedPrivacy == "public", onClick = { selectedPrivacy = "public" }, colors = RadioButtonDefaults.colors(selectedColor = AppGreen))
                        Spacer(Modifier.width(8.dp))
                        Text("Everyone (Public)", color = Color.White)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = pendingUri!!
                        showPrivacyDialog = false
                        isUploading = true
                        
                        val storageRef = FirebaseStorage.getInstance().reference.child("statuses/$currentUserId/${System.currentTimeMillis()}.jpg")
                        storageRef.putFile(uri).addOnSuccessListener {
                            storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                                val statusMap = hashMapOf(
                                    "uid" to currentUserId,
                                    "imageUrl" to downloadUri.toString(),
                                    "timestamp" to com.google.firebase.Timestamp.now(),
                                    "name" to (FirebaseAuth.getInstance().currentUser?.displayName ?: "User"),
                                    "privacy" to selectedPrivacy
                                )
                                FirebaseFirestore.getInstance().collection("statuses").add(statusMap)
                                    .addOnSuccessListener {
                                        isUploading = false
                                        Toast.makeText(context, "Status Updated (${selectedPrivacy.capitalize()})!", Toast.LENGTH_SHORT).show()
                                        pendingUri = null
                                    }
                                    .addOnFailureListener {
                                        isUploading = false
                                        Toast.makeText(context, "Status update failed", Toast.LENGTH_SHORT).show()
                                    }
                            }
                        }.addOnFailureListener {
                            isUploading = false
                            Toast.makeText(context, "Upload failed: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppGreen)
                ) {
                    Text("SHARE")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPrivacyDialog = false; pendingUri = null }) {
                    Text("CANCEL", color = Color.Gray)
                }
            }
        )
    }

    val myStatuses = statuses.filter { it.uid == currentUserId }
    val friendStatuses = statuses.filter { it.uid != currentUserId }
    val groupedStatuses = friendStatuses.groupBy { it.uid }.map { it.value.first() }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { 
                        if (myStatuses.isNotEmpty()) onStatusClick(myStatuses.first())
                        else galleryLauncher.launch("image/*") 
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Surface(
                        modifier = Modifier.size(56.dp).border(2.dp, if (myStatuses.isNotEmpty()) AppGreen else Color.Transparent, CircleShape).padding(3.dp),
                        shape = CircleShape,
                        color = Color.DarkGray
                    ) {
                        if (currentUserPic.isNotEmpty()) {
                            AsyncImage(model = currentUserPic, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        } else {
                            Icon(Icons.Default.Person, contentDescription = null, tint = Color.LightGray)
                        }
                    }
                    if (myStatuses.isEmpty()) {
                        Box(
                            modifier = Modifier.align(Alignment.BottomEnd).size(20.dp).clip(CircleShape).background(AppGreen).border(2.dp, Color.Black, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("My Status", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                    Text(if (myStatuses.isNotEmpty()) "Tap to view your updates" else "Tap to add status update", fontSize = 13.sp, color = TextSecondary)
                }
            }
            
            HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 4.dp))
            
            if (groupedStatuses.isNotEmpty()) {
                Text(
                    text = "RECENT UPDATES",
                    fontSize = 12.sp,
                    color = AppGreen,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    letterSpacing = 1.sp
                )
            }
        }
        
        items(groupedStatuses) { status ->
            StatusListItem(status, onClick = { onStatusClick(status) })
        }
    }
}

@Composable
fun StatusListItem(status: StatusItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(56.dp).border(2.dp, AppGreen, CircleShape).padding(3.dp),
            shape = CircleShape,
            color = Color.DarkGray
        ) {
            if (status.imageUrl.isNotEmpty()) {
                AsyncImage(model = status.imageUrl, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = status.name.firstOrNull()?.toString()?.uppercase() ?: "?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(status.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            Text(status.time, fontSize = 13.sp, color = TextSecondary)
        }
    }
}
@Composable
fun ProfileSettingsDialog(
    currentName: String,
    currentPic: String,
    currentCoverPic: String = "",
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedCoverUri by remember { mutableStateOf<Uri?>(null) }
    var showImageOptions by remember { mutableStateOf(false) }
    var targetingCover by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            if (targetingCover) selectedCoverUri = uri else selectedImageUri = uri
        }
    }
    
    // Simple Camera Logic (requires a file provider for real apps, but let's try direct for now or stick to gallery if complex)
    // Actually, stick to gallery for reliability in this environment, but add the UI options.

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E1E1E),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Edit Profile", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(24.dp))
                
                // ── Cover Photo + Avatar Overlap UI ──────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.7f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF2A2A2A))
                            .clickable { 
                                targetingCover = true
                                galleryLauncher.launch("image/*") 
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedCoverUri != null) {
                            AsyncImage(model = selectedCoverUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        } else if (currentCoverPic.isNotEmpty()) {
                            AsyncImage(model = currentCoverPic, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = Color.Gray)
                                Text("Add Cover Photo", color = TextSecondary.copy(alpha = 0.6f), fontSize = 10.sp)
                            }
                        }
                    }

                    // Avatar (floating on top of cover)
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .align(Alignment.BottomCenter)
                            .clip(CircleShape)
                            .background(Color(0xFF1E1E1E))
                            .border(3.dp, Color(0xFF1E1E1E), CircleShape)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(AppDarkGreen)
                                .clickable { 
                                    targetingCover = false
                                    showImageOptions = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedImageUri != null) {
                                AsyncImage(
                                    model = selectedImageUri, 
                                    contentDescription = null, 
                                    modifier = Modifier.fillMaxSize(), 
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else if (currentPic.isNotEmpty()) {
                                AsyncImage(
                                    model = currentPic, 
                                    contentDescription = null, 
                                    modifier = Modifier.fillMaxSize(), 
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(40.dp))
                            }
                        }
                        
                        // Smaller Camera Overlay Icon
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(AppGreen)
                                .clickable { 
                                    targetingCover = false
                                    showImageOptions = true
                                }
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // ── PHOTO SELECTION MENU ──
                if (showImageOptions) {
                    AlertDialog(
                        onDismissRequest = { showImageOptions = false },
                        containerColor = Color(0xFF2A2A2A),
                        title = { Text("Profile Photo", color = Color.White, fontSize = 18.sp) },
                        text = {
                            Column {
                                ListItem(
                                    headlineContent = { Text("Gallery", color = Color.White) },
                                    leadingContent = { Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = AppGreen) },
                                    modifier = Modifier.clickable {
                                        showImageOptions = false
                                        galleryLauncher.launch("image/*")
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                )
                                ListItem(
                                    headlineContent = { Text("Remove Photo", color = Color.Red.copy(alpha = 0.8f)) },
                                    leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red.copy(alpha = 0.8f)) },
                                    modifier = Modifier.clickable {
                                        showImageOptions = false
                                        if (targetingCover) selectedCoverUri = null else selectedImageUri = null
                                        // Specific logic to notify onSave about removal could be added
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                )
                            }
                        },
                        confirmButton = {}
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("Tap avatar or background to change photos", color = AppGreen, fontSize = 10.sp)
                
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Your Name", color = TextSecondary.copy(alpha = 0.6f)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = AppGreen
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !isSaving) {
                        Text("CANCEL", color = TextSecondary.copy(alpha = 0.6f))
                    }
                    Button(
                        onClick = { 
                            isSaving = true
                            val db = FirebaseFirestore.getInstance()
                            val storage = FirebaseStorage.getInstance()
                            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                            
                            val updateMap = mutableMapOf<String, Any>("name" to name)
                            
                            fun performFinalUpdate() {
                                if (currentUserId != null) {
                                    db.collection("users").document(currentUserId).update(updateMap)
                                        .addOnSuccessListener { 
                                            isSaving = false
                                            onDismiss()
                                            Toast.makeText(context, "Profile Updated!", Toast.LENGTH_SHORT).show()
                                        }
                                        .addOnFailureListener {
                                            isSaving = false
                                            Toast.makeText(context, "Update failed", Toast.LENGTH_SHORT).show()
                                        }
                                }
                            }

                            if (selectedImageUri != null || selectedCoverUri != null) {
                                var pending = 0
                                if (selectedImageUri != null) {
                                    pending++
                                    val ref = storage.reference.child("profiles/$currentUserId.jpg")
                                    val optimizedData = com.hyzin.whtsappclone.utils.ImageUtils.rescaleAndCompressImage(context, selectedImageUri!!)
                                    if (optimizedData != null) {
                                        ref.putBytes(optimizedData).addOnSuccessListener {
                                            ref.downloadUrl.addOnSuccessListener { uri ->
                                                updateMap["profilePicUrl"] = uri.toString()
                                                pending--
                                                if (pending == 0) performFinalUpdate()
                                            }
                                        }
                                    } else {
                                        ref.putFile(selectedImageUri!!).addOnSuccessListener {
                                            ref.downloadUrl.addOnSuccessListener { uri ->
                                                updateMap["profilePicUrl"] = uri.toString()
                                                pending--
                                                if (pending == 0) performFinalUpdate()
                                            }
                                        }
                                    }
                                }
                                if (selectedCoverUri != null) {
                                    pending++
                                    val ref = storage.reference.child("covers/$currentUserId.jpg")
                                    val optimizedData = com.hyzin.whtsappclone.utils.ImageUtils.rescaleAndCompressImage(context, selectedCoverUri!!)
                                    if (optimizedData != null) {
                                        ref.putBytes(optimizedData).addOnSuccessListener {
                                            ref.downloadUrl.addOnSuccessListener { uri ->
                                                updateMap["coverPicUrl"] = uri.toString()
                                                pending--
                                                if (pending == 0) performFinalUpdate()
                                            }
                                        }
                                    } else {
                                        ref.putFile(selectedCoverUri!!).addOnSuccessListener {
                                            ref.downloadUrl.addOnSuccessListener { uri ->
                                                updateMap["coverPicUrl"] = uri.toString()
                                                pending--
                                                if (pending == 0) performFinalUpdate()
                                            }
                                        }
                                    }
                                }
                            } else {
                                performFinalUpdate()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AppGreen),
                        enabled = !isSaving
                    ) {
                        if (isSaving) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
                        else Text("SAVE")
                    }
                }
            }
        }
    }
}
@Composable
fun CreateGroupDialog(
    contacts: List<ChatItem>,
    onDismiss: () -> Unit,
    onCreate: (String, List<ChatItem>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedContacts by remember { mutableStateOf<List<ChatItem>>(emptyList()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E1E1E),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("New Group", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(24.dp))
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Group Name", color = TextSecondary.copy(alpha = 0.6f)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = AppGreen
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("Select Members", color = TextSecondary, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyColumn(modifier = Modifier.height(200.dp)) {
                    items(contacts) { contact ->
                        val isSelected = selectedContacts.contains(contact)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedContacts = if (isSelected) {
                                        selectedContacts - contact
                                    } else {
                                        selectedContacts + contact
                                    }
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                modifier = Modifier.size(40.dp),
                                color = Color.DarkGray
                            ) {
                                AsyncImage(
                                    model = if (contact.profilePicUrl.isEmpty()) R.drawable.ic_launcher_background else contact.profilePicUrl,
                                    contentDescription = null,
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(contact.name, color = TextPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { _ ->
                                    selectedContacts = if (isSelected) {
                                        selectedContacts - contact
                                    } else {
                                        selectedContacts + contact
                                    }
                                },
                                colors = CheckboxDefaults.colors(checkedColor = AppGreen)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("CANCEL", color = TextSecondary.copy(alpha = 0.6f))
                    }
                    Button(
                        onClick = { if (name.isNotBlank() && selectedContacts.isNotEmpty()) onCreate(name, selectedContacts) },
                        colors = ButtonDefaults.buttonColors(containerColor = AppGreen),
                        enabled = name.isNotBlank() && selectedContacts.isNotEmpty()
                    ) {
                        Text("CREATE")
                    }
                }
            }
        }
    }
}

@Composable
fun StatusViewerOverlay(status: StatusItem, onDismiss: () -> Unit) {
    var progress by remember { mutableFloatStateOf(0f) }
    var showInteractions by remember { mutableStateOf(false) }
    val currentUid = FirebaseAuth.getInstance().currentUser?.uid
    val db = FirebaseFirestore.getInstance()
    
    // View Tracking
    LaunchedEffect(status.statusId) {
        if (currentUid != null && status.uid != currentUid && !status.viewers.contains(currentUid)) {
            db.collection("statuses").document(status.statusId)
                .update("viewers", com.google.firebase.firestore.FieldValue.arrayUnion(currentUid))
        }
    }

    // Auto-advance logic: 5 seconds per status (Pause when interactions shown)
    LaunchedEffect(showInteractions) {
        if (!showInteractions) {
            val duration = 5000f
            val interval = 50f
            while (progress < 1f) {
                kotlinx.coroutines.delay(interval.toLong())
                progress += (interval / duration)
            }
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Background Status Image
            AsyncImage(
                model = status.imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { if (!showInteractions) onDismiss() },
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )

            // Top UI: Progress Bar & Info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp)
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = TextPrimary,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Gray),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(status.name.firstOrNull()?.toString()?.uppercase() ?: "?", color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(status.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(status.time, color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }
            
            // Bottom UI: Viewers & Likes (Owner only) or Like Button
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 40.dp)
            ) {
                if (status.uid == currentUid) {
                    // Owner view: Show Viewers count
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clickable { showInteractions = true },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, tint = Color.White)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Visibility, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(status.viewers.size.toString(), color = Color.White, fontSize = 14.sp)
                            Spacer(Modifier.width(12.dp))
                            Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(status.likes.size.toString(), color = Color.White, fontSize = 14.sp)
                        }
                    }
                } else {
                    // Friend view: Show Like button
                    val isLiked = status.likes.contains(currentUid)
                    IconButton(
                        onClick = {
                            if (currentUid != null) {
                                val docRef = db.collection("statuses").document(status.statusId)
                                if (isLiked) {
                                    docRef.update("likes", com.google.firebase.firestore.FieldValue.arrayRemove(currentUid))
                                } else {
                                    docRef.update("likes", com.google.firebase.firestore.FieldValue.arrayUnion(currentUid))
                                }
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f))
                    ) {
                        Icon(
                            if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (isLiked) Color.Red else Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // Interaction Bottom Sheet
            if (showInteractions) {
                StatusInteractionsSheet(
                    likes = status.likes,
                    viewers = status.viewers,
                    onDismiss = { showInteractions = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusInteractionsSheet(
    likes: List<String>,
    viewers: List<String>,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 32.dp)) {
            Text("Activity", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            
            val allIds = (likes + viewers).distinct()
            val userMap = remember { mutableStateMapOf<String, ChatItem>() }
            val db = FirebaseFirestore.getInstance()
            
            LaunchedEffect(allIds) {
                allIds.forEach { uid ->
                    db.collection("users").document(uid).get().addOnSuccessListener { doc ->
                        if (doc != null) {
                            userMap[uid] = ChatItem(
                                uid = uid,
                                name = doc.getString("name") ?: "User",
                                profilePicUrl = doc.getString("profilePicUrl") ?: ""
                            )
                        }
                    }
                }
            }
            
            LazyColumn {
                if (likes.isNotEmpty()) {
                    item { Text("Liked by", color = AppGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    items(likes) { uid ->
                        val user = userMap[uid]
                        InteractionItem(user, isLike = true)
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
                
                if (viewers.isNotEmpty()) {
                    item { Text("Viewed by", color = SkyBlueAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    items(viewers) { uid ->
                        val user = userMap[uid]
                        InteractionItem(user, isLike = false)
                    }
                }
                
                if (likes.isEmpty() && viewers.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No activity yet", color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InteractionItem(user: ChatItem?, isLike: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(modifier = Modifier.size(40.dp), shape = CircleShape, color = Color.DarkGray) {
            if (user?.profilePicUrl?.isNotEmpty() == true) {
                AsyncImage(model = user.profilePicUrl, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            } else {
                Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray, modifier = Modifier.padding(8.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(user?.name ?: "Loading...", color = Color.White, modifier = Modifier.weight(1f))
        if (isLike) {
            Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun CallsScreen(
    onCallClick: (Boolean, String, String) -> Unit,
    contactAliases: Map<String, String> = emptyMap()
) {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val db = FirebaseFirestore.getInstance()
    var selectedFilter by remember { mutableIntStateOf(0) } // 0 = All, 1 = Missed
    val calls = remember { mutableStateListOf<CallItem>() }

    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotEmpty()) {
            db.collection("call_logs")
                .whereArrayContains("participantIds", currentUserId)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(50)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        calls.clear()
                        val timeFormatter = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())

                        snapshot.documents.forEach { doc ->
                            val timestamp = doc.getTimestamp("timestamp")
                            val senderId = doc.getString("senderId") ?: ""
                            val receiverId = doc.getString("receiverId") ?: ""
                            val isIncoming = receiverId == currentUserId
                            val isVideo = doc.getBoolean("isVideoCall") ?: doc.getBoolean("isVideo") ?: false
                            
                            // Get names from the log if available
                            val loggedCallerName = doc.getString("callerName") ?: ""
                            val loggedReceiverName = doc.getString("receiverName") ?: ""
                            val loggedCallerPic = doc.getString("callerPic") ?: doc.getString("callerPicUrl") ?: ""
                            val loggedReceiverPic = doc.getString("receiverPic") ?: doc.getString("receiverPicUrl") ?: ""
                            
                            val timeLabel = if (timestamp != null) timeFormatter.format(timestamp.toDate()) else "Now"
                            val status = doc.getString("status") ?: "completed"
                            val otherId = if (isIncoming) senderId else receiverId
                            val chatId = doc.getString("chatId") ?: doc.id
                            val logId = doc.id
                            
                            // ✅ Priority: 1. Local Alias, 2. Logged Peer Name, 3. Fallback
                            val peerNameInLog = if (isIncoming) loggedCallerName else loggedReceiverName
                            val peerPicInLog = if (isIncoming) loggedCallerPic else loggedReceiverPic
                            
                            val initialName = contactAliases[otherId] 
                                ?: if (peerNameInLog.isNotBlank() && peerNameInLog != "User" && !peerNameInLog.equals("Me", ignoreCase = true)) peerNameInLog 
                                else "..."
                            
                            val duration = doc.getLong("duration")?.toInt() ?: 0
                            
                            val callItem = CallItem(
                                name = initialName,
                                time = timeLabel, 
                                isIncoming = isIncoming, 
                                status = status, 
                                isVideo = isVideo, 
                                chatId = chatId,
                                profilePicUrl = peerPicInLog,
                                otherUserId = otherId,
                                logId = logId,
                                duration = duration
                            )
                            
                            calls.add(callItem)
                            
                            // ✅ Resolve real name from Firestore profile (fallback only if missing from log)
                            if (initialName == "..." || initialName == otherId) {
                                if (otherId.isNotEmpty() && otherId != currentUserId) {
                                    db.collection("users").document(otherId).get().addOnSuccessListener { s ->
                                        val dbName = s.getString("name") ?: s.getString("phone") ?: otherId
                                        val dbAvatar = s.getString("profilePicUrl") ?: s.getString("profilePic") ?: ""
                                        
                                        val idx = calls.indexOfFirst { it.logId == logId }
                                        if (idx != -1) {
                                            calls[idx] = calls[idx].copy(name = dbName, profilePicUrl = dbAvatar)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
        }
    }

    val filteredCalls = when(selectedFilter) {
        1 -> calls.filter { (it.status == "missed" || it.status == "declined") && it.isIncoming }
        else -> calls
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Filter Tabs
        TabRow(
            selectedTabIndex = selectedFilter,
            containerColor = Color.Transparent,
            contentColor = AppGreen,
            divider = {},
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedFilter]),
                    color = AppGreen
                )
            }
        ) {
            Tab(selected = selectedFilter == 0, onClick = { selectedFilter = 0 }) {
                Text("All", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
            }
            Tab(selected = selectedFilter == 1, onClick = { selectedFilter = 1 }) {
                Text("Missed", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold, color = if (selectedFilter == 1) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurface)
            }
        }

        if (filteredCalls.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        modifier = Modifier.size(80.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null, tint = Color.Gray.copy(alpha = 0.5f), modifier = Modifier.padding(20.dp))
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(if (selectedFilter == 1) "No missed calls" else "No call history", color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(filteredCalls) { call ->
                    CallListItem(
                        call = call,
                        contactAliases = contactAliases,
                        onCallClick = { isVideo, chatId, name ->
                            onCallClick(isVideo, chatId, name)
                        }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 72.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                    )
                }
            }
        }
    }
}
