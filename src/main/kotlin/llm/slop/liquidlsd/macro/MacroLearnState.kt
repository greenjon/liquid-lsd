package llm.slop.liquidlsd.macro

/**
 * Transient state manager for Macro Learn Mode and Control selection in the UI.
 *
 * Coordinates interactive click-to-bind linking between Macro Controls (Column 3)
 * and target parameters (Column 1) or modulator properties (Column 2).
 */
object MacroLearnState {
    const val TIMEOUT_MS = 20_000L

    var selectedControlId: String? = null

    data class LearnSession(
        val controlId: String,
        val startTimeMs: Long = System.currentTimeMillis()
    )

    var activeSession: LearnSession? = null
        private set

    var statusBanner: String? = null
        private set
    private var statusBannerExpiryMs: Long = 0L

    /** Sets a temporary status message displayed in the UI banner. */
    fun setStatus(message: String, durationMs: Long = 4000L) {
        statusBanner = message
        statusBannerExpiryMs = System.currentTimeMillis() + durationMs
    }

    /** Returns the current active status message if not expired. */
    fun getActiveStatus(): String? {
        val banner = statusBanner ?: return null
        if (System.currentTimeMillis() > statusBannerExpiryMs) {
            statusBanner = null
            return null
        }
        return banner
    }

    /** Clears the status message. */
    fun clearStatus() {
        statusBanner = null
        statusBannerExpiryMs = 0L
    }

    /** Arms Learn Mode for the given control ID. */
    fun startLearn(controlId: String) {
        selectedControlId = controlId
        activeSession = LearnSession(controlId, System.currentTimeMillis())
        setStatus("LEARN MODE: Click any parameter slider or modulator property to bind.")
    }

    /** Cancels any active Learn Mode session. */
    fun cancelLearn() {
        if (activeSession != null) {
            activeSession = null
            setStatus("Learn Mode cancelled.", 2000L)
        }
    }

    /**
     * The Deep Edit (top tab, section) holding the only parameters [bankId]'s knobs may bind to:
     * Deck A SRC knobs -> Deck A SRC, Deck A FX knobs -> Deck A FX, Master FX knobs -> Master FX.
     * Null for banks that aren't section-scoped (Master, Transitions, FX Sends, Global).
     */
    fun sectionFor(bankId: String?): Pair<String, String>? = when (bankId) {
        MacroEngine.DECK_A -> "Deck A" to "SRC"
        MacroEngine.DECK_B -> "Deck B" to "SRC"
        MacroEngine.DECK_BG -> "Deck BG" to "SRC"
        MacroEngine.DECK_PV -> "Deck PV" to "SRC"
        MacroEngine.DECK_A_FX -> "Deck A" to "FX"
        MacroEngine.DECK_B_FX -> "Deck B" to "FX"
        MacroEngine.DECK_BG_FX -> "Deck BG" to "FX"
        MacroEngine.DECK_PV_FX -> "Deck PV" to "FX"
        MacroEngine.MASTER_FX -> "Mixer" to "FX"
        else -> null
    }

    /** True if a knob in [bankId] may bind to [parameterId] (see [sectionFor]). */
    fun acceptsTarget(bankId: String?, parameterId: String): Boolean {
        val (top, section) = sectionFor(bankId) ?: return true
        return when {
            top == "Mixer" -> parameterId.startsWith("Master/FX/")
            section == "FX" -> parameterId.startsWith("$top/FX/")
            else -> parameterId.startsWith("$top/") && !parameterId.startsWith("$top/FX/")
        }
    }

    /** Human-readable name for [bankId]'s section, e.g. "Deck B FX", for status messages. */
    private fun sectionLabel(bankId: String?): String {
        val (top, section) = sectionFor(bankId) ?: return "this bank"
        return if (top == "Mixer") "Master FX" else "$top $section"
    }

    /**
     * Call when the user navigates Deep Edit / MACROS / a Deck row's [SRC]/[FX] pill to ([topTab],
     * [subTab]). Leaving the armed knob's section disarms Learn, since the knob can't bind to
     * anything there anyway (see [acceptsTarget]).
     */
    fun onNavigateSection(topTab: String, subTab: String) {
        val session = activeSession ?: return
        val bankId = MacroEngine.findBankForControl(session.controlId)?.first
        val section = sectionFor(bankId) ?: return
        if (section != (topTab to subTab)) {
            activeSession = null
            setStatus("Learn cancelled: left ${sectionLabel(bankId)}.", 3000L)
        }
    }

