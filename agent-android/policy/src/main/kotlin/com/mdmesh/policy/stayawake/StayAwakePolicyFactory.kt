package com.mdmesh.policy.stayawake

import android.content.Context
import com.mdmesh.policy.wifi.DpmHandle

/**
 * Selects the [StayAwakePolicy] strategy for the current device. Returns `null` when none is
 * supported (not Device Owner), in which case the `stayAwake` capability is never advertised and
 * the server's gate blocks the command instead of it failing on the device.
 */
object StayAwakePolicyFactory {

    fun create(handle: DpmHandle, context: Context): StayAwakePolicy? =
        listOf(StayAwakeGlobalSettingPolicy(handle, context)).firstOrNull { it.isSupported() }
}
