package com.mdmesh.policy.stayawake

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import com.mdmesh.policy.PolicyOutcome
import com.mdmesh.policy.wifi.DpmHandle

/**
 * Keeps the screen awake on a charger, by two levers at once:
 *
 *  - [Settings.Global.STAY_ON_WHILE_PLUGGED_IN] — the exact semantic ("stay on while plugged in"),
 *    but OEM builds are free to ignore it, and some do: the write succeeds and the screen still
 *    sleeps. Kept because where it is honoured it is the right switch, scoped to charging only.
 *  - [Settings.System.SCREEN_OFF_TIMEOUT] — one of the three settings a Device Owner may write
 *    through `setSystemSetting` (API 28+), and the one that actually holds across devices.
 *
 * Disabling restores the timeout the device had before, remembered at enable time, rather than
 * guessing a default — the admin's own screen-timeout choice survives a round trip through here.
 *
 * The value of the global is a mask of charger types, not a boolean: enabling sets AC, USB and
 * wireless at once.
 */
internal class StayAwakeGlobalSettingPolicy(
    private val handle: DpmHandle,
    private val context: Context,
) : StayAwakePolicy {

    private val prefs = context.getSharedPreferences("mdm_stay_awake", Context.MODE_PRIVATE)

    override val capabilityKey: String = StayAwakePolicy.CAPABILITY_KEY

    override fun isSupported(): Boolean = handle.dpm.isDeviceOwnerApp(handle.admin.packageName)

    @Suppress("DEPRECATION")
    override fun setEnabled(enabled: Boolean): PolicyOutcome = runCatching {
        handle.dpm.setGlobalSetting(
            handle.admin,
            Settings.Global.STAY_ON_WHILE_PLUGGED_IN,
            (if (enabled) ALL_CHARGERS else 0).toString(),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            applyScreenTimeout(enabled)
        }
        PolicyOutcome.Applied
    }.getOrElse { PolicyOutcome.Failed(it.message ?: "stayAwake setEnabled failed") }

    private fun applyScreenTimeout(enabled: Boolean) {
        if (enabled) {
            rememberCurrentTimeout()
            setTimeout(NEVER_MS)
        } else {
            setTimeout(prefs.getInt(KEY_PREVIOUS, DEFAULT_MS))
            prefs.edit().remove(KEY_PREVIOUS).apply()
        }
    }

    /** Only the first enable records it, so enabling twice can't overwrite it with NEVER_MS. */
    private fun rememberCurrentTimeout() {
        if (prefs.contains(KEY_PREVIOUS)) return
        val current = Settings.System.getInt(
            context.contentResolver,
            Settings.System.SCREEN_OFF_TIMEOUT,
            DEFAULT_MS,
        )
        if (current in 1 until NEVER_MS) {
            prefs.edit().putInt(KEY_PREVIOUS, current).apply()
        }
    }

    private fun setTimeout(millis: Int) {
        handle.dpm.setSystemSetting(
            handle.admin,
            Settings.System.SCREEN_OFF_TIMEOUT,
            millis.toString(),
        )
    }

    private companion object {
        const val ALL_CHARGERS = BatteryManager.BATTERY_PLUGGED_AC or
            BatteryManager.BATTERY_PLUGGED_USB or
            BatteryManager.BATTERY_PLUGGED_WIRELESS
        const val NEVER_MS = Int.MAX_VALUE
        const val DEFAULT_MS = 60_000
        const val KEY_PREVIOUS = "previous_screen_off_timeout"
    }
}
