
package com.hyzin.whtsappclone

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import android.util.Log

object NetworkUtils {
    // ── CONFIGURATION ─────────────────
    // Using Google Apps Script (True Free 24/7 Backend - No Blaze Plan Needed)
    private const val OTP_API_URL = "https://script.google.com/macros/s/AKfycbyJlQZGc824KzW8Y2xO0u9m8OjKw5MHp8oRKx5rIMVsKbem4Il52wT5jmaweQ7s_aGN/exec"
    private const val CLOUD_FUNCTIONS_BASE_URL = "https://us-central1-whtsappclonebackend.cloudfunctions.net"
    private const val NODE_SERVER_URL = "http://10.223.107.150:3005" // Matched with SocketClient

    suspend fun verifyIntegrity(token: String): NetworkResult {
        val payload = JsonObject().apply {
            addProperty("type", "verifyIntegrity")
            addProperty("token", token)
        }
        return makePostRequest(OTP_API_URL, payload)
    }

    suspend fun sendVerificationCode(target: String, type: String = "email", otp: String, userId: String? = null): NetworkResult {
        val payload = JsonObject().apply {
            addProperty("to", target)
            addProperty("type", type)
            addProperty("otp", otp)
            if (userId != null) addProperty("userId", userId)
        }
        // Use Google Apps Script backend (free, always-on, no Blaze plan needed)
        return makePostRequest(OTP_API_URL, payload)
    }

    suspend fun sendSecureVerification(userId: String, email: String, deviceId: String = "", secretIdentity: String = ""): NetworkResult {
        val payload = JsonObject().apply {
            addProperty("type", "sendSecureOTP")
            addProperty("userId", userId)
            addProperty("email", email)
            addProperty("newDeviceId", deviceId)
            if (secretIdentity.isNotEmpty()) addProperty("secretIdentity", secretIdentity)
        }
        return makePostRequest(OTP_API_URL, payload)
    }

    suspend fun verifyAuthenticator(userId: String, token: String, secret: String? = null): NetworkResult {
        val payload = JsonObject().apply {
            addProperty("type", "verifyAuthenticator")
            addProperty("userId", userId)
            addProperty("token", token)
            if (secret != null) addProperty("secret", secret)
        }
        return makePostRequest(OTP_API_URL, payload)
    }

    suspend fun validateOtp(target: String, otp: String): NetworkResult {
        val payload = JsonObject().apply {
            addProperty("to", target)
            addProperty("otp", otp)
        }
        return makePostRequest("$NODE_SERVER_URL/validate-otp", payload)
    }

    suspend fun requestMobileUpdate(userId: String, newMobile: String): NetworkResult {
        val payload = JsonObject().apply {
            addProperty("userId", userId)
            addProperty("newMobile", newMobile)
        }
        return makePostRequest("$NODE_SERVER_URL/request-mobile-update", payload)
    }

    suspend fun confirmMobileUpdate(userId: String, newMobile: String, otp: String): NetworkResult {
        val payload = JsonObject().apply {
            addProperty("userId", userId)
            addProperty("newMobile", newMobile)
            addProperty("otp", otp)
        }
        return makePostRequest("$NODE_SERVER_URL/confirm-mobile-update", payload)
    }

    suspend fun getActiveDevices(userId: String): List<DeviceInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val snapshot = com.google.android.gms.tasks.Tasks.await(db.collection("users").document(userId).get())
                val activeId = snapshot.getString("activeDeviceId") ?: ""
                if (activeId.isNotEmpty()) {
                    listOf(DeviceInfo(activeId, "Current Android Device", "online", "Just now"))
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    suspend fun removeDevice(userId: String, deviceId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                com.google.android.gms.tasks.Tasks.await(db.collection("users").document(userId)
                    .update("activeDeviceId", com.google.firebase.firestore.FieldValue.delete()))
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    private suspend fun makePostRequest(urlString: String, payload: JsonObject): NetworkResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("NetworkUtils", "📡 POST to: $urlString")
                Log.d("NetworkUtils", "📦 Payload: $payload")
                
                var currentUrl = urlString
                var redirectCount = 0
                val maxRedirects = 5

                while (redirectCount < maxRedirects) {
                    val url = URL(currentUrl)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = if (redirectCount == 0) "POST" else "GET"
                    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    conn.setRequestProperty("Accept", "application/json")
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; rv:109.0) Gecko/110.0 Firefox/110.0")
                    conn.setRequestProperty("Connection", "close")
                    
                    conn.doOutput = (conn.requestMethod == "POST")
                    conn.doInput = true
                    conn.connectTimeout = 30000
                    conn.readTimeout = 30000
                    conn.instanceFollowRedirects = false

                    if (conn.requestMethod == "POST") {
                        conn.outputStream.bufferedWriter().use { it.write(payload.toString()) }
                    }

                    val code = conn.responseCode
                    Log.d("NetworkUtils", "📡 Attempt: $redirectCount, Status: $code, URL: $currentUrl")

                    if (code in 300..399) {
                        val newLocation = conn.getHeaderField("Location")
                        if (newLocation != null) {
                            currentUrl = if (newLocation.startsWith("http")) newLocation 
                                         else "${url.protocol}://${url.host}$newLocation"
                            redirectCount++
                            conn.disconnect()
                            continue
                        }
                    }

                    if (code in 200..299) {
                        val responseBody = conn.inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                        Log.d("NetworkUtils", "✅ Success: $responseBody")
                        conn.disconnect()
                        return@withContext NetworkResult.Success
                    } else {
                        val body = (conn.errorStream ?: conn.inputStream)?.bufferedReader()?.use { it.readText() } ?: "No response body"
                        Log.e("NetworkUtils", "❌ HTTP $code: $body")
                        conn.disconnect()
                        return@withContext NetworkResult.Error("HTTP $code: $body")
                    }
                }
                NetworkResult.Error("Too many redirects ($redirectCount)")
            } catch (e: java.net.UnknownHostException) {
                Log.e("NetworkUtils", "❌ DNS Error: Cannot resolve host", e)
                NetworkResult.Error("No internet connection")
            } catch (e: java.net.SocketTimeoutException) {
                Log.e("NetworkUtils", "❌ Timeout", e)
                NetworkResult.Error("Connection timed out")
            } catch (e: javax.net.ssl.SSLException) {
                Log.e("NetworkUtils", "❌ SSL Error", e)
                NetworkResult.Error("SSL error: ${e.message}")
            } catch (e: java.io.IOException) {
                Log.e("NetworkUtils", "❌ IO Error: ${e.javaClass.simpleName}", e)
                NetworkResult.Error("Network error: ${e.javaClass.simpleName}: ${e.message}")
            } catch (e: Exception) {
                Log.e("NetworkUtils", "❌ ${e.javaClass.simpleName}: ${e.message}", e)
                NetworkResult.Error("${e.javaClass.simpleName}: ${e.message ?: "Unknown"}")
            }
        }
    }
}

data class DeviceInfo(val id: String, val name: String, val status: String, val lastActive: String)

sealed class NetworkResult {
    object Success : NetworkResult()
    data class Error(val message: String) : NetworkResult()
}

