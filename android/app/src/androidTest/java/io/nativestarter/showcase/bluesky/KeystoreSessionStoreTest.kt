package io.nativestarter.showcase.bluesky

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.nativestarter.showcase.bluesky.core.session.KeystoreSessionStore
import io.nativestarter.showcase.bluesky.core.session.Session
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Round trip through the real Android Keystore and DataStore on the device. */
@RunWith(AndroidJUnit4::class)
class KeystoreSessionStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    // JUnit 4 needs a void test method; runBlocking would otherwise return the last expression (Boolean).
    fun saveLoadClearRoundTrip(): Unit = runBlocking {
        val file = File(context.filesDir, "datastore/test-${UUID.randomUUID()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { file })
        val store = KeystoreSessionStore(context, keyAlias = "test-session-key", dataStore = dataStore)
        val session = Session("did:plc:alice", "alice.test", "access-token-value", "refresh-token-value")

        assertNull(store.load())
        store.save(session)
        assertEquals(session, store.load())
        assertFalse("tokens must not be stored in plain text", file.readBytes().toString(Charsets.ISO_8859_1).contains("refresh-token-value"))
        store.clear()
        assertNull(store.load())
        file.delete()
    }
}
