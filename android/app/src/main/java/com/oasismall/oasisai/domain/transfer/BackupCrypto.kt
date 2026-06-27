package com.oasismall.oasisai.domain.transfer

import java.io.File
import java.io.InputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** AES-256-GCM backup encryption (optional password at export time). */
object BackupCrypto {
    const val MAGIC = "OASBK1"
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val PBKDF2_ITERATIONS = 120_000
    private const val GCM_TAG_BITS = 128

    fun isEncrypted(file: File): Boolean =
        file.isFile && file.length() > MAGIC.length && file.inputStream().use { peekMagic(it) }

    fun isEncryptedHeader(bytes: ByteArray): Boolean =
        bytes.size >= MAGIC.length && String(bytes, 0, MAGIC.length, Charsets.US_ASCII) == MAGIC

    fun encrypt(source: File, dest: File, password: CharArray) {
        val salt = randomBytes(SALT_BYTES)
        val iv = randomBytes(IV_BYTES)
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        dest.parentFile?.mkdirs()
        dest.outputStream().use { raw ->
            raw.write(MAGIC.toByteArray(Charsets.US_ASCII))
            raw.write(salt)
            raw.write(iv)
            CipherOutputStream(raw, cipher).use { encrypted ->
                source.inputStream().use { input -> input.copyTo(encrypted) }
            }
        }
    }

    fun decrypt(source: File, dest: File, password: CharArray) {
        source.inputStream().use { raw ->
            val magicBuf = ByteArray(MAGIC.length)
            require(inputReadFully(raw, magicBuf) && String(magicBuf, Charsets.US_ASCII) == MAGIC) {
                "Not an encrypted Visio Ai backup"
            }
            val salt = ByteArray(SALT_BYTES)
            val iv = ByteArray(IV_BYTES)
            require(inputReadFully(raw, salt) && inputReadFully(raw, iv)) {
                "Encrypted backup file is truncated"
            }
            val key = deriveKey(password, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            dest.parentFile?.mkdirs()
            CipherInputStream(raw, cipher).use { decrypted ->
                dest.outputStream().use { out -> decrypted.copyTo(out) }
            }
        }
    }

    private fun peekMagic(input: InputStream): Boolean {
        val buf = ByteArray(MAGIC.length)
        return input.read(buf) == MAGIC.length && String(buf, Charsets.US_ASCII) == MAGIC
    }

    private fun inputReadFully(input: InputStream, buf: ByteArray): Boolean {
        var offset = 0
        while (offset < buf.size) {
            val read = input.read(buf, offset, buf.size - offset)
            if (read < 0) return false
            offset += read
        }
        return true
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    private fun randomBytes(size: Int): ByteArray = ByteArray(size).also { SecureRandom().nextBytes(it) }
}
