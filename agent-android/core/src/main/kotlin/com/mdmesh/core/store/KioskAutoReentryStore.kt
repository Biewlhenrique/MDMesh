package com.mdmesh.core.store

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether this device returns to kiosk on its own a while after someone exits it.
 *
 * A per-device switch, not a fleet policy: it exists so a technician standing at the machine can
 * keep the device out of kiosk for as long as the job takes. Defaults to on — a kiosk device left
 * unlocked because someone forgot to press anything is the failure this guards against.
 *
 * SharedPreferences-backed so the launcher's exit path can read it without a coroutine.
 */
@Singleton
class KioskAutoReentryStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("mdm_kiosk_prefs", Context.MODE_PRIVATE)

    fun enabled(): Boolean = prefs.getBoolean(KEY, true)

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY, value).apply()
    }

    private companion object { const val KEY = "auto_reentry" }
}
