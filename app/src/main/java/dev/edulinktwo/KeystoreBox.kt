package dev.edulinktwo

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES/GCM blob sealing with a 256-bit key that never leaves the Android
 * Keystore. Blob layout: [iv length byte][iv][ciphertext+tag].
 * Device-only by design: no backup export of plaintext is possible because
 * plaintext is never written anywhere.
 */
class KeystoreBox : BlobBox {
    companion object {
        private const val KS = "AndroidKeyStore"
        private const val ALIAS = "edulinktwo.credentials.v1"
        private const val TRANSFORM = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128

        /** App-scoped store: sealed blob lives in the app-private files dir. */
        fun store(context: Context): CredentialStore =
            CredentialStore(File(context.filesDir, "credentials.enc"), KeystoreBox())
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KS).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KS)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    override fun seal(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.ENCRYPT_MODE, key())
        val ct = c.doFinal(plain)
        val iv = c.iv
        return byteArrayOf(iv.size.toByte()) + iv + ct
    }

    override fun open(blob: ByteArray): ByteArray {
        require(blob.size > 1) { "blob too short" }
        val ivLen = blob[0].toInt()
        require(ivLen > 0 && blob.size > 1 + ivLen) { "bad blob" }
        val iv = blob.copyOfRange(1, 1 + ivLen)
        val ct = blob.copyOfRange(1 + ivLen, blob.size)
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return c.doFinal(ct)
    }
}
