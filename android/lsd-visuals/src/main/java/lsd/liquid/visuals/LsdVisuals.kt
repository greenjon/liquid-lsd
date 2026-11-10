package lsd.liquid.visuals

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * The Liquid LSD visuals as a Compose element: an [LsdPlayer] that draws and listens while [active] and the app is
 * on screen. The app's audio reaches the page through [PcmTap], held back by [VisualsSettings.syncMs]. The view
 * takes no touches. [onFailed] fires if the picture can't start (no WebView, no WebGL2, repeated renderer crashes,
 * or no sign of life within 20 s), so the caller can show something else. [control] (optional) drives the scenes,
 * effects and backgrounds; [onState] (optional) receives what is on air and how the controls are set whenever that
 * changes; [onReady] fires each time a page is up (the first, and after a renderer restart).
 */
@Composable
fun LsdVisuals(
    settings: VisualsSettings,
    active: Boolean,
    onFailed: () -> Unit,
    modifier: Modifier = Modifier,
    control: LsdControl? = null,
    onState: ((LsdState) -> Unit)? = null,
    onReady: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnFailed by rememberUpdatedState(onFailed)
    val currentOnState by rememberUpdatedState(onState)
    val currentOnReady by rememberUpdatedState(onReady)
    val wantsState = onState != null
    // Plain holders, not Compose state: a stopped activity gets no recomposition, so the lifecycle observer
    // applies the change itself.
    val gate = remember { ActiveGate(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    val powerSave = rememberPowerSave(context)

    val player = remember(context, control) {
        LsdPlayer(
            context, control,
            onReady = { currentOnReady?.invoke() },
            onFailed = { currentOnFailed() },
            onState = if (wantsState) { s -> currentOnState?.invoke(s) } else null,
        )
    }
    DisposableEffect(player) { onDispose { player.destroy() } }

    DisposableEffect(lifecycle, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) { gate.visible = true; player.active = gate.wanted && gate.visible }
            if (event == Lifecycle.Event.ON_STOP) { gate.visible = false; player.active = false }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    SideEffect {
        player.settings = settings
        player.powerSave = powerSave
        gate.wanted = active
        player.active = active && gate.visible
    }

    AndroidView(modifier = modifier, factory = { player.view })
}

@Composable
private fun rememberPowerSave(context: Context): Boolean {
    val pm = remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    var saving by remember { mutableStateOf(pm.isPowerSaveMode) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { saving = pm.isPowerSaveMode }
        }
        context.registerReceiver(receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }
    return saving
}

private class ActiveGate(var visible: Boolean) {
    var wanted = false
}
