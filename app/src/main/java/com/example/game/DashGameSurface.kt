package com.example.game

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.DeepAsphalt
import kotlin.math.abs

@Composable
fun DashGameSurface(
  engine: DashWorldEngine,
  isPlaying: Boolean,
  onHudTick: (HudSnapshot) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val swipeThresholdPx = with(LocalDensity.current) { 11.dp.toPx() }
  val chainThresholdPx = with(LocalDensity.current) { 24.dp.toPx() }
  val mainHandler = remember { Handler(Looper.getMainLooper()) }
  val latestOnHudTick by rememberUpdatedState(onHudTick)
  var textureView by remember { mutableStateOf<DashTextureView?>(null) }
  var mountGlSurface by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    withFrameNanos { }
    mountGlSurface = true
  }

  DisposableEffect(lifecycleOwner, textureView) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> textureView?.onResume()
        Lifecycle.Event.ON_PAUSE -> {
          textureView?.onPause()
          if (engine.phase == GamePhase.PLAYING) {
            engine.pauseGame()
            latestOnHudTick(engine.hudSnapshot())
          }
        }
        else -> {}
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  Box(modifier = modifier.fillMaxSize().background(DeepAsphalt)) {
    if (mountGlSurface) {
      AndroidView(
        factory = {
          val renderer =
            DashGLRenderer(engine) { snapshot ->
              mainHandler.post { latestOnHudTick(snapshot) }
            }
          DashTextureView(context, renderer).also {
            textureView = it
          }
        },
        modifier = Modifier.fillMaxSize(),
      )
    }

    if (isPlaying) {
      Box(
        modifier =
          Modifier
            .fillMaxSize()
            .testTag("game_swipe_surface")
            .pointerInput(engine, swipeThresholdPx, chainThresholdPx) {
              var lastTapAtMs = 0L
              awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var anchorPos = down.position
                var lastDir: SwipeDirection? = null

                while (true) {
                  val event = awaitPointerEvent()
                  val change = event.changes.firstOrNull { it.id == down.id } ?: break
                  val dx = change.position.x - anchorPos.x
                  val dy = change.position.y - anchorPos.y
                  val activeThreshold = if (lastDir == null) swipeThresholdPx else chainThresholdPx

                  if (abs(dx) > activeThreshold || abs(dy) > activeThreshold) {
                    val dir =
                      if (abs(dx) > abs(dy)) {
                        if (dx > 0f) SwipeDirection.RIGHT else SwipeDirection.LEFT
                      } else {
                        if (dy > 0f) SwipeDirection.DOWN else SwipeDirection.UP
                      }
                    if (lastDir == null || dir != lastDir) {
                      engine.handleSwipe(dir)
                      lastDir = dir
                      change.consume()
                    }
                    anchorPos = change.position
                  }

                  if (!change.pressed) {
                    if (lastDir == null) {
                      val now = System.currentTimeMillis()
                      if (now - lastTapAtMs <= 300L) {
                        engine.activateHoverboard()
                        lastTapAtMs = 0L
                      } else {
                        lastTapAtMs = now
                      }
                    }
                    break
                  }
                }
              }
            }
      )
    }
  }
}
