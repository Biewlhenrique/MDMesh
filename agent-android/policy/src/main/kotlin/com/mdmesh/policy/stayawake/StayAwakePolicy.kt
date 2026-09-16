package com.mdmesh.policy.stayawake

import com.mdmesh.policy.PolicyOutcome
import com.mdmesh.policy.TogglePolicy

/**
 * Keeps the screen on while the device is charging.
 *
 * `setEnabled(true)` holds the display awake on any charger; `setEnabled(false)` restores the
 * normal screen timeout. Backed by the global setting a Device Owner is still allowed to write
 * ([android.provider.Settings.Global.STAY_ON_WHILE_PLUGGED_IN]).
 *
 * The point of this one on a shop-floor fleet: a wall-powered tablet acting as a machine HMI
 * should be readable without anyone touching it.
 */
interface StayAwakePolicy : TogglePolicy {

    override fun setEnabled(enabled: Boolean): PolicyOutcome

    companion object {
        const val CAPABILITY_KEY = "stayAwake"
    }
}
