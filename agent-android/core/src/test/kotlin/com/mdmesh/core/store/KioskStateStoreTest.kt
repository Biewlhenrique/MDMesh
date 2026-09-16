package com.mdmesh.core.store

import com.mdmesh.proto.KioskApplyPayload
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KioskStateStoreTest {

    @Test
    fun `save then load returns the payload`() = runBlocking {
        val store = InMemoryKioskStateStore()
        val p = KioskApplyPayload(mode = "single", pinPackage = "com.x")
        store.save(p)
        assertEquals(p, store.load())
    }

    @Test
    fun `save null clears`() = runBlocking {
        val store = InMemoryKioskStateStore(KioskApplyPayload())
        store.save(null)
        assertNull(store.load())
    }

    @Test
    fun `last payload survives a clear so kiosk can be re-entered`() = runBlocking {
        val store = InMemoryKioskStateStore()
        val p = KioskApplyPayload(pinPackage = "com.x", password = "1234")
        store.save(p)
        store.save(null)
        assertNull(store.load())
        assertEquals(p, store.loadLast())
    }

    @Test
    fun `last payload is null until one is applied`() = runBlocking {
        assertNull(InMemoryKioskStateStore().loadLast())
    }
}
