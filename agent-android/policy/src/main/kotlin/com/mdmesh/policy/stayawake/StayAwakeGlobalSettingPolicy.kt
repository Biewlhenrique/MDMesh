package com.mdmesh.policy.stayawake

import android.os.BatteryManager
import android.provider.Settings
import com.mdmesh.policy.PolicyOutcome
import com.mdmesh.policy.wifi.DpmHandle

/**
 * Writes [Settings.Global.STAY_ON_WHILE_PLUGGED_IN] as Device Owner.
 *
 * `setGlobalSetting` is deprecated for most keys, but this is one of the few a Device Owner may
 * still write — there is no non-deprecated replacement for it, so the deprecation is suppressed
 * rather than worked around.
 *
 * The value is a mask of charger types, not a boolean: enabling means "on any charger we can be
 * plugged into", so all three bits are set at once. Zero restores the normal screen timeout.
 */
internal class StayAwakeGlobalSettingPolicy(
    private val handle: DpmHandle,
) : StayAwakePolicy {

    override val capabilityKey: String = StayAwakePolicy.CAPABILITY_KEY

    override fun isSupported(): Boolean = handle.dpm.isDeviceOwnerApp(handle.admin.packageName)

    @Suppress("DEPRECATION")
    override fun setEnabled(enabled: Boolean): PolicyOutcome = runCatching {
        val value = if (enabled) ALL_CHARGERS else 0
        handle.dpm.setGlobalSetting(
            handle.admin,
            Settings.Global.STAY_ON_WHILE_PLUGGED_IN,
            value.toString(),
        )
        PolicyOutcome.Applied
    }.getOrElse { PolicyOutcome.Failed(it.message ?: "stayAwake setEnabled failed") }

    private companion object {
        const val ALL_CHARGERS = BatteryManager.BATTERY_PLUGGED_AC or
            BatteryManager.BATTERY_PLUGGED_USB or
            BatteryManager.BATTERY_PLUGGED_WIRELESS
    }
}
