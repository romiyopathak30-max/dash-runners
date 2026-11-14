package com.example.game

import com.example.data.model.RunnerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashWorldEngineTest {

  @Test
  fun swipeLeftAndRight_updatesTargetLaneWithinBounds() {
    val engine = DashWorldEngine()
    engine.startNewRun(RunnerProfile(hoverboardsCount = 3L)) {}

    assertEquals(0, engine.targetLane)
    engine.handleSwipe(SwipeDirection.LEFT)
    assertEquals(-1, engine.targetLane)
    engine.handleSwipe(SwipeDirection.RIGHT)
    assertEquals(0, engine.targetLane)
    engine.handleSwipe(SwipeDirection.RIGHT)
    assertEquals(1, engine.targetLane)
  }

  @Test
  fun jumpAndStep_liftsRunnerOffGroundAndLandsSafely() {
    val engine = DashWorldEngine()
    engine.startNewRun(RunnerProfile()) {}

    engine.handleSwipe(SwipeDirection.UP)
    engine.step(0.05f)
    assertTrue(engine.runnerY > 0.3f)
    assertFalse(engine.isGrounded)

    repeat(30) { engine.step(0.04f) }
    assertTrue(engine.isGrounded)
    assertEquals(0f, engine.runnerY, 0.01f)
  }

  @Test
  fun hoverboard_protectsAgainstFrontalObstacleCrash() {
    var gameOverCalled = false
    val engine = DashWorldEngine()
    engine.startNewRun(RunnerProfile(hoverboardsCount = 2L)) {
      gameOverCalled = true
    }

    engine.activateHoverboard()
    assertTrue(engine.hoverboardActive)
    assertEquals(1L, engine.hoverboardsAvailable)

    // Place a barrier directly in front of the runner in lane 0
    engine.obstacles.add(
      WorldObstacle(
        id = 999,
        kind = ObstacleKind.TALL_BARRIER,
        lane = 0,
        zCenter = -0.5f,
        length = 1.2f,
        height = 2.65f,
      )
    )

    engine.step(0.03f)
    assertFalse(gameOverCalled)
    assertEquals(GamePhase.PLAYING, engine.phase)
    assertFalse(engine.hoverboardActive)
    assertTrue(engine.invulnerableTimer > 0f)
  }

  @Test
  fun frontalCollisionWithoutHoverboard_triggersGameOver() {
    var summary: RunSummary? = null
    val engine = DashWorldEngine()
    engine.startNewRun(RunnerProfile(hoverboardsCount = 0L)) {
      summary = it
    }

    engine.obstacles.add(
      WorldObstacle(
        id = 1001,
        kind = ObstacleKind.TALL_BARRIER,
        lane = 0,
        zCenter = -0.3f,
        length = 1.2f,
        height = 2.65f,
      )
    )

    engine.step(0.02f)
    assertEquals(GamePhase.GAME_OVER, engine.phase)
    assertTrue(summary != null)
  }

  @Test
  fun swipeLeftRightJumpAndRoll_updatesRunnerPositionAndState() {
    val engine = DashWorldEngine()
    engine.startNewRun(RunnerProfile()) {}

    engine.handleSwipe(SwipeDirection.LEFT)
    repeat(10) { engine.step(0.03f) }
    assertEquals(-1, engine.targetLane)
    assertTrue(engine.runnerX < -1.5f)

    engine.handleSwipe(SwipeDirection.UP)
    engine.step(0.04f)
    assertFalse(engine.isGrounded)
    assertTrue(engine.runnerY > 0.2f)

    // Lane change mid-air works
    engine.handleSwipe(SwipeDirection.RIGHT)
    assertEquals(0, engine.targetLane)

    // Roll fast-falls back to ground
    engine.handleSwipe(SwipeDirection.DOWN)
    assertTrue(engine.isRolling)
    repeat(12) { engine.step(0.03f) }
    assertTrue(engine.runnerX > -0.6f)
  }

  @Test
  fun targetFps120AndEngineSnapshot_advancesSmoothlyWithoutHitches() {
    assertEquals(120, DashTextureView.TARGET_FPS)
    assertEquals(8_333_333L, DashTextureView.TARGET_FRAME_NS)

    val engine = DashWorldEngine()
    engine.startNewRun(RunnerProfile()) {}

    val obsSnap = ArrayList<WorldObstacle>(64)
    val coinSnap = ArrayList<WorldCoin>(128)
    val puSnap = ArrayList<WorldPowerUp>(16)
    val partSnap = ArrayList<WorldParticle>(64)

    val dt120 = 1f / 120f
    repeat(360) {
      engine.step(dt120)
      engine.copyRenderSnapshot(obsSnap, coinSnap, puSnap, partSnap)
    }

    assertEquals(3.0f, engine.elapsedTime, 0.02f)
    assertTrue(engine.worldDistance > 60f)
  }
}
