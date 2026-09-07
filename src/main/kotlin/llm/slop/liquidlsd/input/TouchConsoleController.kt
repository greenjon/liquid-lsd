package llm.slop.liquidlsd.input

import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.rendering.Mixer
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

private val logger = KotlinLogging.logger {}

/**
 * 4-Zone Virtual Performance Console Controller.
 * Inspired by the vintage Stanton SCS.3m.
 *
 * Zones:
 * - Bottom 28% (Y in 0.00..0.28): Crossfader (Deck A <-> Deck B)
 * - Deadzone Buffer (Y in 0.28..0.45): Rejects new taps, maintains active drift
 * - Top 55% (Y in 0.45..1.00):
 *     - Left 33% (X in 0.00..0.33): Deck A Level
 *     - Center 33% (X in 0.33..0.67): Deck BG Level
 *     - Right 33% (X in 0.67..1.00): Deck B Level
 */
class TouchConsoleController(
    private val session: SessionContext? = null
) {
    enum class TouchZone {
        CROSSFADER,
        LEVEL_A,
        LEVEL_BG,
        LEVEL_B,
        DEADZONE
    }

    data class TouchContact(
        val id: Long,
        var x: Float,
        var y: Float,
        val zone: TouchZone,
        val timestamp: Long = System.currentTimeMillis()
    )

    val eventQueue = ConcurrentLinkedQueue<TouchConsoleEvent>()
    var backend: TouchStripBackend = NoOpTouchBackend()
        private set

    var isActive: Boolean = false
        private set

    // Independent LIFO contact stacks per zone
    private val crossfaderStack = ArrayDeque<TouchContact>()
    private val levelAStack = ArrayDeque<TouchContact>()
    private val levelBGStack = ArrayDeque<TouchContact>()
    private val levelBStack = ArrayDeque<TouchContact>()

    // Lookup map to ensure Zone Affinity
    private val activeTouches = ConcurrentHashMap<Long, TouchContact>()

    // Current sticky hold markers for UI display
    var holdLevelA: Float = 1.0f; private set
    var holdLevelBG: Float = 1.0f; private set
    var holdLevelB: Float = 1.0f; private set

    // Active mixer reference
    var mixer: Mixer? = null

    // Elevation state tracking
    var isElevatingPermissions: Boolean = false; private set

    fun initialize(windowHandle: Long, activeMixer: Mixer) {
        this.mixer = activeMixer
        val os = System.getProperty("os.name", "").lowercase()

        backend = when {
            os.contains("linux") -> LinuxEvdevTouchBackend(eventQueue)
            os.contains("mac") -> MacCocoaTouchBackend(windowHandle, eventQueue)
            else -> NoOpTouchBackend(TouchBackendState.DISABLED)
        }

        if (session?.uiTheme?.trackpadConsoleEnabled != false) {
            backend.start()
        }
    }

    fun setBackendForTesting(testBackend: TouchStripBackend) {
        this.backend = testBackend
    }

    /**
     * Toggles performance console activation.
     * When active, hardware cursor grabbing is engaged on Linux.
     */
    fun toggleActive(forced: Boolean? = null) {
        if (session?.uiTheme?.trackpadConsoleEnabled == false) {
            if (isActive) setConsoleActive(false)
            return
        }

        val target = forced ?: !isActive
        if (target == isActive) return

        if (target && backend.state != TouchBackendState.READY) {
            backend.start()
            if (backend.state != TouchBackendState.READY) {
                logger.warn { "Cannot engage Touch Console: backend state is ${backend.state}" }
                return
            }
        }

        setConsoleActive(target)
    }

    private fun setConsoleActive(active: Boolean) {
        isActive = active
        backend.setGrabbed(active)
        if (!active) {
            clearAllTouches()
        }
        logger.info { "Touch Console active state changed: $isActive" }
    }

    /**
     * Safety hook invoked when the main application window loses focus.
     */
    fun onFocusLost() {
        if (isActive) {
            logger.info { "Window focus lost: safely releasing trackpad hardware grab" }
            setConsoleActive(false)
        }
    }

    /**
     * Drained once per frame on the primary GLFW render thread (Thread 0).
     */
    fun processPendingEvents() {
        if (!isActive) {
            // Discard any queued events when console is not active
            while (eventQueue.poll() != null) {}
            return
        }

        var event = eventQueue.poll()
        while (event != null) {
            when (event) {
                is TouchConsoleEvent.TouchDown -> handleTouchDown(event.id, event.x, event.y)
                is TouchConsoleEvent.TouchMove -> handleTouchMove(event.id, event.x, event.y)
                is TouchConsoleEvent.TouchUp -> handleTouchUp(event.id)
                is TouchConsoleEvent.TouchReset -> clearAllTouches()
            }
            event = eventQueue.poll()
        }
    }

    private fun determineZone(x: Float, y: Float): TouchZone {
        return when {
            y <= 0.28f -> TouchZone.CROSSFADER
            y >= 0.45f -> {
                when {
                    x < 0.33f -> TouchZone.LEVEL_A
                    x <= 0.67f -> TouchZone.LEVEL_BG
                    else -> TouchZone.LEVEL_B
                }
            }
            else -> TouchZone.DEADZONE
        }
    }

    private fun handleTouchDown(id: Long, x: Float, y: Float) {
        val zone = determineZone(x, y)
        if (zone == TouchZone.DEADZONE) {
            // New touches landing in the deadzone are ignored
            return
        }

        val contact = TouchContact(id, x, y, zone)
        activeTouches[id] = contact

        val stack = getStackForZone(zone)
        stack.addLast(contact)

        applyZoneOutput(zone, contact)
    }

    private fun handleTouchMove(id: Long, x: Float, y: Float) {
        val contact = activeTouches[id] ?: return
        // Zone Affinity: touch maintains its bound zone even when drifting into deadzone
        contact.x = x
        contact.y = y

        val stack = getStackForZone(contact.zone)
        // If this contact is the top of the LIFO stack, update the target parameter continuously
        if (stack.lastOrNull()?.id == id) {
            applyZoneOutput(contact.zone, contact)
        }
    }

    private fun handleTouchUp(id: Long) {
        val contact = activeTouches.remove(id) ?: return
        val stack = getStackForZone(contact.zone)

        stack.removeIf { it.id == id }

        // Multi-touch fallback: snap back to the preceding active finger in the stack
        val nextTop = stack.lastOrNull()
        if (nextTop != null) {
            applyZoneOutput(contact.zone, nextTop)
        } else {
            // Sticky hold: fader remains at the last touched position
            captureStickyHold(contact.zone)
        }
    }

    private fun getStackForZone(zone: TouchZone): ArrayDeque<TouchContact> {
        return when (zone) {
            TouchZone.CROSSFADER -> crossfaderStack
            TouchZone.LEVEL_A -> levelAStack
            TouchZone.LEVEL_BG -> levelBGStack
            TouchZone.LEVEL_B -> levelBStack
            TouchZone.DEADZONE -> crossfaderStack
        }
    }

    private fun applyZoneOutput(zone: TouchZone, contact: TouchContact) {
        val m = mixer ?: return

        when (zone) {
            TouchZone.CROSSFADER -> {
                // Bezel clamping: X <= 0.05 -> -1.0, X >= 0.95 -> +1.0
                // Center detent: |X - 0.50| <= 0.02 -> 0.0
                val rawX = contact.x
                val mappedX = when {
                    rawX <= 0.05f -> -1.0f
                    rawX >= 0.95f -> 1.0f
                    kotlin.math.abs(rawX - 0.50f) <= 0.02f -> 0.0f
                    else -> {
                        val t = ((rawX - 0.05f) / 0.90f).coerceIn(0.0f, 1.0f)
                        -1.0f + t * 2.0f
                    }
                }
                m.onCrossfadeManualTakeover()
                m.crossfade.set(mappedX)
            }
            TouchZone.LEVEL_A -> {
                val level = mapAlphaY(contact.y)
                m.levelA = level
                holdLevelA = level
            }
            TouchZone.LEVEL_BG -> {
                val level = mapAlphaY(contact.y)
                m.levelBG = level
                holdLevelBG = level
            }
            TouchZone.LEVEL_B -> {
                val level = mapAlphaY(contact.y)
                m.levelB = level
                holdLevelB = level
            }
            TouchZone.DEADZONE -> {}
        }
    }

    /**
     * Maps physical Y coordinates to normalized Alpha (0.0..1.0)
     * with bezel clamping: Y <= 0.48 -> 0.0 (blackout), Y >= 0.94 -> 1.0 (full brightness).
     */
    private fun mapAlphaY(y: Float): Float {
        return when {
            y <= 0.48f -> 0.0f
            y >= 0.94f -> 1.0f
            else -> ((y - 0.48f) / 0.46f).coerceIn(0.0f, 1.0f)
        }
    }

    private fun captureStickyHold(zone: TouchZone) {
        val m = mixer ?: return
        when (zone) {
            TouchZone.LEVEL_A -> holdLevelA = m.levelA
            TouchZone.LEVEL_BG -> holdLevelBG = m.levelBG
            TouchZone.LEVEL_B -> holdLevelB = m.levelB
            else -> {}
        }
    }

    private fun clearAllTouches() {
        activeTouches.clear()
        crossfaderStack.clear()
        levelAStack.clear()
        levelBGStack.clear()
        levelBStack.clear()
        while (eventQueue.poll() != null) {}
    }

    // Expose active contact lists for UI HUD rendering
    fun getCrossfaderContacts(): List<TouchContact> = crossfaderStack.toList()
    fun getLevelAContacts(): List<TouchContact> = levelAStack.toList()
    fun getLevelBGContacts(): List<TouchContact> = levelBGStack.toList()
    fun getLevelBContacts(): List<TouchContact> = levelBStack.toList()

    /**
     * Triggers Polkit elevation via pkexec to install the touchpad uaccess udev rule.
     * Re-scans and hot-reloads the backend upon success.
     */
    fun requestPermissionElevation(onComplete: (Boolean) -> Unit = {}) {
        if (isElevatingPermissions) return
        isElevatingPermissions = true

        Thread({
            var success = false
            try {
                val cmd = "rm -f /etc/udev/rules.d/99-liquidlsd-touchpad.rules\n" +
                        "cat << 'EOF' > /etc/udev/rules.d/70-liquidlsd-touchpad.rules\n" +
                        "# Liquid LSD - Enable seat user access to touchpads for performance console\n" +
                        "KERNEL==\"event*\", SUBSYSTEM==\"input\", ENV{ID_INPUT_TOUCHPAD}==\"1\", TAG+=\"uaccess\", TAG+=\"seat\", RUN{builtin}+=\"uaccess\"\n" +
                        "EOF\n" +
                        "udevadm control --reload-rules && udevadm trigger --action=add --subsystem-match=input"

                logger.info { "Executing pkexec to install touchpad udev permissions..." }
                val process = ProcessBuilder("pkexec", "bash", "-c", cmd).start()
                val exitCode = process.waitFor()

                if (exitCode == 0) {
                    logger.info { "Touchpad permissions successfully installed via pkexec!" }
                    Thread.sleep(400) // Brief pause for udevadm trigger to finish
                    val b = backend
                    if (b is LinuxEvdevTouchBackend) {
                        b.detectDevice()
                    }
                    backend.start()
                    success = (backend.state == TouchBackendState.READY)
                } else {
                    logger.warn { "pkexec permission elevation failed with exit code $exitCode" }
                }
            } catch (t: Throwable) {
                logger.error(t) { "Error executing pkexec permission elevation" }
            } finally {
                isElevatingPermissions = false
                onComplete(success)
            }
        }, "LiquidLSD-TouchpadPermissionElevation").start()
    }

    fun shutdown() {
        setConsoleActive(false)
        backend.stop()
    }
}
