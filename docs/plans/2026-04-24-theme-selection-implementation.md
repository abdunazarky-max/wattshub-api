# Theme Selection Implementation Plan

> **For Antigravity:** REQUIRED SUB-SKILL: Load executing-plans to implement this plan task-by-task.

**Goal:** Add a full-screen theme selection page in Privacy settings to switch between Light, Starry Night, and Ocean Blue themes.

**Architecture:** Add a new route to the NavHost, create a dedicated ThemeSelectionScreen, and update PrivacySettingsScreen and MainActivity to handle navigation and theme state updates.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, SharedPreferences (PreferenceManager).

---

### Task 1: Update PrivacySettingsScreen Entry Point

**Files:**
- Modify: `app/src/main/java/com/hyzin/whtsappclone/PrivacySettingsScreen.kt`

**Step 1: Add onNavigateToThemeSelection parameter**
Update the `PrivacySettingsScreen` signature and add the "Theme" item.

```kotlin
@Composable
fun PrivacySettingsScreen(
    onBack: () -> Unit,
    onNavigateToThemeSelection: () -> Unit,
    currentTheme: String
) {
    // ... in the Column ...
    PrivacyItem("Theme", currentTheme) { onNavigateToThemeSelection() }
}
```

**Step 2: Update PrivacyItem to handle clicks**
Ensure the `PrivacyItem` component correctly triggers the click action.

**Step 3: Verify**
Manual verification: Ensure the code compiles (UI check will happen after navigation is wired).

---

### Task 2: Create ThemeSelectionScreen.kt

**Files:**
- Create: `app/src/main/java/com/hyzin/whtsappclone/ThemeSelectionScreen.kt`

**Step 1: Implement the UI**
Create a full-screen page with a list of themes (Light, Starry Night, Ocean Blue) and radio buttons.

```kotlin
// ... imports ...
@Composable
fun ThemeSelectionScreen(
    onBack: () -> Unit,
    currentTheme: String,
    onThemeSelected: (String) -> Unit
) {
    // Scaffold with TopAppBar and RadioButtons list
}
```

**Step 2: Add Radio Button Logic**
When a theme is selected, call `onThemeSelected` and then `onBack`.

**Step 3: Verify**
Manual verification: Ensure the file compiles and contains the three theme options.

---

### Task 3: Wire Navigation and State in MainActivity

**Files:**
- Modify: `app/src/main/java/com/hyzin/whtsappclone/MainActivity.kt`

**Step 1: Update selectedTheme state handling**
Pass a callback to update `selectedTheme` when a theme is picked.

**Step 2: Add navigation route**
Add the `"theme_selection"` route to the `NavHost`.

```kotlin
composable("theme_selection") {
    ThemeSelectionScreen(
        onBack = { navController.popBackStack() },
        currentTheme = selectedTheme,
        onThemeSelected = { newTheme ->
            selectedTheme = newTheme
            prefManager.setTheme(newTheme)
        }
    )
}
```

**Step 3: Verify**
1. Run the app.
2. Go to Privacy settings.
3. Click on "Theme".
4. Select "Ocean Blue".
5. Verify the background changes immediately.
6. Restart the app and verify the theme persists.
