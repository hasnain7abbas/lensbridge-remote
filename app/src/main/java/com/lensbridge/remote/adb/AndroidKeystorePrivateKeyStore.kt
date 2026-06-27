package com.lensbridge.remote.adb

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.flyfishxu.kadb.cert.KadbPrivateKeyStore
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Persists the ADB host private key encrypted by a non-exportable Android Keystore key. */
class AndroidKeystorePrivateKeyStore(context: Context) : KadbPrivateKeyStore {
    private val file = File(context.noBackupFilesDir, "adb_identity.bin")
    private val tempFile = File(context.noBackupFilesDir, "adb_identity.tmp")

    override fun readPrivateKeyPem(): ByteArray? {
        if (!file.exists()) return null
        return runCatching {
            DataInputStream(file.inputStream().buffered()).use { input ->
                require(input.readInt() == FILE_VERSION)
                val iv = ByteArray(input.readUnsignedByte())
                input.readFully(iv)
                val encrypted = ByteArray(input.readInt())
                input.readFully(encrypted)
                Cipher.getInstance(TRANSFORMATION).run {
                    init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
                    doFinal(encrypted)
                }
            }
        }.getOrElse {
            clear()
            null
        }
    }

    override fun writePrivateKeyPemAtomic(privateKeyPem: ByteArray) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        }
        val encrypted = cipher.doFinal(privateKeyPem)
        val bytes = ByteArrayOutputStream().use { output ->
            DataOutputStream(output).use { data ->
                data.writeInt(FILE_VERSION)
                data.writeByte(cipher.iv.size)
                data.write(cipher.iv)
                data.writeInt(encrypted.size)
                data.write(encrypted)
            }
            output.toByteArray()
        }
        tempFile.outputStream().use { it.write(bytes) }
        check(tempFile.renameTo(file) || run { tempFile.copyTo(file, overwrite = true); tempFile.delete(); true })
    }

    override fun clear() {
        file.delete()
        tempFile.delete()
        keyStore().deleteEntry(KEY_ALIAS)
    }

    private fun getOrCreateKey(): SecretKey {
        val store = keyStore()
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generateKey()
        }
    }

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private companion object {
        const val FILE_VERSION = 1
        const val KEY_ALIAS = "lensbridge_adb_host_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
