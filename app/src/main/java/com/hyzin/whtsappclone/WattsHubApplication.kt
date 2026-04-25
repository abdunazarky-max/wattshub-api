package com.hyzin.whtsappclone

import android.app.Application
import org.webrtc.PeerConnectionFactory

class WattsHubApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // 🟢 Proper Global WebRTC Initialization
        // 1. Initialize WebRTC PeerConnectionFactory
        try {
            val options = PeerConnectionFactory.InitializationOptions.builder(applicationContext)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(options)
        } catch (e: Exception) {
            android.util.Log.e("WattsHubApp", "WebRTC Initialization failed: ${e.message}")
        }
        
        // 🧱 Firestore Caching & Persistence Optimization
        // 2. Configure Firestore Settings
        try {
            val settings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .setCacheSizeBytes(com.google.firebase.firestore.FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                .build()
            com.google.firebase.firestore.FirebaseFirestore.getInstance().firestoreSettings = settings
            android.util.Log.d("WattsHubApp", "✅ Firestore Persistence Enabled")
        } catch (e: Exception) {
            android.util.Log.w("WattsHubApp", "Firestore settings could not be set (already initialized): ${e.message}")
        }

        android.util.Log.d("WattsHubApp", "✅ App initialized with WebRTC and Firestore Persistence")
    }
}
