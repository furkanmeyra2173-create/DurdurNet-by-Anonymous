package com.durdurnet

import android.net.VpnService
import android.content.Intent
import android.os.ParcelFileDescriptor

/**
 * Initial VPN service scaffold.
 * Per-app blocking is NOT active in this starter: correctly filtering IP packets
 * requires a complete, tested VPN packet-forwarding/drop implementation.
 */
class FirewallVpnService : VpnService() {
    private var tunnel: ParcelFileDescriptor? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (tunnel == null) {
            tunnel = Builder()
                .setSession("DurdurNet")
                .addAddress("10.10.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1")
                .establish()
        }
        // Deliberately not forwarding or dropping packets yet; see README.
        return START_STICKY
    }
    override fun onDestroy() {
        tunnel?.close()
        tunnel = null
        super.onDestroy()
    }
}
