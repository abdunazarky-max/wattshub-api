# Typing Indicator Implementation Plan

> **For Antigravity:** REQUIRED SUB-SKILL: Load executing-plans to implement this plan task-by-task.

**Goal:** Show animated *"typing..."* in the chat top bar when the other person is typing, with debounce, lifecycle cleanup, and disconnect safety.

**Architecture:** All logic is self-contained within `ChatDetailScreen.kt`. The sender side writes a boolean `isTyping` flag to a Firestore `typingStatus/{chatId}/{userId}` document using a coroutine-debounced Job. The receiver side listens to the contact's node with a `DisposableEffect` and uses `AnimatedContent` in the top bar to crossfade between status strings.

**Tech Stack:** Kotlin Coroutines, Firebase Firestore, Jetpack Compose `AnimatedContent`

---

## Context for the Implementer

The entire `ChatDetailScreen.kt` file uses inline Composable state — no ViewModels. All Firestore listeners are registered in `DisposableEffect` blocks and the `coroutineScope` variable (`rememberCoroutineScope()`) already exists at the top of `ChatDetailScreen`. Follow that exact same pattern.

The file is at:
```
app/src/main/java/com/hyzin/whtsappclone/ChatDetailScreen.kt
```

Key existing variables in `ChatDetailScreen`:
- `val coroutineScope = rememberCoroutineScope()` — reuse this
- `val currentUserId = auth.currentUser?.uid ?: ""`
- `val chatId = ...` — already computed
- `val isOnline by remember { ... }` — used in top bar subtitle
- `val isGroup: Boolean` — parameter, skip typing logic when `true`
- `val db = FirebaseFirestore.getInstance()`

The top bar subtitle `Text` is at approximately line 952–956:
```kotlin
Text(
    text = if (isOnline) "Online" else "last seen recently",
    fontSize = 11.sp,
    color = Color.White.copy(alpha = 0.8f)
)
```

---

## Task 1: Add Typing State Variables

**File:** Modify `ChatDetailScreen.kt` — add two `remember` state vars near the other state declarations (around line 531–535, after `isContactBlocked`).

**Step 1: Add state variables**

Insert after line ~535 (`var isContactBlocked by remember { mutableStateOf(false) }`):

```kotlin
// Typing Indicator State
var isContactTyping by remember { mutableStateOf(false) }
var typingDebounceJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
var isSelfTypingActive by remember { mutableStateOf(false) }
```

`isSelfTypingActive` is a local boolean guard to avoid writing `isTyping=true` to Firestore on every single keystroke — we only write once when typing starts.

**Step 2: Commit**

```bash
git add app/src/main/java/com/hyzin/whtsappclone/ChatDetailScreen.kt
git commit -m "feat(typing): add typing indicator state variables"
```

---

## Task 2: Write Typing Status to Firestore (Sender Side)

**File:** Modify `ChatDetailScreen.kt` — update the `onTextChange` lambda passed to `ChatInputBar` (around line 1027).

**Current code (line ~1027):**
```kotlin
onTextChange = { messageText = it },
```

**Step 1: Replace with debounced typing logic**

```kotlin
onTextChange = { newText ->
    messageText = newText
    if (!isGroup) {
        if (newText.isEmpty()) {
            // User cleared the box — cancel debounce, immediately set false
            typingDebounceJob?.cancel()
            typingDebounceJob = null
            if (isSelfTypingActive) {
                isSelfTypingActive = false
                db.collection("typingStatus").document(chatId)
                    .collection("users").document(currentUserId)
                    .set(mapOf("isTyping" to false))
            }
        } else {
            // Write isTyping=true only on first keystroke of a typing session
            if (!isSelfTypingActive) {
                isSelfTypingActive = true
                db.collection("typingStatus").document(chatId)
                    .collection("users").document(currentUserId)
                    .set(mapOf("isTyping" to true))
            }
            // Cancel previous debounce and restart the 1500ms countdown
            typingDebounceJob?.cancel()
            typingDebounceJob = coroutineScope.launch {
                kotlinx.coroutines.delay(1500)
                isSelfTypingActive = false
                db.collection("typingStatus").document(chatId)
                    .collection("users").document(currentUserId)
                    .set(mapOf("isTyping" to false))
            }
        }
    }
},
```

