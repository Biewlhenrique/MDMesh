package com.mdmesh.kiosk

import android.app.admin.DevicePolicyManager

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Asserts against the real `LOCK_TASK_FEATURE_*` integer values (compile-time constants,
 * inlined by the Kotlin compiler): SYSTEM_INFO=1, NOTIFICATIONS=2, HOME=4, OVERVIEW=8,
 * GLOBAL_ACTIONS=16, KEYGUARD=32.
 */
class KioskFeaturesTest {

    @Test
    fun `all null yields global actions only`() {
        assertEquals(16, lockTaskFeatures(KioskToggles()))
    }

    @Test
    fun `enabling toggles adds their flags plus global actions`() {
        val f = lockTaskFeatures(
            KioskToggles(home = true, recents = true, notifications = true, systemInfo = true, keyguard = true),
        )
        // 4 | 8 | 2 | 1 | 32 | 16 (global actions still on, lockButtons not set)
        assertEquals(63, f)
    }

    @Test
    fun `notifications pulls in home, which the platform requires`() {
        // setLockTaskFeatures throws on NOTIFICATIONS without HOME, and a rejected mask used to
        // fail the whole kiosk.enter — leaving the device unlocked.
        val f = lockTaskFeatures(KioskToggles(notifications = true))
        assertEquals(
            DevicePolicyManager.LOCK_TASK_FEATURE_HOME or
                DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS or
                DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS,
            f,
        )
    }

    @Test
    fun `lockButtons removes the power menu`() {
        assertEquals(0, lockTaskFeatures(KioskToggles(lockButtons = true)))
        // with other toggles, global actions is still removed
        assertEquals(4, lockTaskFeatures(KioskToggles(home = true, lockButtons = true)))
    }
}
