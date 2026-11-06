package com.shieldstream.tv.data.repository

import android.util.Base64
import com.shieldstream.tv.domain.IthilConfig
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

internal class IthilLinkDecoder(config: IthilConfig) {
    private val keyAndIv = Base64.decode(config.linkSecret, Base64.DEFAULT)

    init {
        require(keyAndIv.size == 48) { "Ithil link secret must decode to 48 bytes" }
    }

    private val key = SecretKeySpec(keyAndIv, 16, 32, "AES")
    private val iv = IvParameterSpec(keyAndIv, 0, 16)
    private val cipher = Cipher.getInstance("AES/OFB/NoPadding")

    @Synchronized
    fun decrypt(encrypted: String): String {
        cipher.init(Cipher.DECRYPT_MODE, key, iv)
        val normalized = encrypted.replace('-', '+').replace('_', '/')
        val ciphertext = Base64.decode(normalized, Base64.DEFAULT)
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }
}
