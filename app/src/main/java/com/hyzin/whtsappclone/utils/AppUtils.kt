package com.hyzin.whtsappclone.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * 🔍 Helper to find Activity from any Context (unwrapping ContextWrappers)
 */
fun Context.getActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) return currentContext
        currentContext = currentContext.baseContext
    }
    return null
}
