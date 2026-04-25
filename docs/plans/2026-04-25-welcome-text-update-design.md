# Design Document: Welcome Screen Text Update

## Overview
Update the welcome message shown on the splash screen from "Welcome back!" to "Welcome WattsHub".

## Goals
- Change the welcome text to "Welcome WattsHub".
- Follow Android best practices by using string resources instead of hardcoded strings.
- Maintain existing styles and logo.

## Design
The text will be moved from a hardcoded string in `MainActivity.kt` to `strings.xml`.

### Resource Changes
- **File**: `app/src/main/res/values/strings.xml`
- **Entry**: `<string name="welcome_message">Welcome WattsHub</string>`

### UI Changes
- **File**: `app/src/main/java/com/hyzin/whtsappclone/MainActivity.kt`
- **Component**: `WelcomeScreen`
- **Modification**: Replace `"Welcome back!"` with `stringResource(R.string.welcome_message)`.

## Success Criteria
- The screen displays "Welcome WattsHub" on startup.
- The app compiles and runs without resource errors.
