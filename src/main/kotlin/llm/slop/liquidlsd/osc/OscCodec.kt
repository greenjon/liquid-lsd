package llm.slop.liquidlsd.osc

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A parsed OSC packet: either a single [OscMessage] or an [OscBundle] of nested elements.
 */
sealed interface OscElement

/**
 * An OSC 1.0 message. Supported argument types: [Float] (`f`), [Int] (`i`), [String] (`s`),
 * [ByteArray] (`b`, blob), and [Boolean] (`T`/`F`).
 */
data class OscMessage(val address: String, val args: List<Any> = emptyList()) : OscElement

/** An OSC 1.0 bundle. [timetag] is a 64-bit NTP timestamp; [OscCodec.IMMEDIATE_TIMETAG] means "execute now". */
data class OscBundle(val timetag: Long = OscCodec.IMMEDIATE_TIMETAG, val elements: List<OscElement> = emptyList()) : OscElement

/**
 * Zero-dependency OSC 1.0 binary codec built on [ByteBuffer]. Big-endian byte order,
 * 4-byte-aligned null-terminated strings, and length-prefixed 4-byte-aligned blobs per spec.
 */
object OscCodec {
    /** NTP timetag value meaning "execute immediately", per the OSC 1.0 spec. */
    const val IMMEDIATE_TIMETAG = 1L

    private const val BUNDLE_TAG = "#bundle"

    fun encode(element: OscElement): ByteArray {
        val buffer = ByteBuffer.allocate(sizeOf(element)).order(ByteOrder.BIG_ENDIAN)
        writeElement(buffer, element)
        return buffer.array()
    }

    fun decode(bytes: ByteArray): OscElement {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        return decodeElement(buffer)
    }

    // --- Sizing ---

    private fun sizeOf(element: OscElement): Int = when (element) {
        is OscMessage -> sizeOfMessage(element)
        is OscBundle -> sizeOfBundle(element)
    }

    private fun sizeOfMessage(msg: OscMessage): Int {
        val typeTag = buildTypeTag(msg.args)
        var size = stringPaddedLength(msg.address) + stringPaddedLength(typeTag)
        for (arg in msg.args) {
            size += when (arg) {
                is Float -> 4
                is Int -> 4
                is String -> stringPaddedLength(arg)
                is ByteArray -> 4 + blobPaddedLength(arg.size)
                is Boolean -> 0
                else -> throw IllegalArgumentException("Unsupported OSC argument type: ${arg::class}")
            }
        }
        return size
    }

    private fun sizeOfBundle(bundle: OscBundle): Int {
        var size = stringPaddedLength(BUNDLE_TAG) + 8 // 8-byte NTP timetag
        for (el in bundle.elements) {
            size += 4 + sizeOf(el) // 4-byte element size prefix
        }
        return size
    }

    private fun stringPaddedLength(s: String): Int {
        val byteLen = s.toByteArray(Charsets.UTF_8).size
        return (((byteLen + 1) + 3) / 4) * 4
    }

    private fun blobPaddedLength(n: Int): Int = ((n + 3) / 4) * 4

    private fun buildTypeTag(args: List<Any>): String {
        val sb = StringBuilder(args.size + 1)
        sb.append(',')
        for (arg in args) {
            sb.append(
                when (arg) {
                    is Float -> 'f'
                    is Int -> 'i'
                    is String -> 's'
                    is ByteArray -> 'b'
                    is Boolean -> if (arg) 'T' else 'F'
                    else -> throw IllegalArgumentException("Unsupported OSC argument type: ${arg::class}")
                }
            )
        }
        return sb.toString()
    }

    // --- Writing ---

    private fun writeElement(buffer: ByteBuffer, element: OscElement) {
        when (element) {
            is OscMessage -> writeMessage(buffer, element)
            is OscBundle -> writeBundle(buffer, element)
        }
    }

    private fun writeMessage(buffer: ByteBuffer, msg: OscMessage) {
        writeString(buffer, msg.address)
        writeString(buffer, buildTypeTag(msg.args))
        for (arg in msg.args) {
            when (arg) {
                is Float -> buffer.putFloat(arg)
                is Int -> buffer.putInt(arg)
                is String -> writeString(buffer, arg)
                is ByteArray -> writeBlob(buffer, arg)
                is Boolean -> { /* T/F carry no argument bytes */ }
                else -> throw IllegalArgumentException("Unsupported OSC argument type: ${arg::class}")
            }
        }
    }

    private fun writeBundle(buffer: ByteBuffer, bundle: OscBundle) {
        writeString(buffer, BUNDLE_TAG)
        buffer.putLong(bundle.timetag)
        for (el in bundle.elements) {
            val elBytes = encode(el)
            buffer.putInt(elBytes.size)
            buffer.put(elBytes)
        }
    }

    private fun writeString(buffer: ByteBuffer, s: String) {
        val bytes = s.toByteArray(Charsets.UTF_8)
        buffer.put(bytes)
        val paddedTotal = stringPaddedLength(s)
        repeat(paddedTotal - bytes.size) { buffer.put(0) }
    }

    private fun writeBlob(buffer: ByteBuffer, bytes: ByteArray) {
        buffer.putInt(bytes.size)
        buffer.put(bytes)
        repeat(blobPaddedLength(bytes.size) - bytes.size) { buffer.put(0) }
    }

    // --- Reading ---

    private fun decodeElement(buffer: ByteBuffer): OscElement {
        val first = readString(buffer)
        return if (first == BUNDLE_TAG) {
            val timetag = buffer.long
            val elements = mutableListOf<OscElement>()
            while (buffer.remaining() >= 4) {
                val elemSize = buffer.int
                require(elemSize in 0..buffer.remaining()) { "Malformed OSC bundle element size: $elemSize" }
                val elemBytes = ByteArray(elemSize)
                buffer.get(elemBytes)
                elements.add(decodeElement(ByteBuffer.wrap(elemBytes).order(ByteOrder.BIG_ENDIAN)))
            }
            OscBundle(timetag, elements)
        } else {
            val typeTag = readString(buffer)
            val args = mutableListOf<Any>()
            for (i in 1 until typeTag.length) {
                when (typeTag[i]) {
                    'f' -> args.add(buffer.float)
                    'i' -> args.add(buffer.int)
                    's' -> args.add(readString(buffer))
                    'b' -> {
                        val blobLen = buffer.int
                        require(blobLen in 0..buffer.remaining()) { "Malformed OSC blob length: $blobLen" }
                        val blobBytes = ByteArray(blobLen)
                        buffer.get(blobBytes)
                        val pad = blobPaddedLength(blobLen) - blobLen
                        if (pad > 0) buffer.position((buffer.position() + pad).coerceAtMost(buffer.limit()))
                        args.add(blobBytes)
                    }
                    'T' -> args.add(true)
                    'F' -> args.add(false)
                    else -> { /* unsupported/unknown type tag char: skip, no payload bytes assumed */ }
                }
            }
            OscMessage(first, args)
        }
    }

    private fun readString(buffer: ByteBuffer): String {
        val start = buffer.position()
        val limit = buffer.limit()
        var end = start
        while (end < limit && buffer.get(end) != 0.toByte()) end++
        val bytes = ByteArray(end - start)
        for (i in bytes.indices) bytes[i] = buffer.get(start + i)
        val paddedTotal = (((end - start) + 1 + 3) / 4) * 4
        buffer.position((start + paddedTotal).coerceAtMost(limit))
        return String(bytes, Charsets.UTF_8)
    }
}
