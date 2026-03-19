package social.bony.settings

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

const val ORBOT_PACKAGE = "org.torproject.android"
private const val SOCKS_HOST = "127.0.0.1"
private val SOCKS_PORTS = listOf(9050, 9150) // 9050 = default Tor, 9150 = some Orbot configs
private const val CONNECT_TIMEOUT_MS = 2_000

/**
 * NOTE: Fill in the correct Zapstore deep-link URL for Orbot before shipping.
 * Expected format: something like "https://zapstore.dev/app/org.torproject.android"
 * — verify against the live Zapstore app/website.
 */
const val ORBOT_ZAPSTORE_URL = "TODO_ZAPSTORE_URL_FOR_ORBOT"

enum class OrbotStatus {
    /** Orbot is installed and the SOCKS proxy is reachable — Tor is usable. */
    INSTALLED_AND_CONNECTED,
    /** Orbot is installed but not running — user needs to start it. */
    INSTALLED_NOT_RUNNING,
    /** Orbot is not installed — offer the Zapstore install link. */
    NOT_INSTALLED,
}

object OrbotHelper {
    fun isInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(ORBOT_PACKAGE, 0)
        true
    }.getOrDefault(false)

    /**
     * Probes common Orbot SOCKS ports (9050, 9150).
     * Returns true if any port is reachable.
     *
     * Note: Orbot in VPN mode may not expose a SOCKS port at all — in that case
     * this returns false even when Orbot is running. The toggle is still enabled
     * whenever Orbot is installed; this check only affects the status hint text.
     */
    suspend fun isSocksProxyReachable(): Boolean = withContext(Dispatchers.IO) {
        SOCKS_PORTS.any { port ->
            runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(SOCKS_HOST, port), CONNECT_TIMEOUT_MS)
                    true
                }
            }.getOrDefault(false)
        }
    }

    suspend fun getStatus(context: Context): OrbotStatus = when {
        !isInstalled(context)     -> OrbotStatus.NOT_INSTALLED
        !isSocksProxyReachable()  -> OrbotStatus.INSTALLED_NOT_RUNNING
        else                      -> OrbotStatus.INSTALLED_AND_CONNECTED
    }
}
