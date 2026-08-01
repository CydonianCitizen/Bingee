package com.cydoniancitizen.bingee.data.credential

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject

internal class AndroidKeystoreTmdbCredentialCipher @Inject constructor() : TmdbCredentialCipher {
    override fun encrypt(value: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))

        return ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(cipher.iv.size)
                output.write(cipher.iv)
                output.writeInt(encrypted.size)
                output.write(encrypted)
            }
            bytes.toByteArray()
        }
    }

    override fun decrypt(value: ByteArray): String {
        require(value.size <= MAX_BLOB_SIZE)
        val input = DataInputStream(ByteArrayInputStream(value))
        require(input.readInt() == MAGIC)
        val ivSize = input.readInt()
        require(ivSize in MIN_IV_SIZE..MAX_IV_SIZE)
        val iv = ByteArray(ivSize).also(input::readFully)
        val encryptedSize = input.readInt()
        require(encryptedSize in MIN_CIPHERTEXT_SIZE..MAX_BLOB_SIZE)
        val encrypted = ByteArray(encryptedSize).also(input::readFully)
        require(input.available() == 0)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
    }

    override fun deleteKey() {
        synchronized(this) {
            keyStore().deleteEntry(KEY_ALIAS)
        }
    }

    private fun getOrCreateKey(): SecretKey = synchronized(this) {
        val keyStore = keyStore()
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey) ?: generateKey()
    }

    private fun generateKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return keyGenerator.generateKey()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "bingee_tmdb_read_access_token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val MAGIC = 0x42474331
        const val GCM_TAG_BITS = 128
        const val MIN_IV_SIZE = 12
        const val MAX_IV_SIZE = 32
        const val MIN_CIPHERTEXT_SIZE = 16
        const val MAX_BLOB_SIZE = 16 * 1024
    }
}
