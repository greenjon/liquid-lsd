package llm.slop.liquidlsd.control

/**
 * The `nav.button.<n>` command family: the free side buttons. Their meaning is context dependent
 * (see [NavSurface]); `.alt` is the same button with shift held.
 */
object NavCommands {
    const val BUTTON_COUNT = 3
    const val BANK_NEXT = "controller.bank_next"
    const val BANK_PREV = "controller.bank_prev"
    const val CHAIN_LINK_TOGGLE = "fx.chain_link_toggle"

    fun register(registry: CommandRegistry) {
        registry.register(Command(BANK_NEXT, CommandKind.TRIGGER, "controller", "Next controller bank (and its Perform page)") { _, ctx -> ctx.bankDelta += 1 })
        registry.register(Command(BANK_PREV, CommandKind.TRIGGER, "controller", "Previous controller bank (and its Perform page)") { _, ctx -> ctx.bankDelta -= 1 })
        registry.register(Command(CHAIN_LINK_TOGGLE, CommandKind.TRIGGER, "fx", "Link or unlink all slots of the touched FX chain to its Super Knob") { _, ctx ->
            ctx.knobSurface?.toggleChainLink()
        })
        for (n in 1..BUTTON_COUNT) {
            registry.register(Command("nav.button.$n", CommandKind.TRIGGER, "nav", "Side button $n: context-dependent navigation") { _, ctx ->
                ctx.navSurface?.button(n - 1, shifted = false)
            })
            registry.register(Command("nav.button.$n.alt", CommandKind.TRIGGER, "nav", "Side button $n with shift: context-dependent navigation") { _, ctx ->
                ctx.navSurface?.button(n - 1, shifted = true)
            })
        }
    }
}
