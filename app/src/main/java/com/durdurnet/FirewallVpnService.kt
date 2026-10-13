package com.durdurnet

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Keeps the local VPN in the foreground while blocking selected apps.
 * Packets routed into this VPN are intentionally discarded.
 */
class FirewallVpnService : VpnService() {
    companion object {
        const val EXTRA_BLOCKED_PACKAGES = "blocked_packages"
        const val ACTION_STOP = "com.durdurnet.STOP"
        private const val CHANNEL_ID = "durdurnet_vpn"
        private const val NOTIFICATION_ID = 41
    }

    @Volatile private var tunnel: ParcelFileDescriptor? = null
    @Volatile private var readerTask: Future<*>? = null
    private val executor = Executors.newSingleThreadExecutor()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A foreground notification helps Android keep the VPN service alive.
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        if (intent?.action == ACTION_STOP) {
            stopTunnel()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val packages = intent?.getStringArrayListExtra(EXTRA_BLOCKED_PACKAGES)?.distinct().orEmpty()
        stopTunnel()
        if (packages.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
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
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            val established = builder.establish()
            if (established == null) {
                stopForeground(STOP_FOREGROUND_REMOVE)
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
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "DurdurNet VPN",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "DurdurNet VPN çalışırken gösterilir"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("DurdurNet çalışıyor")
            .setContentText("Seçili uygulamalar için VPN engellemesi etkin.")
            .setOngoing(true)
            .build()
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