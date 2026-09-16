package com.mdmesh.policy.statusbar

import android.os.Build
import com.mdmesh.policy.PolicyOutcome
import com.mdmesh.policy.wifi.DpmHandle

/**
 * Status-bar strategy backed by [android.app.admin.DevicePolicyManager.setStatusBarDisabled]
 * (Device Owner, API 23+).
 *
 * The DPM call takes *disabled*, so the inversion happens once, here at the boundary — the same
 * shape as the camera policy.
 */
internal class StatusBarDisablePolicy(
    private val handle: DpmHandle,
) : StatusBarPolicy {

    override val capabilityKey: String = StatusBarPolicy.CAPABILITY_KEY

    override fun isSupported(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            handle.dpm.isDeviceOwnerApp(handle.admin.packageName)

    override fun setEnabled(enabled: Boolean): PolicyOutcome = runCatching {
        handle.dpm.setStatusBarDisabled(handle.admin, !enabled)
        PolicyOutcome.Applied
    }.getOrElse { PolicyOutcome.Failed(it.message ?: "statusBar setEnabled failed") }
}
