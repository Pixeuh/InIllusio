package fr.inillusio.debug

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.inillusio.InIllusioApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeveloperToolsTest {
    @Test fun unlockAllAndRestoreReturnsTheExactOriginalCollection() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<InIllusioApplication>()
        application.catalogImport.join()
        if (DeveloperTools.hasSnapshot(application).first()) DeveloperTools.restoreCollection(application)
        val dao = application.database.catalogDao()
        val before = dao.allCollectionEntries().sortedBy { it.anecdoteId }
        val preview = DeveloperTools.allAnecdotes(application)
        assertEquals(24, preview.size)
        assertEquals(before, dao.allCollectionEntries().sortedBy { it.anecdoteId })

        val unlocked = DeveloperTools.unlockAll(application)
        assertEquals(24, unlocked)
        assertEquals(preview.map { it.id }.sorted(), dao.allCollectionEntries().map { it.anecdoteId }.sorted())
        assertEquals(24, DeveloperTools.unlockAll(application))
        assertEquals(24, dao.allCollectionEntries().map { it.anecdoteId }.distinct().size)

        assertTrue(DeveloperTools.hasSnapshot(application).first())
        assertEquals(before.size, DeveloperTools.restoreCollection(application))
        assertEquals(before, dao.allCollectionEntries().sortedBy { it.anecdoteId })
        assertFalse(DeveloperTools.hasSnapshot(application).first())
    }
}
