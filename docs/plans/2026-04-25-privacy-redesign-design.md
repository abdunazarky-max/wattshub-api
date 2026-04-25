# Design Document: Premium Privacy Dashboard Redesign

## Overview
Redesign the Privacy settings screen to move away from the standard WhatsApp look towards a premium "Layered Depth" design. Implement full Firestore synchronization for all privacy settings.

## Goals
- **Premium UI**: Use a card-based layout with grouped sections and high-quality iconography.
- **Firestore Sync**: Persist all privacy settings (Last Seen, Profile Photo, Read Receipts, etc.) in the user's Firestore document.
- **Modern Interaction**: Use Bottom Sheets for option selection instead of full screens or dialogs.

## Design

### Data Model (Firestore)
The user document will contain a `privacy` map:
```json
{
  "privacy": {
    "lastSeen": "Everyone",
    "profilePhoto": "My contacts",
    "about": "Everyone",
    "status": "My contacts",
    "readReceipts": true,
    "defaultTimer": "Off",
    "groups": "Everyone",
    "liveLocation": "None"
  }
}
```

### UI Architecture
- **PrivacySettingsScreen**: Main container using `Scaffold`.
- **SectionCard**: Reusable component for grouping items into rounded cards.
- **PrivacyListItem**: Reusable row component with icon, title, and current value.
- **PrivacySelectionBottomSheet**: A generic Bottom Sheet component that takes a list of options and updates Firestore on selection.

### Styling
- **Corners**: `24.dp` for cards.
- **Colors**: Background `0xFF121212`, Card `0xFF1E1E1E`, Accent `AppGreen` or `SkyBlueAccent`.
- **Icons**: Outlined/Stroke Material icons for a lighter, modern feel.

## Success Criteria
- The Privacy screen has a distinct, premium look.
- All settings (Last Seen, etc.) persist in Firestore and sync in real-time.
- Bottom Sheets function correctly for all selectable options.
