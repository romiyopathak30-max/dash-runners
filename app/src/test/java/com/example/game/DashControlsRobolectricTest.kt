package com.example.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import com.example.data.model.RunnerProfile
import com.example.ui.screens.GameHudOverlay
import com.example.ui.theme.DashRunnersTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DashControlsRobolectricTest {

  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun dashGameSurface_mountsSwipeOverlayOnPlayTransition_andRespondsToSwipes() {
    val engine = DashWorldEngine()
    var isPlaying by mutableStateOf(false)

    composeRule.setContent {
      DashRunnersTheme {
        DashGameSurface(
          engine = engine,
          isPlaying = isPlaying,
          onHudTick = {},
        )
      }
    }

    composeRule.runOnUiThread {
      engine.startNewRun(RunnerProfile(hoverboardsCount = 2L)) {}
      isPlaying = true
    }
    composeRule.waitForIdle()

    val surface = composeRule.onNodeWithTag("game_swipe_surface", useUnmergedTree = true)
    surface.performTouchInput { swipeLeft() }
    assertEquals(-1, engine.targetLane)

    surface.performTouchInput { swipeRight() }
    assertEquals(0, engine.targetLane)

    surface.performTouchInput { swipeUp() }
    engine.step(0.04f)
    assertFalse(engine.isGrounded)

    surface.performTouchInput { swipeDown() }
    assertTrue(engine.isRolling)
  }

  @Test
  fun dashGameSurface_supportsContinuousChainedFingerDragWithoutLifting() {
    val engine = DashWorldEngine()
    composeRule.setContent {
      DashRunnersTheme {
        DashGameSurface(
          engine = engine,
          isPlaying = true,
          onHudTick = {},
        )
      }
    }
    composeRule.runOnUiThread {
      engine.startNewRun(RunnerProfile()) {}
    }
    composeRule.waitForIdle()

    val surface = composeRule.onNodeWithTag("game_swipe_surface", useUnmergedTree = true)
    surface.performTouchInput {
      down(center)
      moveBy(Offset(-100f, 0f))
    }
    assertEquals(-1, engine.targetLane)

    surface.performTouchInput {
      moveBy(Offset(0f, -120f))
    }
    assertFalse(engine.isGrounded)

    surface.performTouchInput {
      moveBy(Offset(140f, 0f))
      up()
    }
    assertEquals(0, engine.targetLane)
  }

  @Test
  fun gameHudOverlay_showsOnlyPauseAndHoverboardWithoutQuickPadButtons() {
    val engine = DashWorldEngine()
    engine.startNewRun(RunnerProfile()) {}

    composeRule.setContent {
      DashRunnersTheme {
        GameHudOverlay(
          hud = engine.hudSnapshot(),
          onPauseClick = {},
          onHoverboardClick = {},
        )
      }
    }

    composeRule.onNodeWithTag("pause_game_button").assertExists()
    composeRule.onNodeWithTag("activate_hoverboard_button").assertExists()
    composeRule.onNodeWithTag("quick_left_button").assertDoesNotExist()
    composeRule.onNodeWithTag("quick_right_button").assertDoesNotExist()
    composeRule.onNodeWithTag("quick_up_button").assertDoesNotExist()
    composeRule.onNodeWithTag("quick_down_button").assertDoesNotExist()
  }
}
