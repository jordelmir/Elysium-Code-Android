package com.elysium.code.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

/**
 * ═══════════════════════════════════════════════════════════════
 * ELYSIUM CODE — Media & Clipboard Utilities
 * ═══════════════════════════════════════════════════════════════
 */
object MediaUtils {

    fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Elysium Code Generated Content", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    fun getFromClipboard(context: Context): String {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (clipboard.hasPrimaryClip() && clipboard.primaryClip?.itemCount!! > 0) {
            val item = clipboard.primaryClip?.getItemAt(0)
            return item?.text?.toString() ?: ""
        }
        return ""
    }

    fun saveMediaToDevice(context: Context, resourcePath: String, type: MediaType) {
        // En una implementación de extracción de Gemma 4 E4B,
        // este archivo (proveniente del generador) se copia a MediaStore (Environment.DIRECTORY_DOWNLOADS)
        // Por la maqueta actual vamos a mostrar un Toast de simulación de Matrix
        Toast.makeText(context, "Extracting ${type.name} to device storage...", Toast.LENGTH_LONG).show()
    }
}

enum class MediaType {
    IMAGE, AUDIO, VIDEO, FILE
}
