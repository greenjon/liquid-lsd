package llm.slop.liquidlsd.ui

/**
 * Unicode mapping for Lucide icon font.
 *
 * NOTE: These constants map to the standard Lucide webfont PUA codepoints.
 */
object Icons {
    const val SETTINGS    = "\ue154" // settings
    const val PREFERENCES = SETTINGS // preferences
    const val POWER       = "\ue140" // power
    const val POWER_OFF   = "\ue209" // power-off
    const val TRASH       = "\ue18e" // trash-2
    const val DICES       = "\ue2c5" // dices
    const val FOLDER      = "\ue0d7" // folder
    const val FOLDER_PLUS = "\ue0d9" // folder-plus
    const val FILE        = "\ue0c0" // file
    const val FILE_PLUS   = "\ue0c9" // file-plus
    const val ACTIVITY    = "\ue038" // activity
    const val ZAP         = "\ue1b4" // zap
    // NOTE: Lucide's chevron-up (\ue070) and chevron-down (\ue06d) glyphs used to bake
    // corrupted (aliasing onto unrelated digit glyphs). Root cause: Inter-Regular.ttf
    // embeds ~760 stray glyphs of its own in the E000-F8FF PUA block (OpenType stylistic
    // alternates, e.g. "G.1"), which collided with Lucide's merged icon glyphs at the same
    // codepoints even though Inter is only ever requested for MAIN_RANGES. Fixed for the
    // whole icon set by stripping those PUA cmap entries from the shipped Inter TTFs (see
    // UITheme.loadFonts). Left as plain Unicode triangles rather than reverting to the
    // Lucide glyphs since they already work and match visually.
    const val CHEVRON_UP    = "\u25b2" // black up-pointing triangle
    const val CHEVRON_DOWN  = "\u25bc" // black down-pointing triangle
    const val SEARCH      = "\ue151" // search
    const val REFRESH     = "\ue145" // refresh-cw
    const val PLUS        = "\ue13d" // plus
    const val MINUS       = "\ue11c" // minus
    const val PLAY        = "\ue13c" // play
    const val PAUSE       = "\ue12e" // pause
    const val ALERT       = "\ue193" // alert-triangle
    const val INFO        = "\ue0f9" // info
    const val SAVE        = "\ue14d" // save
    const val DOWNLOAD    = "\ue0b2" // download
    const val DISC        = "\ue0af" // disc
    const val EJECT       = "\ue45d" // unplug
    const val UPLOAD      = "\ue19e" // upload / load
    const val LAYOUT_FULL = "\ue377" // rectangle-vertical
    const val LAYOUT_HALF = "\ue439" // rows-2
    const val LAYOUT_HIDE = "\ue42c" // panel-bottom
    const val PANEL_LEFT_OPEN = "\ue21d" // panel-left-open
    const val REPEAT      = "\ue146" // repeat
    const val SHUFFLE     = "\ue15e" // shuffle
    const val MORE_HORIZONTAL = "\ue0b6" // ellipsis
    const val MORE_VERTICAL   = "\ue0b7" // ellipsis-vertical
    const val NOTE            = "\ue1f9" // pencil
    const val LOCK            = "\ue10b" // lock
    const val UNLOCK          = "\ue10c" // lock-open / unlock
    const val VOLUME          = "\ue1a9" // volume
    const val VOLUME_X        = "\ue1ac" // volume-x
    const val BOT             = "\ue1bb" // bot
    const val BOT_OFF         = "\ue5e0" // bot-off
    const val X               = "\ue1b2" // x / close
    const val SQUARE          = "\ue167" // square / box / maximize
    const val COPY            = "\ue09e" // copy / restore
    const val MAXIMIZE        = "\ue112" // maximize
    const val MINIMIZE        = "\ue11a" // minimize

    // Wave Shapes
    const val WAVE_SINE   = "\ue38b" // spline
    const val WAVE_TRI    = "\ue192" // triangle
    const val WAVE_SQUARE = "\ue167" // square

    // Asymmetry alignments
    const val ALIGN_LEFT_LINE   = "\ue457" // arrow-left-to-line
    const val ALIGN_CENTER_LINE = "\ue43b" // fold-horizontal
    const val ALIGN_RIGHT_LINE  = "\ue459" // arrow-right-to-line
}

