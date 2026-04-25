package com.hyzin.whtsappclone.utils

import android.content.Context
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import com.google.android.play.core.integrity.IntegrityTokenResponse
import com.google.android.gms.tasks.Task

/**
 * Manages Google Play Integrity API requests to verify app and device authenticity.
 */
object PlayIntegrityManager {

    /**
     * Requests an integrity token from Google Play.
     * @param context App context
     * @param nonce A unique string from your server to prevent replay attacks.
     */
    fun getIntegrityToken(context: Context, nonce: String, onResult: (String?, Exception?) -> Unit) {
        val integrityManager = IntegrityManagerFactory.create(context)

        // Create the integrity token request
        val integrityTokenRequest = IntegrityTokenRequest.builder()
            .setNonce(nonce)
            .build()

        // Request the integrity token
        val integrityTokenResponse: Task<IntegrityTokenResponse> =
            integrityManager.requestIntegrityToken(integrityTokenRequest)

        integrityTokenResponse.addOnSuccessListener { response ->
            val integrityToken = response.token()
            onResult(integrityToken, null)
        }.addOnFailureListener { exception ->
            onResult(null, exception)
        }
    }
}
