package llm.slop.liquidlsd.ui.browser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MultiSelectionModelTest {

    @Test
    fun testNormalClickSelectsSingleAndSetsLeadAndAnchor() {
        val model = MultiSelectionModel<String>()
        val list = listOf("A", "B", "C", "D")

        model.handleClick("B", list, isCtrl = false, isShift = false)

        assertEquals(1, model.count)
        assertTrue(model.isSelected("B"))
        assertFalse(model.isSelected("A"))
        assertEquals("B", model.leadItem)
        assertEquals("B", model.anchorItem)

        // Clicking another item replaces selection
        model.handleClick("D", list, isCtrl = false, isShift = false)
        assertEquals(1, model.count)
        assertFalse(model.isSelected("B"))
        assertTrue(model.isSelected("D"))
        assertEquals("D", model.leadItem)
        assertEquals("D", model.anchorItem)
    }

    @Test
    fun testCtrlClickTogglesItem() {
        val model = MultiSelectionModel<String>()
        val list = listOf("A", "B", "C", "D")

        model.handleClick("A", list, isCtrl = false, isShift = false)
        model.handleClick("C", list, isCtrl = true, isShift = false)

        assertEquals(2, model.count)
        assertTrue(model.isSelected("A"))
        assertTrue(model.isSelected("C"))
        assertEquals("C", model.leadItem)
        assertEquals("C", model.anchorItem)

        // Toggle off "A"
        model.handleClick("A", list, isCtrl = true, isShift = false)
        assertEquals(1, model.count)
        assertFalse(model.isSelected("A"))
        assertTrue(model.isSelected("C"))
        assertEquals("C", model.leadItem)

        // Toggle off "C" (last item)
        model.handleClick("C", list, isCtrl = true, isShift = false)
        assertEquals(0, model.count)
        assertTrue(model.isEmpty)
        assertNull(model.leadItem)
    }

    @Test
    fun testShiftClickRangeSelection() {
        val model = MultiSelectionModel<String>()
        val list = listOf("A", "B", "C", "D", "E")

        // First click A (sets anchor to A)
        model.handleClick("B", list, isCtrl = false, isShift = false)
        assertEquals("B", model.anchorItem)

        // Shift click D -> selects B, C, D
        model.handleClick("D", list, isCtrl = false, isShift = true)
        assertEquals(3, model.count)
        assertFalse(model.isSelected("A"))
        assertTrue(model.isSelected("B"))
        assertTrue(model.isSelected("C"))
        assertTrue(model.isSelected("D"))
        assertFalse(model.isSelected("E"))
        assertEquals("D", model.leadItem)
        assertEquals("B", model.anchorItem) // Anchor remains B

        // Shift click backwards: shift click A -> selects A, B
        model.handleClick("A", list, isCtrl = false, isShift = true)
        assertEquals(2, model.count)
        assertTrue(model.isSelected("A"))
        assertTrue(model.isSelected("B"))
        assertFalse(model.isSelected("C"))
        assertFalse(model.isSelected("D"))
        assertEquals("A", model.leadItem)
        assertEquals("B", model.anchorItem)
    }

    @Test
    fun testCtrlShiftClickExtendsSelection() {
        val model = MultiSelectionModel<String>()
        val list = listOf("A", "B", "C", "D", "E")

        model.handleClick("A", list, isCtrl = false, isShift = false)
        model.handleClick("C", list, isCtrl = true, isShift = false) // Anchor is now C

        // Ctrl+Shift click E -> adds D, E to existing selection (A, C, D, E)
        model.handleClick("E", list, isCtrl = true, isShift = true)
        assertEquals(4, model.count)
        assertTrue(model.isSelected("A"))
        assertFalse(model.isSelected("B"))
        assertTrue(model.isSelected("C"))
        assertTrue(model.isSelected("D"))
        assertTrue(model.isSelected("E"))
    }

    @Test
    fun testSelectAllAndClear() {
        val model = MultiSelectionModel<String>()
        val list = listOf("A", "B", "C")

        model.selectAll(list)
        assertEquals(3, model.count)
        assertTrue(model.isSelected("A"))
        assertTrue(model.isSelected("B"))
        assertTrue(model.isSelected("C"))
        assertEquals("A", model.anchorItem)
        assertEquals("C", model.leadItem)

        model.clear()
        assertEquals(0, model.count)
        assertNull(model.leadItem)
        assertNull(model.anchorItem)
    }

    @Test
    fun testGetSelectedInOrder() {
        val model = MultiSelectionModel<String>()
        val list = listOf("A", "B", "C", "D", "E")

        // Select in arbitrary order: D then B
        model.handleClick("D", list, isCtrl = false, isShift = false)
        model.handleClick("B", list, isCtrl = true, isShift = false)

        val ordered = model.getSelectedInOrder(list)
        assertEquals(listOf("B", "D"), ordered)
    }
}
