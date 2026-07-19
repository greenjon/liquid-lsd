package llm.slop.liquidlsd.ui

import imgui.ImGui
import llm.slop.liquidlsd.cv.CvHistoryBuffer
import llm.slop.liquidlsd.parameters.CvModulator
import llm.slop.liquidlsd.parameters.ModulatableParameter

object OscilloscopeDrawer {

    fun drawFinalOscilloscope(
        session: llm.slop.liquidlsd.SessionContext,
        history: CvHistoryBuffer,
        minVal: Float,
        maxVal: Float,
        themeColor: Int,
        modulators: List<CvModulator>,
        modulatorHistories: Map<String, CvHistoryBuffer>
    ) {
        val historySize = history.size
        val w = ImGui.getContentRegionAvailX()
        val h = 80f
        
        val startX = ImGui.getCursorScreenPosX()
        val startY = ImGui.getCursorScreenPosY()
        ImGui.dummy(w, h)
        
        val dl = ImGui.getWindowDrawList()
        val bgCol = ImGui.colorConvertFloat4ToU32(0.04f, 0.04f, 0.04f, 1.0f)
        dl.addRectFilled(startX, startY, startX + w, startY + h, bgCol, 4f)
        
        val centerY = startY + h / 2f
        val gridColCenter = ImGui.colorConvertFloat4ToU32(0.2f, 0.2f, 0.2f, 0.8f)
        val gridColFaint = ImGui.colorConvertFloat4ToU32(0.12f, 0.12f, 0.12f, 0.5f)
        
        dl.addLine(startX, centerY, startX + w, centerY, gridColCenter, 1.5f)
        dl.addLine(startX, startY + 5f, startX + w, startY + 5f, gridColFaint, 1f)
        dl.addLine(startX, startY + h - 5f, startX + w, startY + h - 5f, gridColFaint, 1f)
        
        val numDivisions = 4
        for (i in 1 until numDivisions) {
            val gridX = startX + (w * i / numDivisions)
            dl.addLine(gridX, startY, gridX, startY + h, gridColFaint, 1f)
        }
        
        val stepX = w / (historySize - 1)
        val usableHeight = h - 10f
        
        val range = maxVal - minVal
        val divisor = if (range == 0f) 1f else range
        
        // 1. Draw each active modulator's history line
        for (mod in modulators) {
            val hist = modulatorHistories[mod.id] ?: continue
            val colorId = if (mod.sourceId.startsWith("midi_cc_")) "midi" else mod.sourceId
            val modColor = CvTheme.getThemeColor(colorId, 0.6f) // Thinner and transparent background line
            
            for (i in 0 until historySize - 1) {
                val raw1 = hist.getAt(i)
                val raw2 = hist.getAt(i + 1)
                
                val val1 = if (range == 0f) 0.5f else ((raw1 - minVal) / divisor).coerceIn(0f, 1f)
                val val2 = if (range == 0f) 0.5f else ((raw2 - minVal) / divisor).coerceIn(0f, 1f)
                
                val x1 = startX + i * stepX
                val y1 = (startY + h - 5f) - val1 * usableHeight
                val x2 = startX + (i + 1) * stepX
                val y2 = (startY + h - 5f) - val2 * usableHeight
                
                dl.addLine(x1, y1, x2, y2, modColor, 1.25f)
            }
        }
        
        // 2. Draw final modulated line on top
        val lineCol = themeColor
        for (i in 0 until historySize - 1) {
            val raw1 = history.getAt(i)
            val raw2 = history.getAt(i + 1)
            
            val val1 = if (range == 0f) 0.5f else ((raw1 - minVal) / divisor).coerceIn(0f, 1f)
            val val2 = if (range == 0f) 0.5f else ((raw2 - minVal) / divisor).coerceIn(0f, 1f)
            
            val x1 = startX + i * stepX
            val y1 = (startY + h - 5f) - val1 * usableHeight
            val x2 = startX + (i + 1) * stepX
            val y2 = (startY + h - 5f) - val2 * usableHeight
            
            dl.addLine(x1, y1, x2, y2, lineCol, 2.25f)
        }
        
        val borderCol = ImGui.colorConvertFloat4ToU32(0.18f, 0.18f, 0.18f, 1.0f)
        dl.addRect(startX, startY, startX + w, startY + h, borderCol, 4f)
        
        ImGui.setCursorScreenPos(startX + 6f, startY + 4f)
        session.uiTheme.captionColored(0.5f, 0.5f, 0.5f, 0.6f, "%.2f".format(maxVal))
        ImGui.setCursorScreenPos(startX + 6f, centerY - 6f)
        session.uiTheme.captionColored(0.5f, 0.5f, 0.5f, 0.6f, "%.2f".format(minVal + range * 0.5f))
        ImGui.setCursorScreenPos(startX + 6f, startY + h - 16f)
        session.uiTheme.captionColored(0.5f, 0.5f, 0.5f, 0.6f, "%.2f".format(minVal))
        
        val textWidth = ImGui.calcTextSize("Final Parameter Value").x
        ImGui.setCursorScreenPos(startX + w - textWidth - 8f, startY + 4f)
        session.uiTheme.captionColored(0.5f, 0.5f, 0.5f, 0.6f, "Final Parameter Value")
        
        ImGui.setCursorScreenPos(startX, startY + h)
    }

