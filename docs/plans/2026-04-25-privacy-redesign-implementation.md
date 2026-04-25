# Premium Privacy Dashboard Implementation Plan

> **For Antigravity:** REQUIRED SUB-SKILL: Load executing-plans to implement this plan task-by-task.

**Goal:** Redesign the Privacy screen with a premium "Layered Depth" UI and full Firestore synchronization.

**Architecture:** 
- Grouped card-based layout in `PrivacySettingsScreen.kt`.
- `collectAsState` for real-time Firestore synchronization.
- `ModalBottomSheetLayout` (or `ModalBottomSheet`) for modern option selection.

**Tech Stack:** Kotlin, Jetpack Compose, Firebase Firestore.

---

### Task 1: UI Components - SectionCard & PrivacyListItem

**Files:**
- Modify: `app/src/main/java/com/hyzin/whtsappclone/PrivacySettingsScreen.kt`

**Step 1: Implement SectionCard and updated PrivacyListItem**

Create reusable components that follow the "Layered Depth" design:
- `SectionCard`: Rounded `Surface` with `16.dp` padding.
- `PrivacyListItem`: Updated with icons and premium typography.

**Step 2: Commit**

```bash
git add app/src/main/java/com/hyzin/whtsappclone/PrivacySettingsScreen.kt
git commit -m "ui: implement premium card and list item components for Privacy screen"
```

---

### Task 2: Firestore State Management

**Files:**
- Modify: `app/src/main/java/com/hyzin/whtsappclone/PrivacySettingsScreen.kt`

**Step 1: Setup Firestore listener**

1. Add `val db = FirebaseFirestore.getInstance()` and `val currentUserId = FirebaseAuth.getInstance().currentUser?.uid`.
2. Use `produceState` or `remember` + `LaunchedEffect` to listen to the `privacy` map in the user document.

**Step 2: Define default privacy state**

Handle the case where the `privacy` map doesn't exist yet in Firestore.

**Step 3: Commit**

```bash
git add app/src/main/java/com/hyzin/whtsappclone/PrivacySettingsScreen.kt
git commit -m "feat: implement real-time Firestore sync for Privacy settings"
```

---

### Task 3: Bottom Sheet Selection Logic

**Files:**
- Modify: `app/src/main/java/com/hyzin/whtsappclone/PrivacySettingsScreen.kt`

**Step 1: Implement ModalBottomSheet for selections**

1. Add `SheetState` and logic to show options for the currently selected category (Last Seen, Profile Photo, etc.).
2. Implement `PrivacySelectionSheet` content with clickable options.

**Step 2: Implement Firestore update function**

Create a function that updates the specific privacy field in Firestore when an option is selected.

**Step 3: Commit**

```bash
git add app/src/main/java/com/hyzin/whtsappclone/PrivacySettingsScreen.kt
git commit -m "feat: implement bottom sheet selection and Firestore updates"
```

---

### Task 4: Final Assembly & Cleanup

**Files:**
- Modify: `app/src/main/java/com/hyzin/whtsappclone/PrivacySettingsScreen.kt`

**Step 1: Connect all list items to the Bottom Sheet/Firestore**

Ensure all items (Last Seen, Profile Photo, About, Read Receipts, etc.) are fully functional.

**Step 2: Styling and polish**

Apply gradients, icons, and ensure consistency with the app's theme.

**Step 3: Commit**

```bash
git add app/src/main/java/com/hyzin/whtsappclone/PrivacySettingsScreen.kt
git commit -m "feat: complete Privacy dashboard redesign with all functions enabled"
```
