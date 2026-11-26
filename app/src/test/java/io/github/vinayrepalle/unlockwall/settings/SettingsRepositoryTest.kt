package io.github.vinayrepalle.unlockwall.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.vinayrepalle.unlockwall.core.Slot
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    /** Opens the file, runs [block], then closes it so that it can be opened again. */
    private fun <T> withRepository(file: File, block: suspend (SettingsRepository) -> T): T =
        runBlocking {
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            try {
                block(SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file }))
            } finally {
                scope.coroutineContext.job.cancelAndJoin()
            }
        }

    private fun newFile() = File(folder.root, "settings.preferences_pb")

    @Test
    fun startsOffAndUnknown() {
        withRepository(newFile()) { assertEquals(Settings(), it.current()) }
    }

    @Test
    fun valuesSurviveReopening() {
        val file = newFile()
        withRepository(file) {
            it.setEnabled(true)
            it.setShowing(Slot.QUICK)
        }
        withRepository(file) { assertEquals(Settings(true, Slot.QUICK), it.current()) }
    }

    @Test
    fun showingCanBeForgotten() {
        withRepository(newFile()) {
            it.setShowing(Slot.SLOW)
            it.setShowing(null)
            assertEquals(null, it.current().showing)
        }
    }
}
