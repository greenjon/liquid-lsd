package llm.slop.liquidlsd.input

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Structure
import mu.KotlinLogging
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

private val logger = KotlinLogging.logger {}

/**
 * Linux multi-touch trackpad backend utilizing evdev and direct libc ioctl/read via JNA.
 * Features:
 * - Direct JNA libc binding for POSIX open, close, read, and ioctl.
 * - Hardware axis min/max query via EVIOCGABS.
 * - Multi-touch Protocol B Slot State Cache with SYN_REPORT sync.
 * - Dynamic EVIOCGRAB cursor grab/ungrab.
 * - Automatic touchpad device discovery with permission validation.
 */
class LinuxEvdevTouchBackend(
    private val eventQueue: ConcurrentLinkedQueue<TouchConsoleEvent>
) : TouchStripBackend {

    interface CLib : Library {
        fun open(path: String, flags: Int): Int
        fun close(fd: Int): Int
        fun read(fd: Int, buf: ByteArray, count: Int): Int
        fun ioctl(fd: Int, request: Int, arg: Int): Int
        fun ioctl(fd: Int, request: Int, arg: Structure): Int
    }

    @Structure.FieldOrder("value", "minimum", "maximum", "fuzz", "flat", "resolution")
    open class InputAbsInfo : Structure() {
        @JvmField var value: Int = 0
        @JvmField var minimum: Int = 0
        @JvmField var maximum: Int = 0
        @JvmField var fuzz: Int = 0
        @JvmField var flat: Int = 0
        @JvmField var resolution: Int = 0
    }

    companion object {
        private const val O_RDONLY = 0x0000
        private const val O_NONBLOCK = 0x0800

        // _IOW('E', 0x90, int) = 0x40044590 (decimal 1074021776)
        const val EVIOCGRAB = 0x40044590

        // _IOR('E', 0x20 + axis, struct input_absinfo) = 0x80184520 + axis
        fun EVIOCGABS(axis: Int): Int = (0x80184520L or axis.toLong()).toInt()

        const val EV_SYN = 0x00
        const val EV_ABS = 0x03
        const val SYN_REPORT = 0x00

        const val ABS_X = 0x00
        const val ABS_Y = 0x01
        const val ABS_MT_SLOT = 0x2f         // 47
        const val ABS_MT_TOUCH_MAJOR = 0x30  // 48
        const val ABS_MT_POSITION_X = 0x35   // 53
        const val ABS_MT_POSITION_Y = 0x36   // 54
        const val ABS_MT_TRACKING_ID = 0x39  // 57

        const val MAX_SLOTS = 16
        const val EVENT_SIZE = 24 // 64-bit Linux: 16-byte timeval + 2-byte type + 2-byte code + 4-byte value
    }

    private var clib: CLib? = null
    private var fd: Int = -1
    private var detectedPath: String? = null
    private val isRunning = AtomicBoolean(false)
    private val isGrabbed = AtomicBoolean(false)
    private var readerThread: Thread? = null

    override var state: TouchBackendState = TouchBackendState.NO_DEVICE
        private set

    // Hardware axis ranges queried dynamically via EVIOCGABS
    var minX: Int = 0; private set
    var maxX: Int = 1920; private set
    var minY: Int = 0; private set
    var maxY: Int = 1080; private set

    private class SlotState {
        var trackingId: Int = -1
        var rawX: Int = 0
        var rawY: Int = 0
        var prevTrackingId: Int = -1
        var dirty: Boolean = false
    }

    private val slots = Array(MAX_SLOTS) { SlotState() }
    private var activeSlot = 0

    init {
        try {
            clib = Native.load("c", CLib::class.java)
        } catch (t: Throwable) {
            logger.warn(t) { "Unable to load libc via JNA for evdev trackpad support" }
        }
        detectDevice()
    }

    fun hasTouchpadAccess(devicePath: String): Boolean {
        val file = File(devicePath)
        return file.exists() && file.canRead() && file.canWrite()
    }

    fun detectDevice(): String? {
        val candidate = findTouchpadDeviceNode()
        detectedPath = candidate

        if (candidate == null) {
            state = TouchBackendState.NO_DEVICE
            return null
        }

        val file = File(candidate)
        if (!file.exists()) {
            state = TouchBackendState.NO_DEVICE
            return null
        }

        if (!file.canRead() || !file.canWrite()) {
            logger.warn { "Touchpad detected at $candidate but read/write permissions are missing." }
            state = TouchBackendState.PERMISSION_REQUIRED
            return candidate
        }

        state = TouchBackendState.READY
        logger.info { "Touchpad successfully detected with full read/write access: $candidate" }
        return candidate
    }

    private fun findTouchpadDeviceNode(): String? {
        // 1. First check /dev/input/by-id/ symlinks
        val byIdDir = File("/dev/input/by-id")
        if (byIdDir.isDirectory) {
            val nodes = byIdDir.listFiles { _, name ->
                name.contains("touchpad", ignoreCase = true) || name.contains("trackpad", ignoreCase = true)
            }
            if (!nodes.isNullOrEmpty()) {
                val resolved = nodes[0].canonicalPath
                logger.info { "Found touchpad via /dev/input/by-id: ${nodes[0].name} -> $resolved" }
                return resolved
            }
        }

        // 2. Scan /proc/bus/input/devices for touchpad device handler
        val procFile = File("/proc/bus/input/devices")
        if (procFile.exists()) {
            try {
                val content = procFile.readText()
                val blocks = content.split("\n\n")
                for (block in blocks) {
                    val lines = block.lines()
                    val nameLine = lines.firstOrNull { it.startsWith("N: Name=") } ?: ""
                    val handlersLine = lines.firstOrNull { it.startsWith("H: Handlers=") } ?: ""
                    val isTouchpad = nameLine.contains("touchpad", ignoreCase = true) ||
                            nameLine.contains("trackpad", ignoreCase = true) ||
                            nameLine.contains("synaptics", ignoreCase = true) ||
                            nameLine.contains("elan", ignoreCase = true)

                    if (isTouchpad) {
                        val eventMatch = Regex("""event\d+""").find(handlersLine)
                        if (eventMatch != null) {
                            val path = "/dev/input/${eventMatch.value}"
                            logger.info { "Found touchpad in /proc/bus/input/devices: $nameLine -> $path" }
                            return path
                        }
                    }
                }
            } catch (t: Throwable) {
                logger.debug(t) { "Error scanning /proc/bus/input/devices" }
            }
        }

        // 3. Fallback: check event devices directly for MT capabilities
        val inputDir = File("/dev/input")
        if (inputDir.isDirectory) {
            val eventFiles = inputDir.listFiles { _, name -> name.startsWith("event") }
            if (eventFiles != null) {
                for (f in eventFiles) {
                    if (f.name.contains("event6") || f.name.contains("event5")) {
                        // Candidate fallback on standard laptops
                        return f.absolutePath
                    }
                }
            }
        }

        return null
    }

    override fun start(): Boolean {
        if (isRunning.get()) return true
        val c = clib ?: return false
        val path = detectDevice() ?: return false

        if (state != TouchBackendState.READY) {
            logger.warn { "Cannot start LinuxEvdevTouchBackend: state is $state" }
            return false
        }

        fd = c.open(path, O_RDONLY or O_NONBLOCK)
        if (fd < 0) {
            logger.error { "Failed to open $path: fd=$fd" }
            state = TouchBackendState.PERMISSION_REQUIRED
            return false
        }

        // Query hardware axis limits via EVIOCGABS
        queryAxisLimits(c, fd)

        isRunning.set(true)
        readerThread = Thread({ readLoop() }, "LiquidLSD-EvdevTouchpadReader").apply {
            isDaemon = true
            start()
        }

        logger.info { "LinuxEvdevTouchBackend started successfully on $path (X: $minX..$maxX, Y: $minY..$maxY)" }
        return true
    }

    private fun queryAxisLimits(c: CLib, fd: Int) {
        val absX = InputAbsInfo()
        val absY = InputAbsInfo()

        val retX = c.ioctl(fd, EVIOCGABS(ABS_MT_POSITION_X), absX)
        if (retX == 0 && absX.maximum > absX.minimum) {
            minX = absX.minimum
            maxX = absX.maximum
        } else {
            // Fallback to legacy ABS_X
            val retLegacyX = c.ioctl(fd, EVIOCGABS(ABS_X), absX)
            if (retLegacyX == 0 && absX.maximum > absX.minimum) {
                minX = absX.minimum
                maxX = absX.maximum
            }
        }

        val retY = c.ioctl(fd, EVIOCGABS(ABS_MT_POSITION_Y), absY)
        if (retY == 0 && absY.maximum > absY.minimum) {
            minY = absY.minimum
            maxY = absY.maximum
        } else {
            // Fallback to legacy ABS_Y
            val retLegacyY = c.ioctl(fd, EVIOCGABS(ABS_Y), absY)
            if (retLegacyY == 0 && absY.maximum > absY.minimum) {
                minY = absY.minimum
                maxY = absY.maximum
            }
        }
    }

    override fun setGrabbed(grabbed: Boolean) {
        val c = clib ?: return
        if (fd < 0) return

        if (isGrabbed.compareAndSet(!grabbed, grabbed)) {
            val arg = if (grabbed) 1 else 0
            val res = c.ioctl(fd, EVIOCGRAB, arg)
            if (res == 0) {
                logger.debug { "Touchpad hardware grab set to $grabbed" }
            } else {
                logger.warn { "Failed to set EVIOCGRAB to $grabbed, result code: $res" }
            }
        }
    }

    private fun readLoop() {
        val c = clib ?: return
        val buffer = ByteArray(EVENT_SIZE * 32)
        val byteBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)

        while (isRunning.get()) {
            val bytesRead = c.read(fd, buffer, buffer.size)
            if (bytesRead > 0) {
                val numEvents = bytesRead / EVENT_SIZE
                for (i in 0 until numEvents) {
                    val offset = i * EVENT_SIZE
                    val type = byteBuffer.getShort(offset + 16).toInt() and 0xFFFF
                    val code = byteBuffer.getShort(offset + 18).toInt() and 0xFFFF
                    val value = byteBuffer.getInt(offset + 20)

                    processEvent(type, code, value)
                }
            } else {
                // Non-blocking read empty, sleep briefly to preserve CPU
                try {
                    Thread.sleep(2)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
    }

    private fun processEvent(type: Int, code: Int, value: Int) {
        when (type) {
            EV_ABS -> {
                when (code) {
                    ABS_MT_SLOT -> {
                        if (value in 0 until MAX_SLOTS) {
                            activeSlot = value
                        }
                    }
                    ABS_MT_TRACKING_ID -> {
                        val slot = slots[activeSlot]
                        slot.trackingId = value
                        slot.dirty = true
                    }
                    ABS_MT_POSITION_X -> {
                        val slot = slots[activeSlot]
                        slot.rawX = value
                        slot.dirty = true
                    }
                    ABS_MT_POSITION_Y -> {
                        val slot = slots[activeSlot]
                        slot.rawY = value
                        slot.dirty = true
                    }
                }
            }
            EV_SYN -> {
                if (code == SYN_REPORT) {
                    commitSlotState()
                }
            }
        }
    }

    private fun commitSlotState() {
        val rangeX = (maxX - minX).coerceAtLeast(1).toFloat()
        val rangeY = (maxY - minY).coerceAtLeast(1).toFloat()

        for (i in 0 until MAX_SLOTS) {
            val slot = slots[i]
            if (!slot.dirty) continue

            val normX = ((slot.rawX - minX) / rangeX).coerceIn(0.0f, 1.0f)
            // Invert Y so physical bottom is 0.0 (crossfader) and physical top is 1.0 (100% alpha)
            val normY = (1.0f - ((slot.rawY - minY) / rangeY)).coerceIn(0.0f, 1.0f)

            if (slot.trackingId == -1 && slot.prevTrackingId != -1) {
                // Finger lifted
                eventQueue.add(TouchConsoleEvent.TouchUp(slot.prevTrackingId.toLong()))
                slot.prevTrackingId = -1
            } else if (slot.trackingId != -1 && slot.prevTrackingId == -1) {
                // New finger down
                eventQueue.add(TouchConsoleEvent.TouchDown(slot.trackingId.toLong(), normX, normY))
                slot.prevTrackingId = slot.trackingId
            } else if (slot.trackingId != -1 && slot.prevTrackingId == slot.trackingId) {
                // Finger moved
                eventQueue.add(TouchConsoleEvent.TouchMove(slot.trackingId.toLong(), normX, normY))
            }

            slot.dirty = false
        }
    }

    override fun stop() {
        if (!isRunning.compareAndSet(true, false)) return
        setGrabbed(false)
        try {
            readerThread?.interrupt()
            readerThread?.join(200)
        } catch (_: Throwable) {}

        clib?.let { c ->
            if (fd >= 0) {
                c.close(fd)
                fd = -1
            }
        }
        logger.info { "LinuxEvdevTouchBackend stopped." }
    }
}
