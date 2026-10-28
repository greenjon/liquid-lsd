package llm.slop.liquidlsd.ui

import io.mockk.mockk
import llm.slop.liquidlsd.SessionContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AutoPreviewTest {
    @Test
    fun theDwellFiresOnceTheKeyHasRestedAndNotAgainForTheSameKey() {
        val d = LibraryNavigation.Dwell(400)
        assertNull(d.update("a", 0))
        assertNull(d.update("a", 399))
        assertEquals("a", d.update("a", 400))
        assertNull(d.update("a", 2000), "already previewed")
        assertNull(d.update("b", 2100), "just arrived")
        assertNull(d.update("c", 2300), "scrolled on before resting")
        assertEquals("c", d.update("c", 2700))
        assertNull(d.update(null, 5000))
        assertNull(d.update("a", 5000))
        assertEquals("a", d.update("a", 5400), "a again after moving away")
    }

    @Test
    fun autoPreviewTurnsOffWhenTheLibraryViewCloses() {
        if (LibraryNavigation.autoPreview) LibraryNavigation.toggleAutoPreview()
        LibraryNavigation.toggleAutoPreview()
        assertTrue(LibraryNavigation.autoPreview)
        LibraryNavigation.tickAutoPreview(mockk<SessionContext>(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), inLibraryView = false, nowMs = 0)
        assertFalse(LibraryNavigation.autoPreview)
    }
}
