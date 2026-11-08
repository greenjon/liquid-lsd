package llm.slop.liquidlsd.utils

import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Messages for the user from code that can't reach the UI (loaders, MIDI, startup). The UI shows them one at a time as
 * toasts ([llm.slop.liquidlsd.ui.ToastOverlay]); posting works before the window exists.
 */
object UserNotice {
    private val pending = ConcurrentLinkedQueue<String>()

    fun post(text: String) { pending.offer(text) }

    /** The next waiting message, or null. */
    fun poll(): String? = pending.poll()
}
