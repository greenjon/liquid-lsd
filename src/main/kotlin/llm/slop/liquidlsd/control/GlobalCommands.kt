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
    const val FX_QUEUE_NEXT = "fx.queue_next"
    const val FX_QUEUE_PREV = "fx.queue_prev"
    const val FX_BG_QUEUE_NEXT = "fx.bg_queue_next"
    const val FX_BG_QUEUE_PREV = "fx.bg_queue_prev"
    const val TAP_TEMPO = "clock.tap_tempo"
    const val AUTO_CROSSFADE = "mixer.auto_crossfade_trigger"
    const val SNAP_A = "mixer.crossfade_snap_a"
    const val SNAP_B = "mixer.crossfade_snap_b"

    fun registerAll(registry: CommandRegistry) {
        fun trigger(id: String, alias: String, category: String, description: String, run: (CommandContext) -> Unit) {
            registry.register(Command(id, CommandKind.TRIGGER, category, description) { _, ctx -> run(ctx) })
            registry.registerAlias(alias, id)
        }

        trigger(QUEUE_NEXT, "Global/queueNext", "mixer", "Advance the A/B Queue") { it.queueDelta += 1 }
        trigger(QUEUE_PREV, "Global/queuePrev", "mixer", "Step the A/B Queue back") { it.queueDelta -= 1 }
        trigger(BG_QUEUE_NEXT, "Global/bgQueueNext", "mixer", "Advance the BG Queue") { it.bgQueueDelta += 1 }
        trigger(BG_QUEUE_PREV, "Global/bgQueuePrev", "mixer", "Step the BG Queue back") { it.bgQueueDelta -= 1 }
        trigger(TRANS_QUEUE_NEXT, "Global/transQueueNext", "mixer", "Advance the Transition Queue") { it.transQueueDelta += 1 }
        trigger(TRANS_QUEUE_PREV, "Global/transQueuePrev", "mixer", "Step the Transition Queue back") { it.transQueueDelta -= 1 }
        fun fxTrigger(id: String, description: String, run: (CommandContext) -> Unit) =
            registry.register(Command(id, CommandKind.TRIGGER, "fx", description) { _, ctx -> run(ctx) })
        fxTrigger(FX_QUEUE_NEXT, "Advance the A/B FX Queue") { it.fxQueueDelta += 1 }
        fxTrigger(FX_QUEUE_PREV, "Step the A/B FX Queue back") { it.fxQueueDelta -= 1 }
        fxTrigger(FX_BG_QUEUE_NEXT, "Advance the BG FX Queue") { it.fxBgQueueDelta += 1 }
        fxTrigger(FX_BG_QUEUE_PREV, "Step the BG FX Queue back") { it.fxBgQueueDelta -= 1 }
        trigger(TAP_TEMPO, "Global/tapTempo", "clock", "Tap tempo") { it.onTapTempo() }

        trigger(AUTO_CROSSFADE, "Global/autoFade", "xf", "Start or cancel an automated crossfade") { ctx ->
            val xf = ctx.crossfade
            if (xf.isAutoFading) {
                xf.onCrossfadeManualTakeover()
            } else {
                val targetIsA = xf.crossfadeBase > 0.0f
                xf.targetCrossfade = if (targetIsA) -1.0f else 1.0f
                xf.isAutoFading = true
                xf.muteCrossfadeNonMidiCv()
            }
        }
        trigger(SNAP_A, "Global/snapDeckA", "mixer", "Snap the crossfader to Deck A") { ctx ->
            ctx.crossfade.onCrossfadeManualTakeover()
            ctx.crossfade.setCrossfade(-1.0f)
        }
        trigger(SNAP_B, "Global/snapDeckB", "mixer", "Snap the crossfader to Deck B") { ctx ->
            ctx.crossfade.onCrossfadeManualTakeover()
            ctx.crossfade.setCrossfade(1.0f)
        }
    }
}