    fun drawOscilloscope(
        session: llm.slop.liquidlsd.SessionContext,
        param: ModulatableParameter, themeColor: Int, activeHistory: CvHistoryBuffer?) {
        val history = activeHistory ?: return
        val historySize = history.size
        val w = ImGui.getContentRegionAvailX()
        val h = 80f // Height of the oscilloscope box
        
        val startX = ImGui.getCursorScreenPosX()
        val startY = ImGui.getCursorScreenPosY()
        
        // Reserve space
        ImGui.dummy(w, h)
        
        val dl = ImGui.getWindowDrawList()
        
        // 1. Background
        val bgCol = ImGui.colorConvertFloat4ToU32(0.04f, 0.04f, 0.04f, 1.0f)
        dl.addRectFilled(startX, startY, startX + w, startY + h, bgCol, 4f)
        
        val isBipolar = param.minClamp < 0f

        // 2. Grid lines
        val gridColCenter = ImGui.colorConvertFloat4ToU32(0.2f, 0.2f, 0.2f, 0.8f)
        val gridColFaint = ImGui.colorConvertFloat4ToU32(0.12f, 0.12f, 0.12f, 0.5f)

        if (isBipolar) {
            // Bipolar: center line represents zero
            val centerY = startY + h / 2f
            dl.addLine(startX, centerY, startX + w, centerY, gridColCenter, 1.5f)
        } else {
            // Monopolar: bottom line is the zero baseline
            dl.addLine(startX, startY + h - 5f, startX + w, startY + h - 5f, gridColCenter, 1.5f)
        }
        dl.addLine(startX, startY + 5f, startX + w, startY + 5f, gridColFaint, 1f)
        if (isBipolar) {
            dl.addLine(startX, startY + h - 5f, startX + w, startY + h - 5f, gridColFaint, 1f)
        }

        // Vertical lines
        val numDivisions = 4
        for (i in 1 until numDivisions) {
            val gridX = startX + (w * i / numDivisions)
            dl.addLine(gridX, startY, gridX, startY + h, gridColFaint, 1f)
        }

        // 3. Draw lines of history
        val stepX = w / (historySize - 1)
        val usableHeight = h - 10f

        val lineCol = themeColor

        for (i in 0 until historySize - 1) {
            val raw1 = history.getAt(i)
            val raw2 = history.getAt(i + 1)
            val norm1 = if (isBipolar) (raw1 + 1f) / 2f else raw1.coerceIn(0f, 1f)
            val norm2 = if (isBipolar) (raw2 + 1f) / 2f else raw2.coerceIn(0f, 1f)

            val x1 = startX + i * stepX
            val y1 = (startY + h - 5f) - norm1 * usableHeight
            val x2 = startX + (i + 1) * stepX
            val y2 = (startY + h - 5f) - norm2 * usableHeight

            dl.addLine(x1, y1, x2, y2, lineCol, 2.0f)
        }
        
        // 4. Border
        val borderCol = ImGui.colorConvertFloat4ToU32(0.18f, 0.18f, 0.18f, 1.0f)
        dl.addRect(startX, startY, startX + w, startY + h, borderCol, 4f)
        
        // 5. Y-Axis label markings (showing scaled bounds instead of hardcoded -1..1)
        val maxLabel = "%.2f".format(param.maxClamp)
        val midLabel = "%.2f".format(param.minClamp + (param.maxClamp - param.minClamp) / 2f)
        val minLabel = "%.2f".format(param.minClamp)
        
        ImGui.setCursorScreenPos(startX + 6f, startY + 4f)
        session.uiTheme.captionColored(0.5f, 0.5f, 0.5f, 0.6f, maxLabel)

        if (isBipolar) {
            val centerY = startY + h / 2f
            ImGui.setCursorScreenPos(startX + 6f, centerY - 6f)
            session.uiTheme.captionColored(0.5f, 0.5f, 0.5f, 0.6f, midLabel)
        }

        ImGui.setCursorScreenPos(startX + 6f, startY + h - 16f)
        session.uiTheme.captionColored(0.5f, 0.5f, 0.5f, 0.6f, minLabel)

        // Restore cursor position for downstream ImGui rendering
        ImGui.setCursorScreenPos(startX, startY + h)
    }
}
