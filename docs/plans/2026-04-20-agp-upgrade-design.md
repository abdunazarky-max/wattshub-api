# Design Doc: Upgrade Android Gradle Plugin (8.7.2 -> 8.13.2)

## Overview
This design covers the upgrade of the Android Gradle Plugin (AGP) in the `WhtsAppClone` project to match the version suggested by the Android Studio Upgrade Assistant.

## Current State
- **Project Path**: `/home/kali/AndroidStudioProjects/WhtsAppClone`
- **Current AGP Version**: `8.7.2`
- **Gradle Version**: `9.3.1` (already compatible)

## Target State
- **Target AGP Version**: `8.13.2`
- **Build Status**: Successful `assembleDebug` after upgrade.

## Implementation Details

### 1. Version Catalog Update
- Modify `gradle/libs.versions.toml` to update the `agp` version string.
- This ensures all modules using the standard `android-application` or `android-library` plugins are updated simultaneously.

### 2. Verification Strategy
- Run `./gradlew clean` to ensure no stale artifacts remain.
- Run `./gradlew assembleDebug` to verify compilation.
- Review build logs for any new deprecation warnings introduced by the upgrade.

## Risks & Mitigations
- **Breaking Changes**: Although a minor/mid-version jump, AGP 8.13 might introduce stricter linting or property requirements.
- **Mitigation**: I will monitor the build output and fix any immediate configuration errors.
