package com.mdmesh.policy.statusbar

import com.mdmesh.policy.PolicyOutcome
import com.mdmesh.policy.TogglePolicy

/**
 * Whether the system status bar can be pulled down.
 *
 * `setEnabled(true)` gives the status bar back; `setEnabled(false)` locks it, which also takes
 * quick settings and notifications out of reach — the point on a kiosked machine terminal.
 */
interface StatusBarPolicy : TogglePolicy {

    override fun setEnabled(enabled: Boolean): PolicyOutcome

    companion object {
        const val CAPABILITY_KEY = "statusBar"
    }
}
