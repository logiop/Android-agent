package com.logiop.androidagent.security

import android.content.Context
import android.util.Base64
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/**
 * Append-only, encrypted log of every action the agent performs (the plan's
 * "log locale cifrato di ogni azione").
 *
 * Each entry is encrypted independently with AES-256/GCM using a Keystore-backed
 * key and stored as `base64(iv):base64(ciphertext)`, one per line. The plaintext
 * never touches disk, and the file is readable only on this device.
 */
class AuditLog(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)
    private val key by lazy { AuditKey.getOrCreate() }
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

    @Synchronized
    fun record(event: String) {
        try {
            val plaintext = "${timestampFormat.format(Date())} $event"
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val line = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(ciphertext, Base64.NO_WRAP) + "\n"
            FileOutputStream(file, /* append = */ true).use {
                it.write(line.toByteArray(Charsets.US_ASCII))
            }
        } catch (t: Throwable) {
            Log.w(TAG, "audit record failed", t)
        }
    }

    /** Decrypts and returns all entries, oldest first. */
    @Synchronized
    fun readAll(): List<String> {
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull { decrypt(it) }
    }

    private fun decrypt(line: String): String? = try {
        val parts = line.split(":", limit = 2)
        if (parts.size != 2) {
            null
        } else {
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        }
    } catch (t: Throwable) {
        null
    }

    private companion object {
        const val FILE_NAME = "agent_audit.log"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val TAG = "AndroidAgent"
    }
}
