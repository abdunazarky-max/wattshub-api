# Call Signaling Hardening Implementation Plan

> **For Antigravity:** REQUIRED SUB-SKILL: Load executing-plans to implement this plan task-by-task.

**Goal:** Resolve call synchronization issues where Phone B rings indefinitely and Phone A "auto-declines" or handles cancellations incorrectly.

**Architecture:** Harden `SignalingClient` by ensuring synchronous state initialization before listening, and updating `CallScreen` lifecycle to guarantee terminal signaling on exit.

**Tech Stack:** Kotlin, Jetpack Compose, Firebase Firestore, WebRTC.

---

### Task 1: Harden SignalingClient Race Conditions

**Files:**
- Modify: `app/src/main/java/com/hyzin/whtsappclone/SignalingClient.kt`

**Step 1: Update sendOffer to await success before listening**
Ensure the "ringing" status is actually written before we start the listener, preventing us from reading stale "declined" states.

```kotlin
// In SignalingClient.kt
fun sendOffer(sdp: SessionDescription, callerName: String, callerAvatar: String, callLogId: String) {
    this.callLogId = callLogId
    val offerData = hashMapOf(...) // as before
    
    db.collection("signaling_calls").document(chatId).collection("candidates")
        .get().addOnSuccessListener { snapshot ->
            for (doc in snapshot.documents) {
                doc.reference.delete()
            }
            db.collection("signaling_calls").document(chatId).set(offerData).addOnSuccessListener {
                listenForSignaling() // ✅ Start listening ONLY AFTER set completes
            }
        }
    // Remove individual call to listenForSignaling() outside the block
}
```

**Step 2: Allow terminal states to bypass sender filtering**
Modify `listenForSignaling` to process `status` changes even if the `senderId` matches our own, ensuring we hear about terminal events in all cases.

```kotlin
// In SignalingClient.kt listenForSignaling
                // Ignore our own updates EXCEPT for terminal status changes
                if (senderId == currentUserId) {
                    val status = data["status"] as? String
                    if (status !in listOf("ended", "cancelled", "declined")) {
                        return@addSnapshotListener
                    }
                }
```

**Step 3: Commit**
```bash
git add app/src/main/java/com/hyzin/whtsappclone/SignalingClient.kt
git commit -m "fix(signaling): prevent race condition on offer and handle global terminal states"
```

---

### Task 2: Fix CallScreen Lifecycle Cleanup

**Files:**
- Modify: `app/src/main/java/com/hyzin/whtsappclone/CallScreen.kt`

**Step 1: Signal termination in onDispose**
Ensure the partner is notified when we leave the call screen unsignaled.

```kotlin
// In CallScreen.kt onDispose
        onDispose {
            if (CallActionReceiver.lastHandledCallId != callLogId) {
                // ... update call_logs (existing logic) ...
                
                // ✅ Add signaling notification
                if (!callConnected) {
                    if (isIncoming) signalingClient.declineCall() else signalingClient.cancelCall()
                } else {
                    signalingClient.endCall()
                }
            }
            // ... the rest of existing onDispose ...
        }
```

**Step 2: Commit**
```bash
git add app/src/main/java/com/hyzin/whtsappclone/CallScreen.kt
git commit -m "fix(call): insure terminal signaling on screen disposal"
```

---

### Task 3: Hardened CallNotificationService

**Files:**
- Modify: `app/src/main/java/com/hyzin/whtsappclone/CallNotificationService.kt`
- Modify: `app/src/main/java/com/hyzin/whtsappclone/CallActionReceiver.kt`

**Step 1: Improve Firestore Listener in Service**
Use more robust checks for document existence and status.

```kotlin
// In CallNotificationService.kt startSignalingListener
                if (snapshot == null || !snapshot.exists()) {
                    Log.d("CallService", "Signaling doc missing, stopping service.")
                    stopSelf()
                    return@addSnapshotListener
                }
                
                val status = snapshot.getString("status")
                val hasAnswer = snapshot.contains("answer") // safer check
                
                if (status in listOf("answered", "ended", "declined", "cancelled") || hasAnswer) {
                     stopSelf()
                }
```

**Step 2: Use standardized action for stopService**
Ensure `CallActionReceiver` and other callers use the `STOP_SERVICE` action to trigger the explicit `stopSelf()` path.

```kotlin
// In CallActionReceiver.kt
context.stopService(Intent(context, CallNotificationService::class.java).apply { action = "STOP_SERVICE" })
```

**Step 3: Commit**
```bash
git add app/src/main/java/com/hyzin/whtsappclone/CallNotificationService.kt app/src/main/java/com/hyzin/whtsappclone/CallActionReceiver.kt
git commit -m "fix(service): harden notification listener and standardize stop action"
```
