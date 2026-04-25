# Starry Night Elite Implementation Plan

> **For Antigravity:** REQUIRED SUB-SKILL: Load executing-plans to implement this plan task-by-task.

**Goal:** Redesign the Privacy Control and Profile settings into a premium, futuristic "Starry Night" experience that differentiates WattsHub from WhatsApp.

**Architecture:** We will implement a glassmorphic design system using frosted tiles with neon borders. This system will be driven by a centralized theme state and persisted via local preferences.

**Tech Stack:** Kotlin, Jetpack Compose, Firebase Firestore, SharedPreferences.

---

### Task 1: Foundation - Neon Design System & Theme Support

**Files:**
- Modify: `app/src/main/java/com/hyzin/whtsappclone/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/hyzin/whtsappclone/ui/theme/Theme.kt`
- Modify: `app/src/main/java/com/hyzin/whtsappclone/utils/PreferenceManager.kt`

**Step 1: Add Neon Colors**
Add `NeonPurple`, `NeonCyan`, and `GlassBackground` to `Color.kt`.

**Step 2: Update Theme State**
Ensure `WhtsAppCloneTheme` can dynamically react to theme changes without an app restart.

**Step 3: Commit**
`git add . && git commit -m "feat: add neon colors and prepare theme engine for dynamic updates"`

---

### Task 2: Shared Components - The GlassTile & Neon Glow

**Files:**
- Create: `app/src/main/java/com/hyzin/whtsappclone/components/EliteComponents.kt`

**Step 1: Implement GlassTile**
Create a reusable `@Composable` `EliteGlassTile` that features:
- Frosted glass background (Blur + Alpha).
- Neon border with a subtle outer glow.
- Support for icons and titles.

**Step 2: Implement ThemeSelectionDialog**
Create a high-end dialog that lets users switch between "Starry Night", "Ocean Blue", and "Classic".

**Step 3: Commit**
`git add . && git commit -m "feat: implement EliteGlassTile and ThemeSelectionDialog"`

---

### Task 3: Privacy Control - The Elite Dashboard

**Files:**
- Modify: `app/src/main/java/com/hyzin/whtsappclone/PrivacySettingsScreen.kt`

**Step 1: Replace List with Tiles**
Redesign the `PrivacySettingsScreen` layout to use a `LazyVerticalGrid` or custom `FlowRow` of `EliteGlassTile` components.

**Step 2: Apply Starry Background**
Wrap the content in `AnimatedBackground` and apply the Starry Night specific color scheme.

**Step 3: Integrate Theme Switcher**
Add the "Unique Theme" entry point that launches the `ThemeSelectionDialog`.

**Step 4: Commit**
`git add . && git commit -m "feat: redesign PrivacySettingsScreen into Elite Dashboard"`

---

### Task 4: Profile Identity - The Glass Card

**Files:**
- Modify: `app/src/main/java/com/hyzin/whtsappclone/EditProfileScreen.kt`

**Step 1: Redesign Header & Card**
Change the `EditProfileScreen` to use a floating glass card for user details.
Add a neon ring around the profile picture.

**Step 2: Neon Input Fields**
Standardize the input fields to use neon borders when focused.

**Step 3: Commit**
`git add . && git commit -m "feat: redesign EditProfileScreen with Glass Card and Neon accents"`

---

### Task 5: Integration & Verification

**Files:**
- Modify: `app/src/main/java/com/hyzin/whtsappclone/MainActivity.kt`

**Step 1: Global Theme State**
Ensure `MainActivity` observes the theme preference and updates the root `WhtsAppCloneTheme`.

**Step 2: Verification**
Run the app (or verify via code review) that navigating between Privacy and Profile feels consistent and "Unique".

**Step 3: Commit**
`git add . && git commit -m "feat: finalize Starry Night Elite integration"`
