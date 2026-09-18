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
