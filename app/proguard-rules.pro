# ══════════════════════════════════════════════════════════════════════
# WattsHub — ProGuard / R8 Obfuscation Rules
# Maximum obfuscation + shrinking for production APK
# ══════════════════════════════════════════════════════════════════════

# ── AGGRESSIVE OBFUSCATION ───────────────────────────────────────────
-optimizationpasses 5
-dontusemixedcaseclassnames
-verbose
-allowaccessmodification
-mergeinterfacesaggressively
-repackageclasses ''
-overloadaggressively

# Remove all logging in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}

# Remove System.out/err prints
-assumenosideeffects class java.io.PrintStream {
    public void println(...);
    public void print(...);
}

# ── KEEP: Android Core Components ────────────────────────────────────
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.preference.Preference

# Keep the main activities / services / receivers referenced in manifest
-keep class com.hyzin.whtsappclone.MainActivity { *; }
-keep class com.hyzin.whtsappclone.CallNotificationService { *; }
-keep class com.hyzin.whtsappclone.CallActionReceiver { *; }

# ── KEEP: Composable Functions ───────────────────────────────────────
# Jetpack Compose requires annotations & metadata to function
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ── KEEP: Firebase & Google Play Services ────────────────────────────
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**
-keep class com.google.firebase.auth.** { *; }
-keep class com.google.firebase.firestore.** { *; }
-keep class com.google.firebase.storage.** { *; }
-keep class com.google.firebase.database.** { *; }
-keep class com.google.firebase.remoteconfig.** { *; }

# Play Integrity API
-keep class com.google.android.play.core.integrity.** { *; }
-dontwarn com.google.android.play.core.integrity.**

# ── KEEP: Data Classes used with Firestore ───────────────────────────
# These are serialized/deserialized and field names must be preserved
-keep class com.hyzin.whtsappclone.NetworkUtils$DeviceInfo { *; }
-keepclassmembers class com.hyzin.whtsappclone.NetworkUtils$DeviceInfo { *; }

# Sealed classes referenced by type at runtime
-keep class com.hyzin.whtsappclone.NetworkResult { *; }
-keep class com.hyzin.whtsappclone.NetworkResult$* { *; }

# ── KEEP: Gson (JSON parsing) ────────────────────────────────────────
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Keep any model classes used by Gson
-keep class com.hyzin.whtsappclone.models.** { *; }

# Prevent Gson from stripping generic type info
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# ── KEEP: WebRTC & Stream SDK ────────────────────────────────────────
-keep class org.webrtc.** { *; }
-keep interface org.webrtc.** { *; }
-keep class io.getstream.webrtc.** { *; }
-dontwarn io.getstream.webrtc.**
-dontwarn org.webrtc.**

# ── KEEP: Socket.IO ─────────────────────────────────────────────────
-keep class io.socket.** { *; }
-dontwarn io.socket.**
-keep class io.engineio.** { *; }
-dontwarn io.engineio.**
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-keep class okio.** { *; }
-dontwarn okio.**

# ── KEEP: Coil (Image Loading) ──────────────────────────────────────
-keep class coil.** { *; }
-dontwarn coil.**

# ── KEEP: CameraX ───────────────────────────────────────────────────
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# ── KEEP: Kotlin Coroutines ─────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ── KEEP: Kotlin Metadata (required for reflection) ─────────────────
-keepattributes KotlinMetadata
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# ── KEEP: Native Methods ────────────────────────────────────────────
-keepclasseswithmembernames class * {
    native <methods>;
}

# ── KEEP: Enums ─────────────────────────────────────────────────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── KEEP: Parcelable/Serializable ────────────────────────────────────
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    !private <fields>;
    !private <methods>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ── KEEP: View/click handlers (XML referenced) ─────────────────────
-keepclassmembers class * {
    public void *(android.view.View);
}

# ── OBFUSCATION: String Encryption Hints ────────────────────────────
# R8 will aggressively rename everything NOT marked with -keep
# All internal logic, utilities, and helper classes will be obfuscated:
#   - EncryptionManager → a.b.c
#   - PhoneUtils → a.d
#   - AppProtection → a.e.f
#   - SignalingClient → a.g.h
#   - etc.

# ── SHRINKING: Remove unused code ────────────────────────────────────
# R8 automatically removes unreachable code when isMinifyEnabled = true
# This includes dead branches, unused methods, and unreferenced classes

# ── ADDITIONAL HARDENING ─────────────────────────────────────────────
# Remove source file names and line numbers from stack traces (security)
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# Suppress warnings for missing optional dependencies
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn com.facebook.react.**
-dontwarn com.facebook.yoga.**
-dontwarn com.facebook.fbjni.**
-dontwarn javax.annotation.**
-dontwarn sun.misc.**