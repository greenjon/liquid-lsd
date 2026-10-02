package llm.slop.liquidlsd.control

import mu.KotlinLogging

/** Wraps [inner] and logs every message at INFO, for diagnosing what a controller is actually sent. */
class TracingSink(private val inner: MidiSink, private val name: String) : MidiSink {
    private val logger = KotlinLogging.logger {}
    private val startMs = System.currentTimeMillis()

    override fun sendCc(channel: Int, cc: Int, value: Int) {
        logger.info { "feedback tx +${System.currentTimeMillis() - startMs}ms $name ch=$channel cc=$cc value=$value" }
        inner.sendCc(channel, cc, value)
    }

    override val isHealthy: Boolean get() = inner.isHealthy

    override fun close() {
        logger.info { "feedback port closed: $name" }
        inner.close()
    }

    companion object {
        /** True when tracing is requested by the profile or by the LSD_MIDI_TRACE=1 environment variable. */
        fun enabled(profile: ControllerProfile): Boolean = profile.output.trace || System.getenv("LSD_MIDI_TRACE") == "1"
    }
}
