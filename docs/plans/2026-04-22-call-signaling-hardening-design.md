# Design Doc: Call Signaling Hardening

## Overview
This design addresses persistent call-signaling synchronization issues in the WattsHub application. Specifically, it targets the "auto-decline" race condition on the caller side and the "zombie ringtone" issue on the receiver side.

## Problem Statement
1. **Auto-Decline:** `SignalingClient` starts listening for updates before the new "ringing" state is persisted, causing it to read the previous call's "declined" or "ended" state and terminate the new call immediately.
2. **Missing Cleanup:** `CallScreen`'s `onDispose` updates logs but does not signal the terminal state to the `signaling_calls` collection, leaving the partner device ringing indefinitely if the user navigates back.
3. **Sender Filtering:** `SignalingClient` ignores updates from the current user based on the `from` field. Since the receiver doesn't update the `from` field when declining/ending, the caller ignores the signal.

## Proposed Changes

### 1. SignalingClient (SignalingClient.kt)
- **Synchronous Initialization:** Modify `sendOffer` to ensure `set()` completes before calling `listenForSignaling()`.
- **Global Terminal State Tracking:** Process `status` updates (`declined`, `ended`, `cancelled`) even if the `from` ID matches the current user, ensuring that terminal states are always respected.
- **Atomic State Updates:** Ensure `updateCallLogId` is handled before processing new signaling events to avoid filtering mismatches.

### 2. Call Lifecycle (CallScreen.kt)
- **Explicit Cleanup:** Update the `onDispose` block to call `signalingClient.cancelCall()` or `signalingClient.endCall()` based on connection status.
- **Tone Management:** Ensure `toneGenerator` is stopped in all terminal paths.

### 3. Notification Service (CallNotificationService.kt)
- **Hardened Listener:** Implement logic to stop the service immediately if the signaling document is missing or in a terminal state.
- **Standardized Control:** Use `STOP_SERVICE` action consistently for all local and remote termination requests.

## Data Flow
1. **A calls B:** A sets status to "ringing" -> waits for success -> starts listener.
2. **B rings:** B's service sees "ringing" -> starts ringtone + listener.
3. **A cancels:** A calls `cancelCall()` -> updates Firestore doc -> B's service sees "cancelled" -> calls `stopSelf()` -> `onDestroy` stops ringtone.
4. **B answers:** B calls `answer()` -> updates Firestore doc -> A's listener sees "answered" -> starts WebRTC connection.

## Error Handling
- **Timeout Fallback:** Retain the 30-second safety timeout in `CallNotificationService` as a final guard against connectivity loss.
- **Document Absence:** Treat a missing signaling document as a "cancelled/ended" state.
