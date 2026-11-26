package io.github.vinayrepalle.unlockwall.monitor

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.vinayrepalle.unlockwall.core.Reason
import io.github.vinayrepalle.unlockwall.core.Request
import io.github.vinayrepalle.unlockwall.core.Slot
import io.github.vinayrepalle.unlockwall.settings.SettingsRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WallpaperControllerTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val settings by lazy {
        SettingsRepository(
            PreferenceDataStoreFactory.create(scope = scope) {
                File(folder.root, "s.preferences_pb")
            },
        )
    }

    private val applied = mutableListOf<Slot>()
    private val logs = mutableListOf<String>()
    private var problem: String? = null
    private var awake = 0

    private val controller by lazy {
        WallpaperController(
            settings = settings,
            available = { Slot.entries.toSet() },
            apply = { slot ->
                applied.add(slot)
                problem
            },
            log = { logs.add(it) },
            keepAwake = {
                awake++
                { awake-- }
            },
        )
    }

    @After
    fun close() = scope.cancel()

    private fun send(slot: Slot, reason: Reason = Reason.QUICK_UNLOCK) {
        controller.submit(Request(slot, reason)).get()
    }

    @Test
    fun appliesAndRemembers() = runBlocking {
        settings.setEnabled(true)
        send(Slot.QUICK)
        send(Slot.QUICK)
        assertEquals(listOf(Slot.QUICK), applied)
        assertEquals(Slot.QUICK, settings.current().showing)
        assertEquals(0, awake)
    }

    @Test
    fun doesNothingWhileSwitchedOff() = runBlocking {
        send(Slot.QUICK)
        assertEquals(emptyList<Slot>(), applied)
    }

    @Test
    fun failureLeavesTheCurrentPictureUnknownSoItIsRetried() = runBlocking {
        settings.setEnabled(true)
        problem = "blocked"
        send(Slot.NORMAL, Reason.SCREEN_OFF)
        assertNull(settings.current().showing)
        problem = null
        send(Slot.NORMAL, Reason.SCREEN_OFF)
        assertEquals(listOf(Slot.NORMAL, Slot.NORMAL), applied)
        assertEquals(Slot.NORMAL, settings.current().showing)
    }

    @Test
    fun anExceptionIsLoggedAndReleasesTheWakeLock() = runBlocking {
        settings.setEnabled(true)
        val throwing = WallpaperController(
            settings = settings,
            available = { Slot.entries.toSet() },
            apply = { error("boom") },
            log = { logs.add(it) },
            keepAwake = {
                awake++
                { awake-- }
            },
        )
        throwing.submit(Request(Slot.QUICK, Reason.QUICK_UNLOCK)).get()
        assertTrue(logs.any { it.startsWith("ERROR") })
        assertEquals(0, awake)
    }
}
