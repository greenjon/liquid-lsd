package llm.slop.liquidlsd.control

/**
 * The fixed set of global (non-parameter) actions that used to be hard-coded in MidiMappingManager.
 * Ids follow docs/developer/unified_control_mapping.md; the `Global/...` aliases keep existing
 * MIDI profiles loading unchanged.
 */
object GlobalCommands {
    const val QUEUE_NEXT = "mixer.queue_next"
    const val QUEUE_PREV = "mixer.queue_prev"
    const val BG_QUEUE_NEXT = "mixer.bg_queue_next"
    const val BG_QUEUE_PREV = "mixer.bg_queue_prev"
    const val TRANS_QUEUE_NEXT = "mixer.trans_queue_next"
    const val TRANS_QUEUE_PREV = "mixer.trans_queue_prev"
    const val TAP_TEMPO = "clock.tap_tempo"
    const val AUTO_CROSSFADE = "mixer.auto_crossfade_trigger"
    const val SNAP_A = "mixer.crossfade_snap_a"
    const val SNAP_B = "mixer.crossfade_snap_b"

    fun registerAll(registry: CommandRegistry) {
        fun trigger(id: String, alias: String, category: String, description: String, run: (CommandContext) -> Unit) {
            registry.register(Command(id, CommandKind.TRIGGER, category, description) { _, ctx -> run(ctx) })
            registry.registerAlias(alias, id)
        }

        trigger(QUEUE_NEXT, "Global/queueNext", "mixer", "Advance the A/B play queue") { it.queueDelta += 1 }
        trigger(QUEUE_PREV, "Global/queuePrev", "mixer", "Step the A/B play queue back") { it.queueDelta -= 1 }
        trigger(BG_QUEUE_NEXT, "Global/bgQueueNext", "mixer", "Advance the background shader queue") { it.bgQueueDelta += 1 }
        trigger(BG_QUEUE_PREV, "Global/bgQueuePrev", "mixer", "Step the background shader queue back") { it.bgQueueDelta -= 1 }
        trigger(TRANS_QUEUE_NEXT, "Global/transQueueNext", "mixer", "Advance the transition queue") { it.transQueueDelta += 1 }
        trigger(TRANS_QUEUE_PREV, "Global/transQueuePrev", "mixer", "Step the transition queue back") { it.transQueueDelta -= 1 }
        trigger(TAP_TEMPO, "Global/tapTempo", "clock", "Tap tempo") { it.onTapTempo() }

        trigger(AUTO_CROSSFADE, "Global/autoFade", "mixer", "Start or cancel an automated crossfade") { ctx ->
            val mixer = ctx.mixer
            if (mixer.isAutoFading) {
                mixer.onCrossfadeManualTakeover()
            } else {
                val targetIsA = mixer.crossfade.baseValue > 0.0f
                mixer.targetCrossfade = if (targetIsA) -1.0f else 1.0f
                mixer.isAutoFading = true
                mixer.muteCrossfadeNonMidiCv()
            }
        }
        trigger(SNAP_A, "Global/snapDeckA", "mixer", "Snap the crossfader to Deck A") { ctx ->
            ctx.mixer.onCrossfadeManualTakeover()
            ctx.mixer.crossfade.set(-1.0f)
        }
        trigger(SNAP_B, "Global/snapDeckB", "mixer", "Snap the crossfader to Deck B") { ctx ->
            ctx.mixer.onCrossfadeManualTakeover()
            ctx.mixer.crossfade.set(1.0f)
        }
    }
}
