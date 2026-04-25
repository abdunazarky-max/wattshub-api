# Theme Selection Design

## Goal
Implement a full-screen theme selection page accessible from the Privacy settings, allowing users to switch between Light, Starry Night, and Ocean Blue themes with immediate effect and persistence.

## Architecture & Components

### 1. Navigation
- New route: `"theme_selection"`
- Added to `NavHost` in `MainActivity.kt`.
- `PrivacySettingsScreen` navigates to this route when the "Theme" item is clicked.

### 2. PrivacySettingsScreen.kt
- Add a "Theme" item in the list.
- Display the current theme name as a subtitle.
- Accept an `onNavigateToThemeSelection` callback.

### 3. ThemeSelectionScreen.kt (NEW)
- A full-screen Composable.
- Contains a list of themes: `LIGHT`, `STARRY_NIGHT`, `OCEAN_BLUE`.
- Uses `RadioButton` for selection.
- Triggers `onThemeChange` when an option is selected.

### 4. Data Flow
- `MainActivity` manages `selectedTheme` state.
- `PreferenceManager` persists the `selectedTheme` string.
- `ThemeSelectionScreen` calls a callback that updates `MainActivity` state and `PreferenceManager`.

## UI/UX
- Follows WhatsApp's list-based selection pattern but as a full-screen page.
- Uses the app's existing design system (AppGreen, transparency, gradients).
- Immediate visual feedback upon selection.

## Verification Plan
- Verify that the "Theme" item appears in Privacy settings.
- Verify that clicking it opens the selection page.
- Verify that selecting each theme updates the app's background and colors immediately.
- Verify that the selection is saved after restarting the app.
