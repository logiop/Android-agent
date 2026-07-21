package com.logiop.androidagent.brain

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Locates and imports the on-device LLM model file.
 *
 * The model (a MediaPipe `.task` bundle) is too large to ship in the APK, so it
 * lives in the app's private files dir and is imported by the user from a file
 * they downloaded (phone-only workflow, via the Storage Access Framework).
 */
object ModelRepository {

    private const val MODEL_DIR = "models"
    private const val MODEL_FILE = "model.task"
    private const val COPY_BUFFER = 1 shl 16

    fun modelFile(context: Context): File {
        val dir = File(context.filesDir, MODEL_DIR).apply { mkdirs() }
        return File(dir, MODEL_FILE)
    }

    fun isReady(context: Context): Boolean {
        val file = modelFile(context)
        return file.exists() && file.length() > 0
    }

    /** Copies the picked document into the model location. Returns success. */
    fun importFrom(context: Context, uri: Uri): Boolean {
        val target = modelFile(context)
        val tmp = File(target.parentFile, "$MODEL_FILE.tmp")
        return try {
            context.contentResolver.openInputStream(uri).use { input ->
                if (input == null) return false
                FileOutputStream(tmp).use { output -> input.copyTo(output, COPY_BUFFER) }
            }
            if (target.exists()) target.delete()
            tmp.renameTo(target)
        } catch (e: IOException) {
            tmp.delete()
            false
        }
    }
}
