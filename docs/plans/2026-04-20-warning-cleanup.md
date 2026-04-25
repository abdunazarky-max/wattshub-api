# Warning Cleanup & Modernization Implementation Plan

> **For Antigravity:** REQUIRED SUB-SKILL: Load executing-plans to implement this plan task-by-task.

**Goal:** Resolve build warnings, icon deprecations, and modern audio management issues in WattsHub.

**Architecture:** Use `@Suppress("UNCHECKED_CAST")` for Firestore operations where type erasure is unavoidable. Update icons to AutoMirrored versions. Implement version-safe audio routing for speakerphone.

**Tech Stack:** Kotlin, Jetpack Compose, WebRTC, Firebase Firestore.

---

### Task 1: Fix AuthenticatorScreen.kt
**Files:**
- Modify: `app/src/main/java/com/hyzin/whtsappclone/AuthenticatorScreen.kt`

**Step 1: Apply fixes to AuthenticatorScreen.kt**
Replace `Icons.Default.ArrowBack` with `Icons.AutoMirrored.Filled.ArrowBack` and fix CircularProgressIndicator progress.

**Step 2: Commit**
```bash
git add app/src/main/java/com/hyzin/whtsappclone/AuthenticatorScreen.kt
git commit -m "refactor: update icons and fix progress indicator in AuthenticatorScreen"
```

---

### Task 2: Fix SignalingClient.kt Unchecked Casts
**Files:**
- Modify: `app/src/main/java/com/hyzin/whtsappclone/SignalingClient.kt`

**Step 1: Suppress unchecked casts**
Add `@Suppress("UNCHECKED_CAST")` to Firestore map retrievals.

**Step 2: Commit**
```bash
git add app/src/main/java/com/hyzin/whtsappclone/SignalingClient.kt
git commit -m "refactor: fix unchecked cast warnings in SignalingClient"
```

---

### Task 3: Modern Audio Management in WebRTCClient & CallScreen
**Files:**
- Modify: `app/src/main/java/com/hyzin/whtsappclone/WebRTCClient.kt`
- Modify: `app/src/main/java/com/hyzin/whtsappclone/CallScreen.kt`
- Create: `app/src/main/java/com/hyzin/whtsappclone/utils/AudioUtils.kt`

**Step 1: Create version-safe AudioUtils**
Create a helper to handle speakerphone switching for API 31+.

**Step 2: Update WebRTCClient and CallScreen**
Replace direct `isSpeakerphoneOn` calls with `AudioUtils.setSpeakerphoneOn`.

**Step 3: Commit**
```bash
git add app/src/main/java/com/hyzin/whtsappclone/utils/AudioUtils.kt app/src/main/java/com/hyzin/whtsappclone/WebRTCClient.kt app/src/main/java/com/hyzin/whtsappclone/CallScreen.kt
git commit -m "fix: implement version-safe speakerphone management for API 31+"
```

---

### Task 4: HomeScreen & General Cleanup
**Files:**
- Modify: `app/src/main/java/com/hyzin/whtsappclone/HomeScreen.kt`

**Step 1: Remove unused imports and fix casts**
Clean up `HomeScreen.kt` warnings.

**Step 2: Commit**
```bash
git add app/src/main/java/com/hyzin/whtsappclone/HomeScreen.kt
git commit -m "refactor: cleanup warnings and unused imports in HomeScreen"
```
