package com.mdmesh.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mdmesh.core.command.handlers.KioskEnterHandler
import com.mdmesh.core.store.KioskAutoReentryStore
import com.mdmesh.core.store.KioskStateStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Puts the device back into kiosk a while after someone left it, re-applying the last payload —
 * same allowlist, same exit password.
 *
 * Scheduled by the on-device exit only. A `kiosk.exit` pushed from the console is an admin
 * deciding this device should be out of kiosk, and undoing that behind their back would be a
 * device fighting its operator.
 */
@HiltWorker
class KioskReentryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val autoReentry: KioskAutoReentryStore,
    private val kioskState: KioskStateStore,
    private val kioskEnter: KioskEnterHandler,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!autoReentry.enabled()) return Result.success()
        // Someone already went back in — nothing to restore, and re-applying would bounce the UI.
        if (kioskState.load() != null) return Result.success()
        val payload = kioskState.loadLast() ?: return Result.success()
        kioskEnter.applyPayload(payload)
        return Result.success()
    }

    companion object {
        private const val NAME = "mdm-kiosk-reentry"

        /** How long a technician gets before the device locks itself back down. */
        const val DELAY_MINUTES = 10L

        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<KioskReentryWorker>()
                .setInitialDelay(DELAY_MINUTES, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
