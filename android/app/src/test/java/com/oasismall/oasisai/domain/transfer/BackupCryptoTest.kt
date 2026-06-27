package com.oasismall.oasisai.domain.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BackupCryptoTest {

    @Test
    fun encryptDecrypt_roundTrip() {
        val dir = createTempDir("backup-crypto")
        val plain = File(dir, "backup.zip")
        plain.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x01, 0x02, 0x03))
        val encrypted = File(dir, "backup.enc")
        val decrypted = File(dir, "backup-out.zip")
        val password = "test-pass".toCharArray()
        try {
            BackupCrypto.encrypt(plain, encrypted, password)
            assertTrue(BackupCrypto.isEncrypted(encrypted))
            BackupCrypto.decrypt(encrypted, decrypted, password)
            assertEquals(plain.readBytes().toList(), decrypted.readBytes().toList())
        } finally {
            password.fill('\u0000')
        }
    }

    @Test
    fun isEncrypted_detectsMagicHeader() {
        val dir = createTempDir("backup-crypto")
        val file = File(dir, "x.zip")
        file.writeBytes((BackupCrypto.MAGIC + "payload").toByteArray(Charsets.US_ASCII))
        assertTrue(BackupCrypto.isEncrypted(file))
    }
}
