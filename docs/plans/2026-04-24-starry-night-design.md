# Design: Starry Night Unique Theme for Privacy & Profile

## Overview
WattsHub is moving away from its "WhatsApp Clone" aesthetic to a more premium, unique, and futuristic "Elite" design. The centerpiece of this transformation is the **Starry Night Skin**, a high-end visual conversion that applies to the Privacy Control and Profile settings.

## Design Principles
1. **Glassmorphism**: Use frosted glass surfaces with varying blur and transparency levels.
2. **Neon Accents**: High-intensity neon borders (Purple/Cyan) to define interactive elements.
3. **Motion**: Constant, subtle movement via the Starry Night background to make the UI feel alive.
4. **Depth**: Use z-axis layering (shadows and overlays) to separate content from the cosmic background.

## UI Components

### 1. Privacy Control Dashboard (`PrivacySettingsScreen.kt`)
*   **Layout**: A grid of "Control Tiles" rather than a vertical list.
*   **Visuals**: 
    *   Background: `AnimatedBackground` (Starry Night).
    *   Tiles: Frosted glass containers with `NeonPurple` or `NeonCyan` borders.
    *   Interaction: Tiles pulse or change border intensity when selected or hovered.
*   **Sections**:
    *   *Identity*: Last Seen, Profile Photo, About.
    *   *Security*: App Lock, Blocked Contacts, Two-Step.
    *   *Stealth*: Disappearing Messages, Read Receipts.

### 2. Profile Identity Card (`EditProfileScreen.kt`)
*   **Header**: Cinematic full-width cover image with a gradient overlay.
*   **Card**: A floating glassmorphic card containing Name, Bio, and Contact info.
*   **Profile Pic**: Surrounded by a neon ring that pulses slowly.
*   **Form Fields**: Standardized neon-bordered input fields that replace the classic Material Design look.

### 3. Theme Engine Integration
*   **PreferenceManager**: Store the `SELECTED_THEME` as a string (`STARRY_NIGHT`, `OCEAN_BLUE`, `CLASSIC`).
*   **Global State**: A `currentTheme` State in the app's root to trigger instant UI updates across all glassmorphic components.

## Success Criteria
*   The Privacy and Profile screens no longer look like WhatsApp.
*   The user experience feels "Premium" and "Elite".
*   The "Starry Night" theme is consistently applied across both screens.
