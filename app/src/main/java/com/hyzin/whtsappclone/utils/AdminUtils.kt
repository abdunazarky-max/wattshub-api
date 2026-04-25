package com.hyzin.whtsappclone.utils

import com.google.firebase.auth.FirebaseAuth

object AdminUtils {
    private val ADMIN_EMAILS = listOf("abdunazarky@gmail.com")

    fun isAdmin(email: String?): Boolean {
        if (email == null) return false
        return ADMIN_EMAILS.contains(email.trim().lowercase())
    }

    fun isCurrentUserAdmin(): Boolean {
        val user = FirebaseAuth.getInstance().currentUser
        return isAdmin(user?.email)
    }
}
