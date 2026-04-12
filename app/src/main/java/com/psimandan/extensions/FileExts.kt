package com.psimandan.extensions

import android.content.Context
import java.io.File

/**
 * Extracts a file from the APK's assets folder to the app's internal storage.
 * Returns the absolute physical path of the extracted file.
 */
fun Context.extractModelFromAssets(modelFileName: String, force: Boolean = false): String {
    val destFile = File(this.filesDir, modelFileName)

    // Only perform the heavy copy operation if the file hasn't been extracted yet
    if (force || !destFile.exists()) {
        this.assets.open(modelFileName).use { inputStream ->
            destFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    }

    return destFile.absolutePath
}