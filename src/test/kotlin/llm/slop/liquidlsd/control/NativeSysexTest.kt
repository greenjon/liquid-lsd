package llm.slop.liquidlsd.control

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NativeSysexTest {
    private val sysex = NativeSysex()
    private fun hex(b: ByteArray) = b.joinToString(" ") { "%02X".format(it) }

    @Test fun enterAndLeave() {
        assertEquals("F0 00 01 79 05 00 01 F7", hex(sysex.enter()))
        assertEquals("F0 00 01 79 05 00 00 F7", hex(sysex.leave()))
    }

    @Test fun indicatorBarWithDetent() {
        assertEquals("F0 00 01 79 05 01 00 00 01 01 7F F7", hex(sysex.indicator(0, IndicatorStyle(IndicatorType.BAR, true, 127))))
    }

    @Test fun indicatorDotNoDetent() {
        assertEquals("F0 00 01 79 05 01 00 0F 00 00 7F F7", hex(sysex.indicator(15, IndicatorStyle(IndicatorType.DOT))))
    }

    @Test fun colorClampsToSevenBits() {
        assertEquals("F0 00 01 79 05 01 01 02 7F 00 40 F7", hex(sysex.color(2, 500, -3, 64)))
    }

    @Test fun queueKeepsSysexInOrderAndBeforeCc() {
        val q = CcQueue()
        q.offer(0, 1, 10)
        q.offerSysex(byteArrayOf(1)); q.offerSysex(byteArrayOf(2)); q.offerSysex(byteArrayOf(3))
        assertEquals(listOf<Byte>(1, 2, 3), List(3) { q.pollSysex()!![0] })
        assertNull(q.pollSysex())
        assertEquals(Cc(0, 1, 10), q.poll())
    }
}
