package lsd.liquid.visuals

import android.app.Presentation
import android.content.Context
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.PixelCopy
import android.view.WindowManager
import androidx.annotation.RequiresApi

/**
 * The visuals on a second display (USB-C/HDMI, Miracast, a simulated overlay): a [Presentation] holding its own
 * [LsdPlayer], rendered for that display. The phone keeps its screen for controls; [snapshot] gives it a cheap
 * preview. Show it from an Activity (`show()`), `dismiss()` it when the display goes or output stops.
 */
class LsdPresentation(
    outerContext: Context,
    display: Display,
    control: LsdControl?,
    onReady: () -> Unit = {},
    onFailed: () -> Unit = {},
    onState: ((LsdState) -> Unit)? = null,
) : Presentation(outerContext, display) {
    val player = LsdPlayer(context, control, onReady, onFailed, onState)
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(player.view)
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setCancelable(false)
    }

    override fun onStop() {
        player.destroy()
        super.onStop()
    }

    /**
     * The picture as it is on the display, scaled into a [width]-pixel-wide bitmap (aspect kept); null if it
     * isn't on screen yet. Asynchronous, on the main thread.
     */
    @RequiresApi(26)
    fun snapshot(width: Int, onResult: (Bitmap?) -> Unit) {
        val w = window
        val view = w?.decorView
        if (w == null || view == null || view.width == 0 || view.height == 0 || !isShowing) return onResult(null)
        val h = (width.toLong() * view.height / view.width).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
        try {
            PixelCopy.request(w, bmp, { r -> onResult(if (r == PixelCopy.SUCCESS) bmp else null) }, main)
        } catch (_: Exception) {
            onResult(null)
        }
    }

    companion object {
        /** Displays a presentation can go to (external, wireless, overlay), first one first. */
        fun displays(ctx: Context): List<Display> =
            ctx.getSystemService(DisplayManager::class.java).getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).toList()
    }
}
