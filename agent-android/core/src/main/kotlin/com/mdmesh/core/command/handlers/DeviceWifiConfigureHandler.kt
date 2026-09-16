package com.mdmesh.core.command.handlers

import android.content.Context
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import com.mdmesh.core.command.CommandHandler
import com.mdmesh.core.command.CommandResults
import com.mdmesh.proto.CommandEnvelope
import com.mdmesh.proto.CommandResult
import com.mdmesh.proto.DeviceAction
import com.mdmesh.proto.ProtocolJson
import kotlinx.serialization.Serializable

/**
 * `device.wifiConfigure` — saves a Wi-Fi network on the device. Payload:
 * `{ "ssid": "...", "password": "...", "security": "WPA" | "WEP" | "NONE" }`.
 *
 * The point is staging: a tablet set up on one network can be handed the network it will meet at
 * its destination, and joins on arrival without anyone touching a locked-down device.
 *
 * Uses the [WifiConfiguration] APIs, deprecated since API 29 for ordinary apps but explicitly still
 * honoured for a Device Owner — and there is no replacement that saves a network without a user
 * prompt, which is exactly what a kiosked device can't show.
 */
@Suppress("DEPRECATION")
class DeviceWifiConfigureHandler(
    private val context: Context,
) : CommandHandler {

    override val type: String = DeviceAction.WIFI_CONFIGURE

    @Serializable
    private data class Payload(
        val ssid: String,
        val password: String? = null,
        val security: String? = null,
    )

    override suspend fun handle(command: CommandEnvelope): CommandResult {
        val p = command.payload?.let {
            runCatching { ProtocolJson.json.decodeFromJsonElement(Payload.serializer(), it) }
                .getOrElse { e -> return CommandResults.failed(command, "bad payload: ${e.message}") }
        } ?: return CommandResults.failed(command, "device.wifiConfigure requires { ssid }")

        if (p.ssid.isBlank()) {
            return CommandResults.failed(command, "ssid is required")
        }
        val security = (p.security ?: if (p.password.isNullOrEmpty()) NONE else WPA).uppercase()
        if (security == EAP) {
            // Enterprise needs certificates and an identity this payload doesn't carry; saying so
            // beats saving a network that can never associate.
            return CommandResults.unsupported(command, "EAP networks are not supported")
        }

        return runCatching {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val config = WifiConfiguration().apply {
                SSID = quote(p.ssid)
                when (security) {
                    NONE -> allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                    WEP -> {
                        allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                        wepKeys[0] = quote(p.password.orEmpty())
                        wepTxKeyIndex = 0
                    }
                    else -> {
                        allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                        preSharedKey = quote(p.password.orEmpty())
                    }
                }
            }
            val netId = wifi.addNetwork(config)
            if (netId == -1) {
                return CommandResults.failed(command, "the device refused the network (already saved?)")
            }
            // Not forcing a disconnect: the device may be on the network that delivered this very
            // command, and it should stay there until the new one is actually in range.
            wifi.enableNetwork(netId, false)
            wifi.saveConfiguration()
            CommandResults.done(command, "saved ${p.ssid} ($security)")
        }.getOrElse { CommandResults.failed(command, it.message ?: "wifi configure failed") }
    }

    private fun quote(s: String) = "\"" + s + "\""

    private companion object {
        const val NONE = "NONE"
        const val WEP = "WEP"
        const val WPA = "WPA"
        const val EAP = "EAP"
    }
}
