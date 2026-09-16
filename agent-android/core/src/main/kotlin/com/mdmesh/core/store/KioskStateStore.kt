package com.mdmesh.core.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mdmesh.proto.KioskApplyPayload
import com.mdmesh.proto.ProtocolJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.kioskDataStore: DataStore<Preferences> by preferencesDataStore(name = "mdm_kiosk")

/**
 * Persists the last-applied [KioskApplyPayload] so the agent can re-enter kiosk on boot
 * (the agent never receives the Headwind config — only commands — so there is nothing else
 * to re-read). [save]`(null)` clears it on `kiosk.exit`.
 */
interface KioskStateStore {
    suspend fun save(payload: KioskApplyPayload?)
    suspend fun load(): KioskApplyPayload?

    /**
     * The most recent payload ever applied, kept across `kiosk.exit` (which clears [load]). Lets the
     * device re-enter kiosk — with the same allowlist and exit password — without waiting for the
     * server to queue another `kiosk.enter`. Null only before the first one ever arrives.
     */
    suspend fun loadLast(): KioskApplyPayload?

    /**
     * Cold stream of the current kiosk payload (or `null` when not in kiosk), emitting on every
     * change. The launcher collects this so a background `kiosk.enter`/`kiosk.exit` command — which
     * runs in the check-in service, not the foreground activity — is reflected on screen
     * immediately (enter shows the grid/pinned app; exit drops to idle and unpins).
     */
    fun flow(): Flow<KioskApplyPayload?>
}

class DataStoreKioskStateStore(private val context: Context) : KioskStateStore {

    override suspend fun save(payload: KioskApplyPayload?) {
        context.kioskDataStore.edit {
            if (payload == null) {
                it.remove(KEY) // KEY_LAST deliberately survives, so re-entry has something to apply
            } else {
                val encoded = ProtocolJson.json.encodeToString(KioskApplyPayload.serializer(), payload)
                it[KEY] = encoded
                it[KEY_LAST] = encoded
            }
        }
    }

    override suspend fun load(): KioskApplyPayload? {
        val raw = context.kioskDataStore.data.map { it[KEY] }.first() ?: return null
        return decode(raw)
    }

    override suspend fun loadLast(): KioskApplyPayload? {
        val raw = context.kioskDataStore.data.map { it[KEY_LAST] }.first() ?: return null
        return decode(raw)
    }

    override fun flow(): Flow<KioskApplyPayload?> =
        context.kioskDataStore.data.map { prefs -> prefs[KEY]?.let(::decode) }

    private fun decode(raw: String): KioskApplyPayload? = runCatching {
        ProtocolJson.json.decodeFromString(KioskApplyPayload.serializer(), raw)
    }.getOrNull()

    private companion object {
        val KEY = stringPreferencesKey("kiosk_payload")
        val KEY_LAST = stringPreferencesKey("kiosk_payload_last")
    }
}

/** In-memory [KioskStateStore] for unit tests. */
class InMemoryKioskStateStore(initial: KioskApplyPayload? = null) : KioskStateStore {
    private val state = MutableStateFlow(initial)
    private var last: KioskApplyPayload? = initial

    override suspend fun save(payload: KioskApplyPayload?) {
        state.value = payload
        if (payload != null) last = payload
    }

    override suspend fun load(): KioskApplyPayload? = state.value

    override suspend fun loadLast(): KioskApplyPayload? = last

    override fun flow(): Flow<KioskApplyPayload?> = state
}
