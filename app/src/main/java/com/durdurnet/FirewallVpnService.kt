package com.durdurnet

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Routes only the selected packages into a local VPN and discards their packets.
 * Other apps are not added to this VPN and should keep their normal network path.
 */
class FirewallVpnService : VpnService() {
    companion object {
        const val EXTRA_BLOCKED_PACKAGES = "blocked_packages"
        const val ACTION_STOP = "com.durdurnet.STOP"
    }

    @Volatile private var tunnel: ParcelFileDescriptor? = null
    @Volatile private var readerTask: Future<*>? = null
    private val executor = Executors.newSingleThreadExecutor()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTunnel()
            stopSelf()
            return START_NOT_STICKY
        }

        val packages = intent?.getStringArrayListExtra(EXTRA_BLOCKED_PACKAGES)?.distinct().orEmpty()
        stopTunnel()
        if (packages.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            val builder = Builder()
                .setSession("DurdurNet")
                .setMtu(1500)
                .setBlocking(true)
                .addAddress("10.10.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addAddress("fd10:10:10::2", 128)
                .addRoute("::", 0)

            var allowedCount = 0
            for (packageName in packages) {
                try {
                    builder.addAllowedApplication(packageName)
                    allowedCount++
                } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                    // Ignore apps uninstalled after selection.
                }
            }
            if (allowedCount == 0) {
                stopSelf()
                return START_NOT_STICKY
            }

            val established = builder.establish()
            if (established == null) {
                stopSelf()
                return START_NOT_STICKY
            }
            tunnel = established
            val fd = established.fileDescriptor
            readerTask = executor.submit {
                val buffer = ByteArray(32767)
                try {
                    FileInputStream(fd).use { input ->
                        while (!Thread.currentThread().isInterrupted) {
                            // Intentionally discard every packet: selected apps have no network path.
                            if (input.read(buffer) < 0) break
                        }
                    }
                } catch (_: Exception) {
                    // Closing the VPN descriptor ends this reader.
                }
            }
        } catch (_: Exception) {
            stopTunnel()
            stopSelf()
            return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    private fun stopTunnel() {
        readerTask?.cancel(true)
        readerTask = null
        try { tunnel?.close() } catch (_: Exception) {}
        tunnel = null
    }

    override fun onDestroy() {
        stopTunnel()
        executor.shutdownNow()
        super.onDestroy()
    }
}
