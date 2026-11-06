package com.shieldstream.tv.data.repository

import android.util.Base64
import com.shieldstream.tv.domain.IthilConfig
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class IthilLinkDecoderTest {
    @Test
    fun decryptsGeneratedAesOfbLinkWithStandardAndUrlSafeBase64() {
        val blob = ByteArray(48) { it.toByte() }
        val config = IthilConfig("https://metadata.test", Base64.encodeToString(blob, Base64.NO_WRAP), "fixture")
        val plain = "https://video.test/generated-fixture"
        val cipher = Cipher.getInstance("AES/OFB/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(blob, 16, 32, "AES"),
            IvParameterSpec(blob, 0, 16),
        )
        val ciphertext = cipher.doFinal(plain.toByteArray())
        val encryptedVariants = listOf(
            Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            Base64.encodeToString(ciphertext, Base64.URL_SAFE or Base64.NO_WRAP),
        )

        encryptedVariants.forEach { encrypted ->
            assertEquals(plain, IthilLinkDecoder(config).decrypt(encrypted))
        }
    }
}
