package com.hyzin.whtsappclone

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.*
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object EncryptionManager {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "whatsapp_clone_e2ee_key"
    private const val RSA_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
    private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"

    /**
     * Generates an RSA key pair in the Android KeyStore if it doesn't exist.
     * The private key never leaves the KeyStore.
     */
    fun generateKeyPairIfNotExists(): String {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA,
                ANDROID_KEYSTORE
            )
            val parameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                .build()

            kpg.initialize(parameterSpec)
            kpg.generateKeyPair()
        }

        val publicKey = keyStore.getCertificate(KEY_ALIAS).publicKey
        return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
    }

    private fun getPrivateKey(): PrivateKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        return keyStore.getKey(KEY_ALIAS, null) as? PrivateKey
    }

    /**
     * Encrypts a message using the recipient's public key.
     * Returns a map containing encrypted message, encrypted key, and IV.
     */
    fun encryptMessage(text: String, recipientPublicKeyBase64: String): Map<String, String> {
        // 1. Generate random AES key
        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES)
        keyGen.init(256)
        val aesKey = keyGen.generateKey()

        // 2. Encrypt text with AES-GCM
        val aesCipher = Cipher.getInstance(AES_TRANSFORMATION)
        aesCipher.init(Cipher.ENCRYPT_MODE, aesKey)
        val iv = aesCipher.iv
        val encryptedMessageBytes = aesCipher.doFinal(text.toByteArray(Charsets.UTF_8))

        // 3. Encrypt AES key with Recipient's RSA Public Key
        val publicKeyBytes = Base64.decode(recipientPublicKeyBase64, Base64.NO_WRAP)
        val keyFactory = KeyFactory.getInstance("RSA")
        val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes))

        val rsaCipher = Cipher.getInstance(RSA_TRANSFORMATION)
        rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val encryptedAesKey = rsaCipher.doFinal(aesKey.encoded)

        return mapOf(
            "encryptedText" to Base64.encodeToString(encryptedMessageBytes, Base64.NO_WRAP),
            "encryptedKey" to Base64.encodeToString(encryptedAesKey, Base64.NO_WRAP),
            "iv" to Base64.encodeToString(iv, Base64.NO_WRAP)
        )
    }

    /**
     * Decrypts a message using the locally stored private key.
     */
    fun decryptMessage(encryptedText: String, encryptedKey: String, iv: String): String {
        try {
            // 1. Decrypt AES key with local Private Key
            val privateKey = getPrivateKey() ?: throw IllegalStateException("Local Private Key not found in KeyStore")
            val rsaCipher = Cipher.getInstance(RSA_TRANSFORMATION)
            rsaCipher.init(Cipher.DECRYPT_MODE, privateKey)
            
            val aesKeyBytes = rsaCipher.doFinal(Base64.decode(encryptedKey, Base64.NO_WRAP))
            val aesKey = SecretKeySpec(aesKeyBytes, 0, aesKeyBytes.size, "AES")

            // 2. Decrypt text with AES-GCM
            val aesCipher = Cipher.getInstance(AES_TRANSFORMATION)
            val gcmSpec = GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            aesCipher.init(Cipher.DECRYPT_MODE, aesKey, gcmSpec)
            val decryptedBytes = aesCipher.doFinal(Base64.decode(encryptedText, Base64.NO_WRAP))

            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            // Provide context for failure
            return "[Decryption Error: ${e.javaClass.simpleName}${if (e.message != null) ": ${e.message}" else ""}]"
        }
    }
}
