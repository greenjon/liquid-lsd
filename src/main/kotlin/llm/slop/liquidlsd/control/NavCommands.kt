package llm.slop.liquidlsd.control

/**
 * The `nav.button.<n>` command family: the free side buttons. Their meaning is context dependent
 * (see [NavSurface]); `.alt` is the same button with shift held.
 */
object NavCommands {
    const val BUTTON_COUNT = 3

    fun register(registry: CommandRegistry) {
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
