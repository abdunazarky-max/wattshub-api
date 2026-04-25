package com.hyzin.whtsappclone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class StartupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.d("StartupReceiver", "WattsHub initialized on boot.")
            
            // Note: FCM and other Firebase services initialize automatically.
            // This receiver ensures the app process is primed and ready.
        }
    }
}
