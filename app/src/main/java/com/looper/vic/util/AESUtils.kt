package com.looper.vic.util

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

// AES-128
object AESUtils {
    fun encrypt(text: String, key: ByteArray): String {
        // Generate a random IV.
        val iv = ByteArray(16)
        SecureRandom().nextBytes(iv)
        val ivSpec = IvParameterSpec(iv)

        // Pad the plaintext.
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val secretKeySpec = SecretKeySpec(key, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivSpec)
        val encrypted = cipher.doFinal(text.toByteArray(StandardCharsets.UTF_8))

        // Combine IV and encrypted data.
        val encryptedData = iv + encrypted
        return Base64.encodeToString(encryptedData, Base64.DEFAULT)
    }

    fun decrypt(text: String, key: ByteArray): String {
        // Decode the base64 encoded text.
        val encryptedData = Base64.decode(text, Base64.DEFAULT)

        // Extract the IV from the encrypted data.
        val iv = encryptedData.sliceArray(0 until 16)
        val encrypted = encryptedData.sliceArray(16 until encryptedData.size)
        val ivSpec = IvParameterSpec(iv)

        // Decrypt the data.
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val secretKeySpec = SecretKeySpec(key, "AES")
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivSpec)
        val decryptedPadded = cipher.doFinal(encrypted)

        return String(decryptedPadded, StandardCharsets.UTF_8)
    }

    fun keyFromString(base64String: String): ByteArray {
        return Base64.decode(base64String, Base64.DEFAULT)
    }

    fun generateKey(): ByteArray {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(128)
        val secretKey = keyGen.generateKey()
        return secretKey.encoded
    }
}