    /** Returns true if Learn Mode is currently active, checking timeout. */
    fun isLearning(): Boolean {
        val session = activeSession ?: return false
        if (System.currentTimeMillis() - session.startTimeMs > TIMEOUT_MS) {
            activeSession = null
            setStatus("Learn Mode timed out.", 3000L)
            return false
        }
        return true
    }

    /** Returns true if the specified control ID is currently armed for Learn Mode. */
    fun isControlLearning(controlId: String): Boolean {
        return isLearning() && activeSession?.controlId == controlId
    }

    /**
     * Resolves a control from the specified bank or any registered bank in MacroEngine.
     */
    fun findControl(controlId: String, bank: MacroBank = MacroBank()): MacroControl? {
        val inBank = bank.knobs.find { it.id == controlId }
        if (inBank != null) return inBank
        val pair = MacroEngine.findBankForControl(controlId)
        return pair?.second?.knobs?.find { it.id == controlId }
    }

    /**
     * Attempts to establish a binding from the currently armed control to the specified target.
     * Automatically invalidates [MacroEngine] cache on success.
     *
     * @return true if binding was created, false otherwise.
     */
    fun bindTarget(
        bank: MacroBank,
        targetType: MacroTargetType,
        parameterId: String,
        unitInstanceId: String? = null,
        modulatorIndex: Int = 0,
        propertyName: String = "",
        minVal: Float = 0.0f,
        maxVal: Float = 1.0f,
        curve: MacroCurveType = MacroCurveType.LINEAR
    ): Boolean {
        val session = activeSession ?: return false
        val controlPair = MacroEngine.findBankForControl(session.controlId)
        val targetBank = controlPair?.second ?: bank
        // Deliberately *not* auto-filled from controlPair?.first: unitInstanceId here describes
        // the binding's target scope (null = resolved by full "Deck A/..." path via
        // ParameterResolver), which is independent of which bank the control being learned lives
        // in. Every Learn-mode call site passes null for the default global scope.
        // relative-path case), and the UI's "is this parameter locked?" queries
        // (MacroEngine.findBindingsTargeting/findPrimaryBindingInfo) match on that same value, so
        // silently substituting the control's bank id here would make freshly-created bindings
        // invisible to those queries.
        val targetUnitInstanceId = unitInstanceId

        val control = findControl(session.controlId, targetBank)
        if (control == null) {
            cancelLearn()
            return false
        }

        // Deck A SRC knobs bind only Deck A SRC parameters, Deck A FX knobs only Deck A FX, etc.
        // Learn stays armed so the user can just click a parameter in the right section.
        val controlBankId = controlPair?.first
        if (!acceptsTarget(controlBankId, parameterId)) {
            val ctrlName = control.label.ifEmpty { "This knob" }
            setStatus("Cannot bind: $ctrlName is a ${sectionLabel(controlBankId)} knob -- click a ${sectionLabel(controlBankId)} parameter.")
            return false
        }

        if (control.bindings.size >= MacroControl.MAX_BINDINGS_PER_CONTROL) {
            setStatus("Cannot bind: ${control.label.ifEmpty { "Control" }} reached max ${MacroControl.MAX_BINDINGS_PER_CONTROL} bindings limit.")
            activeSession = null
            return false
        }

        // Check if duplicate binding already exists
        val existing = control.bindings.find {
            it.unitInstanceId == targetUnitInstanceId &&
            it.parameterId == parameterId &&
            it.targetType == targetType &&
            (targetType != MacroTargetType.MODULATOR_PROPERTY || (it.modulatorIndex == modulatorIndex && it.propertyName == propertyName))
        }
        if (existing != null) {
            setStatus("Already bound: ${control.label.ifEmpty { "Control" }} -> $parameterId")
            activeSession = null
            return false
        }

        val newBinding = MacroBinding(
            unitInstanceId = targetUnitInstanceId,
            parameterId = parameterId,
            targetType = targetType,
            modulatorIndex = modulatorIndex,
            propertyName = propertyName,
            minVal = minVal,
            maxVal = maxVal,
            curve = curve,
            enabled = true
        )

        control.bindings.add(newBinding)
        MacroEngine.invalidate()
        activeSession = null

        val targetDesc = if (targetType == MacroTargetType.PARAM_BASE_VALUE) {
            parameterId
        } else {
            "$parameterId [$propertyName]"
        }
        val ctrlName = control.label.ifEmpty { "Knob" }
        setStatus("Bound $ctrlName -> $targetDesc", 4000L)
        return true
    }
}
