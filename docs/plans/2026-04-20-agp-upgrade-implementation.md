# AGP Upgrade (8.7.2 -> 8.13.2) Implementation Plan

> **For Antigravity:** REQUIRED SUB-SKILL: Load executing-plans to implement this plan task-by-task.

**Goal:** Upgrade the Android Gradle Plugin to version 8.13.2 and verify project stability.

**Architecture:** Use the Version Catalog (`libs.versions.toml`) to centralize the version change and verify using the Gradle wrapper.

**Tech Stack:** Gradle, Android Gradle Plugin, Version Catalogs.

---

### Task 1: Update AGP Version

**Files:**
- Modify: `/home/kali/AndroidStudioProjects/WhtsAppClone/gradle/libs.versions.toml`

**Step 1: Verify current version**
Run: `grep "agp =" /home/kali/AndroidStudioProjects/WhtsAppClone/gradle/libs.versions.toml`
Expected: `agp = "8.7.2"`

**Step 2: Update to 8.13.2**
Update the `agp` line in `[versions]` section.

**Step 3: Verify the change**
Run: `grep "agp =" /home/kali/AndroidStudioProjects/WhtsAppClone/gradle/libs.versions.toml`
Expected: `agp = "8.13.2"`

### Task 2: Build Verification

**Files:**
- No code changes, verifying build state.

**Step 1: Clean build artifacts**
Run: `cd /home/kali/AndroidStudioProjects/WhtsAppClone && ./gradlew clean`
Expected: `BUILD SUCCESSFUL`

**Step 2: Compile the project**
Run: `cd /home/kali/AndroidStudioProjects/WhtsAppClone && ./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

### Task 3: Unit Test Verification

**Files:**
- No code changes, verifying logic state.

**Step 1: Run unit tests**
Run: `cd /home/kali/AndroidStudioProjects/WhtsAppClone && ./gradlew testDebugUnitTest`
Expected: `BUILD SUCCESSFUL` with all tests passing.

### Task 4: Finalize and Commit

**Step 1: Review for warnings**
Review the build output from Task 2 & 3 for any NEW deprecation warnings or lint errors.

**Step 2: Commit the changes**
Run:
```bash
git -C /home/kali/AndroidStudioProjects/WhtsAppClone add gradle/libs.versions.toml docs/plans/2026-04-20-agp-upgrade-design.md
git -C /home/kali/AndroidStudioProjects/WhtsAppClone commit -m "chore(deps): upgrade AGP from 8.7.2 to 8.13.2"
```
Expected: Commit successful.
