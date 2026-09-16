package com.mdmesh.core.command.handlers

import android.os.Build
import android.provider.Settings
import com.mdmesh.core.command.CommandHandler
import com.mdmesh.core.command.CommandResults
import com.mdmesh.policy.wifi.DpmHandle
import com.mdmesh.proto.CommandEnvelope
import com.mdmesh.proto.CommandResult
import com.mdmesh.proto.DeviceAction
import com.mdmesh.proto.ProtocolJson
import kotlinx.serialization.Serializable

/**
 * `device.brightness` — screen brightness. Payload: `{ "auto": true }` to hand the screen back to
 * the light sensor, or `{ "auto": false, "value": 0..255 }` to pin it.
 *
 * A value command rather than a `policy.apply` toggle, which only carries a boolean — the same
 * reason `device.powerMode` is shaped this way.
 *
 * Writing these needs [android.app.admin.DevicePolicyManager.setSystemSetting], which a Device
 * Owner may only call from API 28. Below that the command reports `unsupported` instead of
 * failing, so the server can tell "this device can't" from "this device broke".
 */
class DeviceBrightnessHandler(
    private val handle: DpmHandle,
) : CommandHandler {

    override val type: String = DeviceAction.BRIGHTNESS

    @Serializable
    private data class Payload(val auto: Boolean? = null, val value: Int? = null)

    override suspend fun handle(command: CommandEnvelope): CommandResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return CommandResults.unsupported(command, "brightness needs API 28+")
        }
        val p = command.payload?.let {
            runCatching { ProtocolJson.json.decodeFromJsonElement(Payload.serializer(), it) }
                .getOrElse { e -> return CommandResults.failed(command, "bad payload: ${e.message}") }
        } ?: return CommandResults.failed(command, "device.brightness requires { auto } or { value }")

        return runCatching {
            p.auto?.let {
                handle.dpm.setSystemSetting(
                    handle.admin,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    if (it) AUTOMATIC else MANUAL,
                )
            }
            // A pinned level is meaningless while the sensor owns the screen, so it is only written
            // when auto is off (or left unspecified).
            val level = p.value
            if (level != null && p.auto != true) {
                handle.dpm.setSystemSetting(
                    handle.admin,
                    Settings.System.SCREEN_BRIGHTNESS,
                    level.coerceIn(0, MAX_LEVEL).toString(),
                )
            }
            CommandResults.done(command, "brightness auto=${p.auto} value=${p.value}")
        }.getOrElse { CommandResults.failed(command, it.message ?: "brightness failed") }
    }

    private companion object {
        const val AUTOMATIC = "1" // SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        const val MANUAL = "0"    // SCREEN_BRIGHTNESS_MODE_MANUAL
        const val MAX_LEVEL = 255
    }
}