**Step 2: Also reset typing on message send**

In the `onSend` lambda (around line 1028–1049), after `messageText = ""`, add:

```kotlin
// Reset typing status immediately on send
typingDebounceJob?.cancel()
typingDebounceJob = null
if (isSelfTypingActive) {
    isSelfTypingActive = false
    db.collection("typingStatus").document(chatId)
        .collection("users").document(currentUserId)
        .set(mapOf("isTyping" to false))
}
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/hyzin/whtsappclone/ChatDetailScreen.kt
git commit -m "feat(typing): write debounced isTyping status to Firestore on sender side"
```

---

## Task 3: Firestore Listener for Contact's Typing Status (Receiver Side)

**File:** Modify `ChatDetailScreen.kt` — add a new `DisposableEffect` block after the existing socket relay `LaunchedEffect` (after line ~839).

**Step 1: Add the listener**

```kotlin
// Typing Indicator Listener — listen to contact's typing status
DisposableEffect(chatId, contactId) {
    if (isGroup || contactId.isEmpty() || currentUserId.isEmpty()) {
        return@DisposableEffect onDispose {}
    }
    val typingRef = db.collection("typingStatus").document(chatId)
        .collection("users").document(contactId)
    val typingListener = typingRef.addSnapshotListener { snap, _ ->
        isContactTyping = snap?.getBoolean("isTyping") ?: false
    }
    onDispose {
        typingListener.remove()
        // Also reset our own typing status when leaving the screen
        typingDebounceJob?.cancel()
        if (isSelfTypingActive) {
            isSelfTypingActive = false
            db.collection("typingStatus").document(chatId)
                .collection("users").document(currentUserId)
                .set(mapOf("isTyping" to false))
        }
    }
}
```

**Why in `DisposableEffect`:** This mirrors the existing `contactListener` and `messageListener` patterns in the file. It auto-detaches when the composable leaves the composition, preventing memory leaks.

**Step 2: Commit**

```bash
git add app/src/main/java/com/hyzin/whtsappclone/ChatDetailScreen.kt
git commit -m "feat(typing): listen to contact isTyping Firestore node with DisposableEffect"
```

---

## Task 4: Animate the Top Bar Subtitle

**File:** Modify `ChatDetailScreen.kt` — replace the static subtitle `Text` in the top bar with `AnimatedContent`.

**Current code (lines ~952–956):**
```kotlin
Text(
    text = if (isOnline) "Online" else "last seen recently", 
    fontSize = 11.sp, 
    color = Color.White.copy(alpha = 0.8f)
)
```

**Step 1: Add the import at the top of the file**

Add to the existing import block:
```kotlin
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
```

**Step 2: Replace the subtitle Text**

```kotlin
val subtitleText = when {
    isContactTyping -> "typing..."
    isOnline -> "Online"
    else -> "last seen recently"
}
AnimatedContent(
    targetState = subtitleText,
    transitionSpec = { fadeIn() togetherWith fadeOut() },
    label = "typing_status"
) { statusText ->
    Text(
        text = statusText,
        fontSize = 11.sp,
        color = if (isContactTyping) AppGreen else Color.White.copy(alpha = 0.8f)
    )
}
```

The text turns green when the contact is typing, matching WattsHub's brand color. It fades smoothly between states.

**Step 3: Commit**

```bash
git add app/src/main/java/com/hyzin/whtsappclone/ChatDetailScreen.kt
git commit -m "feat(typing): animate top bar subtitle between online/typing states"
```

---

## Task 5: Verify Build and Behavior

**Step 1: Build the project**

```bash
cd /home/kali/AndroidStudioProjects/WhtsAppClone
./gradlew assembleDebug 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

**Step 2: Manual verification checklist**

1. Open a 1-on-1 chat and start typing → contact's device should show *"typing..."* in the top bar
2. Stop typing for 1.5 seconds → top bar reverts to "Online" / "last seen recently"
3. Send the message → typing status resets immediately
4. Navigate back while typing → status resets (onDispose handler)
5. Open a group chat → no typing indicator appears at all

**Step 3: Final commit**

```bash
git add .
git commit -m "feat: complete live typing indicator for 1-on-1 chats"
```
