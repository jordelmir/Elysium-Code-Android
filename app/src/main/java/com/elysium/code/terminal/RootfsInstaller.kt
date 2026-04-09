package com.elysium.code.terminal

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object RootfsInstaller {
    private const val TAG = "RootfsInstaller"

    /**
     * Extracts the bundled busybox binary to the app's internal files directory,
     * sets it as executable, and binds its applets via symlinks.
     * This provides a massive baseline of 300+ standard Linux commands (ls, grep, awk, wget, etc).
     */
    suspend fun setupBusybox(context: Context): String? = withContext(Dispatchers.IO) {
        try {
            val binDir = File(context.filesDir, "bin")
            if (!binDir.exists()) {
                binDir.mkdirs()
            }

            val busyboxFile = File(binDir, "busybox")
            if (!busyboxFile.exists() || busyboxFile.length() == 0L) {
                Log.i(TAG, "Extracting bundled busybox to ${busyboxFile.absolutePath}")
                context.assets.open("bin/busybox").use { input ->
                    busyboxFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                // Make executable
                busyboxFile.setExecutable(true)
                
                Log.i(TAG, "Busybox extracted and made executable")
                
                // Create symlinks for common tools (ash, sh, ls, etc)
                // We run busybox --install -s <binDir>
                val process = ProcessBuilder(busyboxFile.absolutePath, "--install", "-s", binDir.absolutePath)
                    .redirectErrorStream(true)
                    .start()
                
                process.waitFor()
                Log.i(TAG, "Busybox applets installed")
            } else {
                Log.i(TAG, "Busybox already installed at ${busyboxFile.absolutePath}")
            }
            
            return@withContext File(binDir, "ash").absolutePath
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup busybox runtime", e)
            return@withContext null
        }
    }
}
