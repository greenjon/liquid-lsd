package llm.slop.liquidlsd.ui

import imgui.ImGui
import mu.KotlinLogging
import org.lwjgl.glfw.GLFW.*

/**
 * Manages Client-Side Decorations (CSD) / Frameless Window interactions:
 * - Window dragging via header bar
 * - Double-click maximize / restore toggle
 * - Minimize, maximize/restore, and close actions
 * - Perimeter edge and corner resizing with cursor switching
 */
class WindowFrameController(
    val windowHandle: Long
) {
    private val logger = KotlinLogging.logger {}

    private val winXBuf = IntArray(1)
    private val winYBuf = IntArray(1)
    private val winWBuf = IntArray(1)
    private val winHBuf = IntArray(1)
    private val curXBuf = DoubleArray(1)
    private val curYBuf = DoubleArray(1)

    // Standard GLFW cursors
    private var arrowCursor: Long = 0L
    private var hResizeCursor: Long = 0L
    private var vResizeCursor: Long = 0L
    private var currentCursor: Long = 0L

    // Drag state
    private var isDragging = false
    private var dragStartCurX = 0.0
    private var dragStartCurY = 0.0

    // Resize state
    enum class ResizeEdge {
        NONE, TOP, BOTTOM, LEFT, RIGHT, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    private var activeResizeEdge = ResizeEdge.NONE
    private var resizeStartWinX = 0
    private var resizeStartWinY = 0
    private var resizeStartWinW = 0
    private var resizeStartWinH = 0
    private var resizeStartDesktopX = 0.0
    private var resizeStartDesktopY = 0.0

    init {
        try {
            arrowCursor = glfwCreateStandardCursor(GLFW_ARROW_CURSOR)
            hResizeCursor = glfwCreateStandardCursor(GLFW_HRESIZE_CURSOR)
            vResizeCursor = glfwCreateStandardCursor(GLFW_VRESIZE_CURSOR)
            currentCursor = arrowCursor
        } catch (e: Exception) {
            logger.warn(e) { "Failed to create standard GLFW cursors" }
        }
    }

    fun isMaximized(): Boolean = glfwGetWindowAttrib(windowHandle, GLFW_MAXIMIZED) == GLFW_TRUE

    fun minimize() {
        glfwIconifyWindow(windowHandle)
    }

    fun toggleMaximize() {
        if (isMaximized()) {
            glfwRestoreWindow(windowHandle)
        } else {
            glfwMaximizeWindow(windowHandle)
        }
    }

    fun getWindowPos(): Pair<Int, Int> {
        glfwGetWindowPos(windowHandle, winXBuf, winYBuf)
        return winXBuf[0] to winYBuf[0]
    }

    fun getWindowSize(): Pair<Int, Int> {
        glfwGetWindowSize(windowHandle, winWBuf, winHBuf)
        return winWBuf[0] to winHBuf[0]
    }

    fun getCursorPos(): Pair<Double, Double> {
        glfwGetCursorPos(windowHandle, curXBuf, curYBuf)
        return curXBuf[0] to curYBuf[0]
    }

    private fun setCursor(cursor: Long) {
        if (currentCursor != cursor && cursor != 0L) {
            glfwSetCursor(windowHandle, cursor)
            currentCursor = cursor
        }
    }

    /**
     * Called by the MenuBar when mouse interaction occurs over the empty header drag area.
     */
    fun onTopBarInteraction(isHovered: Boolean, isDoubleClicked: Boolean) {
        if (!UITheme.framelessWindow) return

        if (isDoubleClicked) {
            toggleMaximize()
            isDragging = false
            return
        }

        if (isHovered && ImGui.isMouseClicked(0) && activeResizeEdge == ResizeEdge.NONE) {
            val (winX, winY) = getWindowPos()
            val (curX, curY) = getCursorPos()
            if (isMaximized()) {
                val (winW, _) = getWindowSize()
                toggleMaximize()
                val (newW, _) = getWindowSize()
                val ratio = if (winW > 0) curX / winW.toDouble() else 0.5
                val newCurX = ratio * newW.toDouble()
                val newWinX = (winX + curX - newCurX).toInt()
                glfwSetWindowPos(windowHandle, newWinX, winY)
                dragStartCurX = newCurX
                dragStartCurY = curY
            } else {
                dragStartCurX = curX
                dragStartCurY = curY
            }
            isDragging = true
        }
    }

    /**
     * Called once per frame in UIManager.render to process window dragging and edge resizing.
     */
    fun update() {
        if (!UITheme.framelessWindow) {
            if (currentCursor != arrowCursor) {
                setCursor(arrowCursor)
            }
            return
        }

        // Handle title bar window dragging
        if (isDragging) {
            if (ImGui.isMouseDown(0)) {
                val (curX, curY) = getCursorPos()
                val deltaX = curX - dragStartCurX
                val deltaY = curY - dragStartCurY
                if (deltaX != 0.0 || deltaY != 0.0) {
                    val (currentWinX, currentWinY) = getWindowPos()
                    val targetX = (currentWinX + deltaX).toInt()
                    val targetY = (currentWinY + deltaY).toInt()
                    glfwSetWindowPos(windowHandle, targetX, targetY)
                }
            } else {
                isDragging = false
            }
            return
        }

        // Handle perimeter edge / corner resizing when unmaximized and not in clean mode
        if (!isMaximized() && !UITheme.cleanModeEnabled) {
            updateEdgeResizing()
        } else if (currentCursor != arrowCursor) {
            setCursor(arrowCursor)
        }
    }

    private fun updateEdgeResizing() {
        val (winW, winH) = getWindowSize()
        val (winX, winY) = getWindowPos()
        val (curX, curY) = getCursorPos()

        val margin = (6.0 * UITheme.systemDpiScale).coerceIn(4.0, 10.0)

        val insideWindow = curX >= 0.0 && curX <= winW.toDouble() && curY >= 0.0 && curY <= winH.toDouble()

        if (activeResizeEdge == ResizeEdge.NONE) {
            if (!insideWindow || ImGui.isAnyItemActive()) {
                if (currentCursor != arrowCursor) setCursor(arrowCursor)
                return
            }

            val atLeft = curX in 0.0..margin
            val atRight = curX in (winW.toDouble() - margin)..winW.toDouble()
            val atTop = curY in 0.0..margin
            val atBottom = curY in (winH.toDouble() - margin)..winH.toDouble()

            val detectedEdge = when {
                atTop && atLeft -> ResizeEdge.TOP_LEFT
                atTop && atRight -> ResizeEdge.TOP_RIGHT
                atBottom && atLeft -> ResizeEdge.BOTTOM_LEFT
                atBottom && atRight -> ResizeEdge.BOTTOM_RIGHT
                atLeft -> ResizeEdge.LEFT
                atRight -> ResizeEdge.RIGHT
                atTop -> ResizeEdge.TOP
                atBottom -> ResizeEdge.BOTTOM
                else -> ResizeEdge.NONE
            }

            when (detectedEdge) {
                ResizeEdge.LEFT, ResizeEdge.RIGHT -> setCursor(hResizeCursor)
                ResizeEdge.TOP, ResizeEdge.BOTTOM -> setCursor(vResizeCursor)
                ResizeEdge.TOP_LEFT, ResizeEdge.BOTTOM_RIGHT -> setCursor(vResizeCursor) // or diagonal
                ResizeEdge.TOP_RIGHT, ResizeEdge.BOTTOM_LEFT -> setCursor(hResizeCursor) // or diagonal
                ResizeEdge.NONE -> if (currentCursor != arrowCursor) setCursor(arrowCursor)
            }

            if (detectedEdge != ResizeEdge.NONE && ImGui.isMouseClicked(0)) {
                activeResizeEdge = detectedEdge
                resizeStartWinX = winX
                resizeStartWinY = winY
                resizeStartWinW = winW
                resizeStartWinH = winH
                resizeStartDesktopX = winX + curX
                resizeStartDesktopY = winY + curY
            }
        } else {
            // Actively resizing
            if (ImGui.isMouseDown(0)) {
                val currentDesktopX = winX + curX
                val currentDesktopY = winY + curY
                val dx = (currentDesktopX - resizeStartDesktopX).toInt()
                val dy = (currentDesktopY - resizeStartDesktopY).toInt()

                var newX = resizeStartWinX
                var newY = resizeStartWinY
                var newW = resizeStartWinW
                var newH = resizeStartWinH

                val minW = 800
                val minH = 600

                when (activeResizeEdge) {
                    ResizeEdge.RIGHT -> {
                        newW = (resizeStartWinW + dx).coerceAtLeast(minW)
                    }
                    ResizeEdge.BOTTOM -> {
                        newH = (resizeStartWinH + dy).coerceAtLeast(minH)
                    }
                    ResizeEdge.LEFT -> {
                        val maxDelta = resizeStartWinW - minW
                        val clampedDx = dx.coerceAtMost(maxDelta)
                        newX = resizeStartWinX + clampedDx
                        newW = resizeStartWinW - clampedDx
                    }
                    ResizeEdge.TOP -> {
                        val maxDelta = resizeStartWinH - minH
                        val clampedDy = dy.coerceAtMost(maxDelta)
                        newY = resizeStartWinY + clampedDy
                        newH = resizeStartWinH - clampedDy
                    }
                    ResizeEdge.TOP_LEFT -> {
                        val maxDx = resizeStartWinW - minW
                        val clampedDx = dx.coerceAtMost(maxDx)
                        newX = resizeStartWinX + clampedDx
                        newW = resizeStartWinW - clampedDx

                        val maxDy = resizeStartWinH - minH
                        val clampedDy = dy.coerceAtMost(maxDy)
                        newY = resizeStartWinY + clampedDy
                        newH = resizeStartWinH - clampedDy
                    }
                    ResizeEdge.TOP_RIGHT -> {
                        newW = (resizeStartWinW + dx).coerceAtLeast(minW)

                        val maxDy = resizeStartWinH - minH
                        val clampedDy = dy.coerceAtMost(maxDy)
                        newY = resizeStartWinY + clampedDy
                        newH = resizeStartWinH - clampedDy
                    }
                    ResizeEdge.BOTTOM_LEFT -> {
                        val maxDx = resizeStartWinW - minW
                        val clampedDx = dx.coerceAtMost(maxDx)
                        newX = resizeStartWinX + clampedDx
                        newW = resizeStartWinW - clampedDx

                        newH = (resizeStartWinH + dy).coerceAtLeast(minH)
                    }
                    ResizeEdge.BOTTOM_RIGHT -> {
                        newW = (resizeStartWinW + dx).coerceAtLeast(minW)
                        newH = (resizeStartWinH + dy).coerceAtLeast(minH)
                    }
                    ResizeEdge.NONE -> {}
                }

                if (newX != winX || newY != winY) {
                    glfwSetWindowPos(windowHandle, newX, newY)
                }
                if (newW != winW || newH != winH) {
                    glfwSetWindowSize(windowHandle, newW, newH)
                }
            } else {
                activeResizeEdge = ResizeEdge.NONE
                setCursor(arrowCursor)
            }
        }
    }

    fun destroy() {
        try {
            if (arrowCursor != 0L) glfwDestroyCursor(arrowCursor)
            if (hResizeCursor != 0L) glfwDestroyCursor(hResizeCursor)
            if (vResizeCursor != 0L) glfwDestroyCursor(vResizeCursor)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to destroy GLFW cursors" }
        }
    }
}
