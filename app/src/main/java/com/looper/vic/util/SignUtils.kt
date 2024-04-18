package com.looper.vic.util

import androidx.annotation.Keep
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object SignUtils {
    fun createSignature(data: String, timestamp: Long, key: String): String {
        val message = "$timestamp:$data"
        val keySpec = SecretKeySpec(key.toByteArray(), "HmacSHA256")
        val mac = Mac.getInstance("HmacSHA256").apply {
            init(keySpec)
        }
        return mac.doFinal(message.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}