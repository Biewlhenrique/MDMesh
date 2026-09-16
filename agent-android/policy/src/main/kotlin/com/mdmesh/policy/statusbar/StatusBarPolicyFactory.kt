package com.mdmesh.policy.statusbar

import com.mdmesh.policy.wifi.DpmHandle

/**
 * Selects the [StatusBarPolicy] strategy for the current device, or `null` when none applies
 * (pre-M, or not Device Owner) — in which case `statusBar` is never advertised.
 */
object StatusBarPolicyFactory {

    fun create(handle: DpmHandle): StatusBarPolicy? =
        listOf(StatusBarDisablePolicy(handle)).firstOrNull { it.isSupported() }
}
