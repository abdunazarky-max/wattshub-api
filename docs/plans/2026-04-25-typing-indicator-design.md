# Design: Live Typing & Voice Recording Indicators (1:1 Chats)

## Goal
Show real-time typing and voice recording status on the `HomeScreen` chat list for 1:1 conversations.

## Architecture
We will migrate the typing status from a chat-specific sub-collection to the `users` collection. This allows the `HomeScreen` to leverage its existing "Presence Engine" (which already watches the `users` documents of active contacts) to display typing status without extra queries.

## Data Model Changes

### Firestore: `users/{userId}`
Add the following fields:
- `typingChatId`: `String?` (The ID of the chat the user is currently active in)
- `typingState`: `String?` (Values: `"typing"`, `"recording"`, or `null`)

## Component Changes

### 1. `ChatDetailScreen.kt`
- **Text Entry**: Update the `onTextChange` logic to set `typingChatId` and `typingState = "typing"` on the current user's document.
- **Voice Recording**: Update `onStartRecording` and `onStopRecording` to set/clear `typingState = "recording"`.
- **Cleanup**: Ensure these fields are cleared when the user leaves the screen, sends the message, or the typing debounce timeout (1500ms) is reached.

### 2. `HomeScreen.kt`
- **Data Model**: Update `ChatItem` to include `typingChatId` and `typingState`.
- **Presence Engine**: Update the listener in `HomeScreen` to extract `typingChatId` and `typingState` from the user snapshots.
- **UI (`ChatListItem`)**: 
    - Compare `chat.typingChatId == currentChatId`.
    - If it matches and `typingState` is not null, display "typing..." or "recording voice..." in place of `lastMessage`.
    - Apply a distinct style (e.g., `AppGreen`) to make it pop.

## Performance Considerations
- **Firestore Writes**: Throttled by existing debounce logic in `ChatDetailScreen`.
- **Firestore Reads**: Zero additional reads on the `HomeScreen` because we are reusing the existing presence listener (limited to top 30 active users).

## Security Rules
Ensure users can only update their own `typingChatId` and `typingState` fields.
