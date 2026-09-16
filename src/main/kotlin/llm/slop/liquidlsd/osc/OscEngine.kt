package llm.slop.liquidlsd.osc

import mu.KotlinLogging
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.DatagramChannel
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

enum class OscPacketDirection { IN, OUT }

data class OscSniffedPacket(
    val direction: OscPacketDirection,
    val address: String,
    val argsSummary: String,
    val remoteHost: String,
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * Low-latency UDP transport for OSC 1.0 (TouchOSC and other control surfaces).
 *
 * Runs a dedicated background receiver thread reading datagrams via [DatagramChannel],
 * decoding them with [OscCodec], and depositing messages onto a lock-free [inboundQueue]
 * for the render/main thread to drain each frame. The remote client's address is
 * auto-learned from the sender of the first inbound datagram, so outbound feedback
 * packets (e.g. macro value broadcasts) can be routed back without manual IP entry.
 */
object OscEngine {
    private val logger = KotlinLogging.logger {}

    const val DEFAULT_INBOUND_PORT = 8000
    const val DEFAULT_OUTBOUND_PORT = 9000
    private const val MAX_DATAGRAM_SIZE = 65507
    private const val MAX_SNIFFED_PACKETS = 64

    /** Decoded inbound messages awaiting processing on the main thread. */
    val inboundQueue = ConcurrentLinkedQueue<OscMessage>()

    private val running = AtomicBoolean(false)
    private var receiveChannel: DatagramChannel? = null
    private var sendChannel: DatagramChannel? = null
    private var receiverThread: Thread? = null

    @Volatile
    var inboundPort: Int = DEFAULT_INBOUND_PORT
        private set

    @Volatile
    var outboundPort: Int = DEFAULT_OUTBOUND_PORT
        private set

    private val learnedRemoteAddress = AtomicReference<InetSocketAddress?>(null)

    val isRunning: Boolean get() = running.get()

    /** Human-readable label for the auto-learned remote client, or "(none)" if unknown. */
    fun remoteAddressLabel(): String {
        val addr = learnedRemoteAddress.get() ?: return "(none)"
        return "${addr.address.hostAddress}:$outboundPort"
    }

    /** Actual bound local port (useful when starting with an ephemeral port `0` in tests). */
    fun boundInboundPort(): Int = try { receiveChannel?.socket()?.localPort ?: -1 } catch (e: Exception) { -1 }

    private val sniffedLock = Any()
    private val sniffedPackets = ArrayDeque<OscSniffedPacket>(MAX_SNIFFED_PACKETS)

    fun getRecentPackets(): List<OscSniffedPacket> = synchronized(sniffedLock) { sniffedPackets.toList() }

    private fun recordSniffed(packet: OscSniffedPacket) {
        synchronized(sniffedLock) {
            if (sniffedPackets.size >= MAX_SNIFFED_PACKETS) sniffedPackets.removeFirst()
            sniffedPackets.addLast(packet)
        }
    }

    fun start(inPort: Int = DEFAULT_INBOUND_PORT, outPort: Int = DEFAULT_OUTBOUND_PORT) {
        if (running.get()) return
        try {
            val rx = DatagramChannel.open()
            rx.socket().bind(InetSocketAddress(inPort))
            receiveChannel = rx

            sendChannel = DatagramChannel.open()

            inboundPort = inPort
            outboundPort = outPort
            running.set(true)

            val thread = Thread({ receiveLoop() }, "osc-receiver")
            thread.isDaemon = true
            thread.start()
            receiverThread = thread

            logger.info { "OscEngine started: listening on UDP port ${boundInboundPort()}, feedback target port $outPort" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to start OscEngine on port $inPort" }
            running.set(false)
            closeChannelsQuietly()
        }
    }

    fun stop() {
        if (!running.get()) return
        running.set(false)
        closeChannelsQuietly()
        receiverThread?.interrupt()
        receiverThread = null
        inboundQueue.clear()
        synchronized(sniffedLock) { sniffedPackets.clear() }
        learnedRemoteAddress.set(null)
        logger.info { "OscEngine stopped" }
    }

    private fun closeChannelsQuietly() {
        try { receiveChannel?.close() } catch (e: Exception) { /* already closed */ }
        try { sendChannel?.close() } catch (e: Exception) { /* already closed */ }
        receiveChannel = null
        sendChannel = null
    }

    private fun receiveLoop() {
        val buffer = ByteBuffer.allocate(MAX_DATAGRAM_SIZE)
        val channel = receiveChannel ?: return
        while (running.get()) {
            try {
                buffer.clear()
                val sender = channel.receive(buffer) ?: continue
                buffer.flip()
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)

                val senderAddr = sender as? InetSocketAddress
                if (senderAddr != null) learnedRemoteAddress.set(senderAddr)

                val element = try {
                    OscCodec.decode(bytes)
                } catch (e: Exception) {
                    logger.debug(e) { "Failed to decode inbound OSC packet from $senderAddr" }
                    continue
                }
                dispatchInbound(element, senderAddr?.address?.hostAddress ?: "unknown")
            } catch (e: ClosedChannelException) {
                break
            } catch (e: Exception) {
                if (running.get()) logger.warn(e) { "Error in OSC receive loop" }
            }
        }
    }

    private fun dispatchInbound(element: OscElement, remoteHost: String) {
        when (element) {
            is OscMessage -> {
                inboundQueue.offer(element)
                recordSniffed(OscSniffedPacket(OscPacketDirection.IN, element.address, formatArgs(element.args), remoteHost))
            }
            is OscBundle -> {
                for (el in element.elements) dispatchInbound(el, remoteHost)
            }
        }
    }

    /** Sends an outbound OSC message to the auto-learned remote client. No-op if none has been learned yet. */
    fun send(address: String, args: List<Any> = emptyList()) {
        val channel = sendChannel ?: return
        val target = learnedRemoteAddress.get() ?: return
        try {
            val bytes = OscCodec.encode(OscMessage(address, args))
            channel.send(ByteBuffer.wrap(bytes), InetSocketAddress(target.address, outboundPort))
            recordSniffed(OscSniffedPacket(OscPacketDirection.OUT, address, formatArgs(args), target.address.hostAddress))
        } catch (e: Exception) {
            logger.debug(e) { "Failed to send OSC message to $address" }
        }
    }

    fun sendFloat(address: String, value: Float) = send(address, listOf(value))

    private fun formatArgs(args: List<Any>): String = args.joinToString(", ") { arg ->
        when (arg) {
            is Float -> "%.3f".format(arg)
            is ByteArray -> "blob[${arg.size}]"
            else -> arg.toString()
        }
    }
}
