package llm.slop.liquidlsd.presets

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FxShortlistTest {

    private val filters = listOf(
        FxShortlist.Candidate("color_invert", "Color Invert", listOf("Color")),
        FxShortlist.Candidate("color_sepia", "Color Sepia", listOf("Color")),
        FxShortlist.Candidate("color_hue", "Color Hue", listOf("Color")),
        FxShortlist.Candidate("blur_gaussian", "Blur Gaussian", listOf("Blur")),
        FxShortlist.Candidate("blur_box", "Blur Box", listOf("Blur")),
        FxShortlist.Candidate("distort_ripple", "Distort Ripple", listOf("Distortion")),
        FxShortlist.Candidate("distort_pinch", "Distort Pinch", listOf("Distortion"))
    )

    @BeforeTest
    fun setUp() {
        FxShortlist.setForTest(emptyList())
    }

    @Test
    fun testFavoritesToggle() {
        assertFalse(FxShortlist.isFavorite("color_invert"))
        FxShortlist.setForTest(listOf("color_invert"))
        assertTrue(FxShortlist.isFavorite("color_invert"))
        assertEquals(listOf("color_invert"), FxShortlist.favorites())
    }

    @Test
    fun testNextWithFavoritesStepsThroughFavoritesInOrder() {
        val favs = listOf("distort_pinch", "color_invert", "blur_box")
        // Step forward from distort_pinch -> color_invert
        assertEquals("color_invert", FxShortlist.next("distort_pinch", 1, filters, favs))
        // Step forward from color_invert -> blur_box
        assertEquals("blur_box", FxShortlist.next("color_invert", 1, filters, favs))
        // Wraparound forward: blur_box -> distort_pinch
        assertEquals("distort_pinch", FxShortlist.next("blur_box", 1, filters, favs))

        // Step backward: distort_pinch -> blur_box
        assertEquals("blur_box", FxShortlist.next("distort_pinch", -1, filters, favs))
        // Step backward: blur_box -> color_invert
        assertEquals("color_invert", FxShortlist.next("blur_box", -1, filters, favs))
    }

    @Test
    fun testNextWithoutFavoritesStepsThroughSameCategoryAlphabetically() {
        // Color category filters:
        // "color_hue" ("Color Hue")
        // "color_invert" ("Color Invert")
        // "color_sepia" ("Color Sepia")
        val noFavs = emptyList<String>()

        assertEquals("color_invert", FxShortlist.next("color_hue", 1, filters, noFavs))
        assertEquals("color_sepia", FxShortlist.next("color_invert", 1, filters, noFavs))
        // Wraparound forward:
        assertEquals("color_hue", FxShortlist.next("color_sepia", 1, filters, noFavs))

        // Step backward:
        assertEquals("color_sepia", FxShortlist.next("color_hue", -1, filters, noFavs))
    }

    @Test
    fun testNextFromEmptySlotSelectsFirstFavoriteOrFirstAvailable() {
        val favs = listOf("blur_box", "color_invert")
        // Empty slot with favorites -> first favorite
        assertEquals("blur_box", FxShortlist.next(null, 1, filters, favs))
        assertEquals("color_invert", FxShortlist.next(null, -1, filters, favs))

        // Empty slot without favorites -> first available alphabetically
        assertEquals("blur_box", FxShortlist.next(null, 1, filters, emptyList()))
    }

    @Test
    fun testNextWithEmptyCandidatesReturnsNull() {
        assertNull(FxShortlist.next("color_invert", 1, emptyList(), emptyList()))
    }
}
