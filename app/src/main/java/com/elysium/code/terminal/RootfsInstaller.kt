package com.elysium.code.terminal

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import java.io.InputStream

object RootfsInstaller {
    private const val TAG = "RootfsInstaller"
    private const val UBUNTU_URL = "https://cloud-images.ubuntu.com/noble/current/noble-server-cloudimg-arm64-root.tar.xz"

    /**
     * Extracts a working static proot binary for aarch64
     */
    suspend fun setupProot(context: Context): String? = withContext(Dispatchers.IO) {
        try {
            val binDir = File(context.filesDir, "bin")
            val prootFile = File(binDir, "proot")
            if (!prootFile.exists() || prootFile.length() == 0L) {
                context.assets.open("bin/proot_aarch64").use { input ->
                    prootFile.outputStream().use { output -> input.copyTo(output) }
                }
                prootFile.setExecutable(true)
            }
            return@withContext prootFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Proot setup failed", e)
            null
        }
    }

    suspend fun setupBusybox(context: Context): String? = withContext(Dispatchers.IO) {
        try {
            val binDir = File(context.filesDir, "bin")
            if (!binDir.exists()) binDir.mkdirs()

            val busyboxFile = File(binDir, "busybox")
            if (!busyboxFile.exists() || busyboxFile.length() == 0L) {
                context.assets.open("bin/busybox").use { input ->
                    busyboxFile.outputStream().use { output -> input.copyTo(output) }
                }
                busyboxFile.setExecutable(true)
                ProcessBuilder(busyboxFile.absolutePath, "--install", "-s", binDir.absolutePath)
                    .start().waitFor()
            }
            return@withContext File(binDir, "ash").absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Busybox setup failed", e)
            null
        }
    }

    /**
     * Automated "Staff Level" Bootstrap:
     * 1. Downloads a minimal Ubuntu aarch64 rootfs
     * 2. Extracts it to internal storage
     * 3. Provisions PRoot for sandboxed execution
     */
    suspend fun setupUbuntu(context: Context, onProgress: (Float) -> Unit): String? = withContext(Dispatchers.IO) {
        try {
            val ubuntuDir = File(context.filesDir, "ubuntu")
            val markerFile = File(ubuntuDir, ".installed")
            
            if (markerFile.exists()) {
                return@withContext ubuntuDir.absolutePath
            }

            if (!ubuntuDir.exists()) ubuntuDir.mkdirs()

            val tarFile = File(context.cacheDir, "ubuntu_rootfs.tar.xz")
            
            // Download phase
            Log.i(TAG, "Starting Ubuntu rootfs download...")
            downloadFile(UBUNTU_URL, tarFile, onProgress)

            // Extraction phase
            Log.i(TAG, "Extracting rootfs...")
            val busybox = File(context.filesDir, "bin/busybox").absolutePath
            val process = ProcessBuilder(
                busybox, "tar", "-xJf", tarFile.absolutePath, "-C", ubuntuDir.absolutePath
            ).start()
            
            process.waitFor()
            tarFile.delete() // Cleanup
            
            markerFile.createNewFile()
            return@withContext ubuntuDir.absolutePath

        } catch (e: Exception) {
            Log.e(TAG, "Ubuntu bootstrap failed", e)
            null
        }
    }

    private suspend fun downloadFile(url: String, target: File, onProgress: (Float) -> Unit) {
        val client = HttpClient(OkHttp)
        val response = client.get(url) {
            onDownload { bytesSentTotal, contentLength ->
                if (contentLength != null && contentLength > 0) {
                    onProgress(bytesSentTotal.toFloat() / contentLength)
                }
            }
        }
        
        val input = response.bodyAsChannel().toInputStream()
        target.outputStream().use { output ->
            input.copyTo(output)
        }
        client.close()
    }

    /**
     * Creates a convenience wrapper script to enter the professional Ubuntu environment
     */
    fun createStaffShellWrapper(context: Context, proot: String, rootfs: String): String {
        val binDir = File(context.filesDir, "bin")
        val wrapper = File(binDir, "staff-shell")
        wrapper.writeText("""
            #!/system/bin/sh
            exec $proot -0 -r $rootfs -b /dev -b /proc -b /sys -w /root /usr/bin/env -i HOME=/root TERM=x86-64 PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin /bin/bash
        """.trimIndent())
        wrapper.setExecutable(true)
        return wrapper.absolutePath
    }
}
