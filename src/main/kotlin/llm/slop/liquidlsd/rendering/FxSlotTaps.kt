package llm.slop.liquidlsd.rendering

import org.lwjgl.opengl.GL33.*

/**
 * Small copies of each FX slot's output, for the tour's pipeline diagram (one thumbnail per slot).
 * Off unless [enabled]; then [Renderer.renderFxChainPass] blits every rendered slot here. A slot that was not rendered
 * this frame (empty, bypassed, or the whole chain off) has no tap and shows its input instead: see [rendered].
 */
object FxSlotTaps {
    const val W = 384
    const val H = 216

    @Volatile var enabled = false

    private class Taps {
        val fbos = arrayOfNulls<FBO>(FxChain.SLOT_COUNT)
        val rendered = BooleanArray(FxChain.SLOT_COUNT)
    }

    private val taps = java.util.IdentityHashMap<FxChain, Taps>()

    /** Copies [src]'s colour buffer (slot [slot]'s result, [w] x [h]) into the chain's tap. GL thread only. */
    fun capture(chain: FxChain, slot: Int, src: FBO, w: Int, h: Int) {
        if (!enabled) return
        val t = taps.getOrPut(chain) { Taps() }
        val dst = t.fbos[slot] ?: FBO(W, H).also { t.fbos[slot] = it }
        glBindFramebuffer(GL_READ_FRAMEBUFFER, src.framebufferId)
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, dst.framebufferId)
        glBlitFramebuffer(0, 0, w, h, 0, 0, W, H, GL_COLOR_BUFFER_BIT, GL_LINEAR)
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
        t.rendered[slot] = true
    }

    /** The texture of slot [slot]'s output this frame, or 0 when the slot passed its input through. */
    fun rendered(chain: FxChain, slot: Int): Int {
        val t = taps[chain] ?: return 0
        return if (t.rendered[slot]) t.fbos[slot]?.texture ?: 0 else 0
    }

    /** Clears this frame's marks; call once the frame's taps have been drawn. */
    fun endFrame() {
        for (t in taps.values) t.rendered.fill(false)
    }

    fun dispose() {
        enabled = false
        for (t in taps.values) for (f in t.fbos) f?.dispose()
        taps.clear()
    }
}
