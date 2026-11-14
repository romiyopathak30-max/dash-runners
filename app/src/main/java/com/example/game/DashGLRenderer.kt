package com.example.game

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import com.example.data.model.HoverboardModel
import com.example.data.model.RunnerCatalog
import com.example.data.model.RunnerCharacter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

class DashGLRenderer(
  private val engine: DashWorldEngine,
  private val onHudTick: (HudSnapshot) -> Unit,
) : GLSurfaceView.Renderer {

  private var programId = 0
  private var skyProgramId = 0

  private var aPosLoc = 0
  private var aNormLoc = 0
  private var uMvpLoc = 0
  private var uModelLoc = 0
  private var uColorLoc = 0
  private var uEmissiveLoc = 0
  private var uSpecularLoc = 0
  private var uSunDirLoc = 0
  private var uCamPosLoc = 0
  private var uVertCamPosLoc = 0
  private var uFogColorLoc = 0
  private var uFogDensityLoc = 0

  private var aSkyPosLoc = 0
  private var aSkyColorLoc = 0

  // GPU-resident VBO handles (uploaded once in onSurfaceCreated; zero per-frame CPU copies)
  private var cubeVbo = 0
  private var cubeVertCount = 0
  private var sphereVbo = 0
  private var sphereVertCount = 0
  private var cylinderVbo = 0
  private var cylinderVertCount = 0
  private var coneVbo = 0
  private var coneVertCount = 0
  private var octaVbo = 0
  private var octaVertCount = 0
  private var tieChunkVbo = 0
  private var tieChunkVertCount = 0
  private var railsVbo = 0
  private var railsVertCount = 0
  private var platformsVbo = 0
  private var platformsVertCount = 0
  private var curbsVbo = 0
  private var curbsVertCount = 0
  private var outerGroundVbo = 0
  private var outerGroundVertCount = 0
  private var rampVbo = 0
  private var rampVertCount = 0
  private var coinVbo = 0
  private var coinVertCount = 0
  private var discVbo = 0
  private var discVertCount = 0
  private var skyVbo = 0

  // Batched repeating cityscape/subway corridor VBOs (11 segments = 363m window, 132m period)
  private var envPillarVbo = 0
  private var envPillarVertCount = 0
  private var envNeonVbo = 0
  private var envNeonVertCount = 0
  private var envLampPoleVbo = 0
  private var envLampPoleVertCount = 0
  private var envLampOrbVbo = 0
  private var envLampOrbVertCount = 0
  private var envTreeTrunkVbo = 0
  private var envTreeTrunkVertCount = 0
  private var envTreeCanopyVbo = 0
  private var envTreeCanopyVertCount = 0
  private var envBuildingDarkVbo = 0
  private var envBuildingDarkVertCount = 0
  private var envBuildingMidVbo = 0
  private var envBuildingMidVertCount = 0
  private var envCyanWinVbo = 0
  private var envCyanWinVertCount = 0
  private var envGoldWinVbo = 0
  private var envGoldWinVertCount = 0
  private var envBeaconVbo = 0
  private var envBeaconVertCount = 0

  private val projMatrix = FloatArray(16)
  private val viewMatrix = FloatArray(16)
  private val vpMatrix = FloatArray(16)
  private val modelMatrix = FloatArray(16)
  private val mvpMatrix = FloatArray(16)
  private val runnerRootMatrix = FloatArray(16)
  private val limbMatrix = FloatArray(16)
  private val subLimbMatrix = FloatArray(16)

  // Reusable snapshot buffers (zero allocation during frames)
  private val renderObstacles = ArrayList<WorldObstacle>(64)
  private val renderCoins = ArrayList<WorldCoin>(128)
  private val renderPowerUps = ArrayList<WorldPowerUp>(16)
  private val renderParticles = ArrayList<WorldParticle>(64)

  private var cachedRunnerId: String = ""
  private var cachedRunnerDef: RunnerCharacter = RunnerCatalog.runnerById("blaze")
  private var cachedBoardId: String = ""
  private var cachedBoardDef: HoverboardModel = RunnerCatalog.boardById("aero")

  private var camX = 0f
  private var camY = 4.5f
  private var camZ = 7.4f
  private var camFov = 50f
  private var animRollDip = 0f
  private var aspectRatio = 0.56f
  private var lastFrameNanos = 0L
  private var smoothedDt = 1f / 120f
  private var hudAccumSec = 0f
  private var lastReportedPhase: GamePhase? = null
  private var lastBoundVbo = 0

  // Smooth articulated pose state (interpolated per-frame for fluid 120 FPS motion)
  private var poseInitialized = false
  private var wasGroundedPrev = true
  private var landingSquash = 0f
  private var animHipOffset = 0.94f
  private var animBodyPitch = -13f
  private var animLeftShoulder = 0f
  private var animRightShoulder = 0f
  private var animLeftElbow = 78f
  private var animRightElbow = 78f
  private var animLeftHip = 0f
  private var animRightHip = 0f
  private var animLeftKnee = -18f
  private var animRightKnee = -18f

  override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
    GLES20.glClearColor(0.06f, 0.09f, 0.19f, 1.0f)
    GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    GLES20.glEnable(GLES20.GL_CULL_FACE)
    GLES20.glCullFace(GLES20.GL_BACK)
    GLES20.glDepthMask(true)
    GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

    programId = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
    aPosLoc = GLES20.glGetAttribLocation(programId, "aPosition")
    aNormLoc = GLES20.glGetAttribLocation(programId, "aNormal")
    uMvpLoc = GLES20.glGetUniformLocation(programId, "uMVPMatrix")
    uModelLoc = GLES20.glGetUniformLocation(programId, "uModelMatrix")
    uColorLoc = GLES20.glGetUniformLocation(programId, "uColor")
    uEmissiveLoc = GLES20.glGetUniformLocation(programId, "uEmissive")
    uSpecularLoc = GLES20.glGetUniformLocation(programId, "uSpecular")
    uSunDirLoc = GLES20.glGetUniformLocation(programId, "uSunDir")
    uCamPosLoc = GLES20.glGetUniformLocation(programId, "uCamPos")
    uVertCamPosLoc = GLES20.glGetUniformLocation(programId, "uVertCamPos")
    uFogColorLoc = GLES20.glGetUniformLocation(programId, "uFogColor")
    uFogDensityLoc = GLES20.glGetUniformLocation(programId, "uFogDensity")

    skyProgramId = buildProgram(SKY_VERTEX_SHADER, SKY_FRAGMENT_SHADER)
    aSkyPosLoc = GLES20.glGetAttribLocation(skyProgramId, "aPosition")
    aSkyColorLoc = GLES20.glGetAttribLocation(skyProgramId, "aColor")

    buildGeometryVbos()
    lastFrameNanos = System.nanoTime()
  }

  override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
    GLES20.glViewport(0, 0, width, height)
    aspectRatio = width.toFloat() / max(1, height).toFloat()
  }

  override fun onDrawFrame(gl: GL10?) {
    val now = System.nanoTime()
    val rawDt =
      if (lastFrameNanos == 0L) 1f / 120f
      else ((now - lastFrameNanos) / 1_000_000_000f).coerceIn(0.0005f, 0.033f)
    lastFrameNanos = now
    smoothedDt =
      if (abs(rawDt - smoothedDt) > 0.015f) rawDt
      else smoothedDt + (rawDt - smoothedDt) * 0.28f
    val dt = smoothedDt

    engine.step(dt)
    engine.copyRenderSnapshot(renderObstacles, renderCoins, renderPowerUps, renderParticles)

    hudAccumSec += rawDt
    val currentPhase = engine.phase
    if (currentPhase != lastReportedPhase || (currentPhase != GamePhase.LOBBY && hudAccumSec >= 0.08f)) {
      hudAccumSec = 0f
      lastReportedPhase = currentPhase
      onHudTick(engine.hudSnapshot())
    }

    GLES20.glDepthMask(true)
    GLES20.glDisable(GLES20.GL_BLEND)
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

    // 1. Sunset/twilight gradient backdrop
    drawSkyBackdrop()

    // 2. Framerate-independent exponential camera smoothing (silky at 120 FPS)
    val inLobby = currentPhase == GamePhase.LOBBY
    val lobbyOrbit = if (inLobby) sin(engine.elapsedTime * 0.6f) * 0.65f else 0f
    val targetRollDip = if (engine.isRolling) -0.50f else 0f
    animRollDip += (targetRollDip - animRollDip) * (1f - exp(-18f * dt))
    val vyAnticipate = (engine.runnerVy * 0.022f).coerceIn(-0.24f, 0.24f)
    val targetCamX = if (inLobby) 2.15f + lobbyOrbit else engine.runnerX * 0.64f
    val targetCamY = if (inLobby) 2.85f else 4.45f + engine.runnerY * 0.72f + animRollDip + vyAnticipate
    val targetCamZ = if (inLobby) 5.25f else 7.35f + (engine.worldSpeed - DashWorldEngine.BASE_SPEED) * 0.038f

    camX += (targetCamX - camX) * (1f - exp(-11f * dt))
    camY += (targetCamY - camY) * (1f - exp(-10f * dt))
    camZ += (targetCamZ - camZ) * (1f - exp(-8f * dt))

    val shake = engine.cameraShake
    val shakeX = if (shake > 0f) sin(engine.elapsedTime * 55f) * shake * 0.24f else 0f
    val shakeY = if (shake > 0f) cos(engine.elapsedTime * 62f) * shake * 0.20f else 0f

    val targetFov =
      if (inLobby) 46f
      else (54f + (engine.worldSpeed - DashWorldEngine.BASE_SPEED) * 0.20f + (if (engine.jetpackRemaining > 0f) 6f else 0f)).coerceIn(46f, 68f)
    camFov += (targetFov - camFov) * (1f - exp(-7f * dt))

    Matrix.perspectiveM(projMatrix, 0, camFov, aspectRatio, 0.5f, 220f)

    val lookX = if (inLobby) 0f else engine.runnerX * 0.45f
    val lookY = if (inLobby) 1.38f else 1.65f + engine.runnerY * 0.68f + animRollDip * 0.35f
    val lookZ = if (inLobby) -2.8f else -14f
    Matrix.setLookAtM(
      viewMatrix,
      0,
      camX + shakeX,
      camY + shakeY,
      camZ,
      lookX,
      lookY,
      lookZ,
      -engine.laneTiltDeg * 0.0022f,
      1f,
      0f,
    )
    Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0)

    GLES20.glUseProgram(programId)
    GLES20.glEnableVertexAttribArray(aPosLoc)
    GLES20.glEnableVertexAttribArray(aNormLoc)
    lastBoundVbo = 0
    GLES20.glUniform3f(uSunDirLoc, -0.45f, 0.78f, 0.42f)
    GLES20.glUniform3f(uCamPosLoc, camX, camY, camZ)
    GLES20.glUniform3f(uVertCamPosLoc, camX, camY, camZ)
    GLES20.glUniform3f(uFogColorLoc, 0.21f, 0.24f, 0.42f)
    GLES20.glUniform1f(uFogDensityLoc, 0.0078f)

    // 3. Opaque pass: Subway tracks, platforms, lamps, trees & multi-tier skyline
    drawSubwayEnvironment()

    // 4. Opaque pass: Obstacles (rounded 3D trains, ramps, barriers)
    for (i in 0 until renderObstacles.size) {
      val obs = renderObstacles[i]
      if (obs.zFar > 16f || obs.zNear < -180f) continue
      when (obs.kind) {
        ObstacleKind.PARKED_TRAIN -> drawTrain(obs.xCenter, obs.zCenter, obs.length, obs.colorVariant, isMoving = false, nearDetail = obs.zNear > -95f)
        ObstacleKind.MOVING_TRAIN -> drawTrain(obs.xCenter, obs.zCenter, obs.length, obs.colorVariant, isMoving = true, nearDetail = obs.zNear > -95f)
        ObstacleKind.TRAIN_RAMP -> drawRamp(obs.xCenter, obs.zCenter, obs.length, obs.height)
        ObstacleKind.LOW_BARRIER -> drawLowBarrier(obs.xCenter, obs.zCenter)
        ObstacleKind.HIGH_BARRIER -> drawHighBarrier(obs.xCenter, obs.zCenter)
        ObstacleKind.TALL_BARRIER -> drawTallBarrier(obs.xCenter, obs.zCenter)
      }
    }

    // 5. Opaque pass: Coins & Power-Ups
    val spinDeg = (engine.elapsedTime * 220f) % 360f
    for (i in 0 until renderCoins.size) {
      val coin = renderCoins[i]
      if (coin.collected || coin.z > 14f || coin.z < -165f) continue
      val bob = sin(engine.elapsedTime * 5f + coin.id) * 0.09f
      drawCoin(coin.x, coin.y + bob, coin.z, spinDeg)
    }

    for (i in 0 until renderPowerUps.size) {
      val pu = renderPowerUps[i]
      if (pu.collected || pu.z > 14f || pu.z < -165f) continue
      val bob = sin(engine.elapsedTime * 4.2f + pu.id) * 0.14f
      drawPowerUpToken(pu.x, pu.y + bob, pu.z, pu.kind, spinDeg * 0.75f)
    }

    // 6. Opaque pass: Articulated Chaser Guard & Articulated 3D Runner Character
    val showGuard = currentPhase == GamePhase.PLAYING && engine.chaserDangerTimer > 0f && engine.jetpackRemaining <= 0f
    val guardZ = if (engine.chaserDangerTimer > 4.0f) 2.2f else 3.4f + (3.2f - engine.chaserDangerTimer).coerceAtLeast(0f) * 1.4f
    if (showGuard) {
      drawChaserGuard(engine.runnerX * 0.88f, 0f, guardZ, engine.sprintCycle * 1.05f)
    }
    drawRunnerRig(dt)

    // 7. Translucent pass (GL_BLEND on, depth writes off — zero opaque framebuffer read-modify-write!)
    GLES20.glEnable(GLES20.GL_BLEND)
    GLES20.glDepthMask(false)

    // Runner & guard drop shadows
    val runnerFloorY = if (inLobby) 0f else engine.currentSupportFloorY()
    val shadowScale = (1f - max(0f, engine.runnerY - runnerFloorY) * 0.11f).coerceIn(0.32f, 1f)
    drawDisc(engine.runnerX, runnerFloorY + 0.03f, 0f, 0.64f * shadowScale, 0.02f, 0.03f, 0.06f, 0.50f * shadowScale)
    if (showGuard) {
      drawDisc(engine.runnerX * 0.88f, 0.03f, guardZ, 0.62f, 0.02f, 0.03f, 0.06f, 0.42f)
      drawCone(engine.runnerX * 0.88f + 0.36f, 1.15f, guardZ - 1.55f, 0.80f, 2.0f, 0.80f, 90f, 0f, 0f, 1.0f, 0.94f, 0.55f, 0.16f, 0.95f, 0f)
    }

    // Hoverboard underglow strip
    if (engine.hoverboardActive) {
      val boardDef = getBoardDef(engine.selectedBoardId)
      val hoverBob = sin(engine.elapsedTime * 8f) * 0.06f
      val boardY = engine.runnerY + 0.15f + hoverBob
      val tiltZ = -engine.laneTiltDeg * 1.15f
      drawCube(engine.runnerX, boardY - 0.06f, 0.15f, 0.74f, 0.05f, 1.45f, 0f, engine.laneYawDeg, tiltZ, boardDef.glowRgb[0], boardDef.glowRgb[1], boardDef.glowRgb[2], 0.88f, 0.92f, 0.2f)
    }

    // Coin/PowerUp ground shadows + Moving Express headlight beams (nearby only)
    for (i in 0 until renderCoins.size) {
      val coin = renderCoins[i]
      if (coin.collected || coin.z > 12f || coin.z < -45f) continue
      drawDisc(coin.x, 0.03f, coin.z, 0.38f, 0.02f, 0.03f, 0.06f, 0.32f)
    }
    for (i in 0 until renderObstacles.size) {
      val obs = renderObstacles[i]
      if (obs.kind == ObstacleKind.MOVING_TRAIN && obs.zNear > -85f && obs.zFar < 14f) {
        val frontZ = obs.zCenter + obs.length * 0.5f + 0.03f
        drawCone(obs.xCenter - 0.64f, 0.96f, frontZ + 1.8f, 1.05f, 3.4f, 1.05f, -96f, 0f, 0f, 1.0f, 0.95f, 0.70f, 0.14f, 1.0f, 0f)
        drawCone(obs.xCenter + 0.64f, 0.96f, frontZ + 1.8f, 1.05f, 3.4f, 1.05f, -96f, 0f, 0f, 1.0f, 0.95f, 0.70f, 0.14f, 1.0f, 0f)
      }
    }

    // Fast lightweight 8-triangle octahedron particles
    for (i in 0 until renderParticles.size) {
      val p = renderParticles[i]
      val alpha = (p.life / p.maxLife).coerceIn(0f, 1f)
      val sz = p.size * alpha
      drawPrimitive(octaVbo, octaVertCount, p.x, p.y, p.z, sz, sz, sz, 0f, 0f, 0f, p.r, p.g, p.b, alpha, emissive = 0.75f, specular = 0.2f)
    }

    GLES20.glDepthMask(true)
    GLES20.glDisableVertexAttribArray(aPosLoc)
    GLES20.glDisableVertexAttribArray(aNormLoc)
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
  }

  private fun getRunnerDef(id: String): RunnerCharacter {
    if (id != cachedRunnerId) {
      cachedRunnerId = id
      cachedRunnerDef = RunnerCatalog.runnerById(id)
    }
    return cachedRunnerDef
  }

  private fun getBoardDef(id: String): HoverboardModel {
    if (id != cachedBoardId) {
      cachedBoardId = id
      cachedBoardDef = RunnerCatalog.boardById(id)
    }
    return cachedBoardDef
  }

  private fun drawSkyBackdrop() {
    GLES20.glDisable(GLES20.GL_DEPTH_TEST)
    GLES20.glDisable(GLES20.GL_CULL_FACE)
    GLES20.glUseProgram(skyProgramId)
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, skyVbo)
    GLES20.glEnableVertexAttribArray(aSkyPosLoc)
    GLES20.glVertexAttribPointer(aSkyPosLoc, 2, GLES20.GL_FLOAT, false, 6 * 4, 0)
    GLES20.glEnableVertexAttribArray(aSkyColorLoc)
    GLES20.glVertexAttribPointer(aSkyColorLoc, 4, GLES20.GL_FLOAT, false, 6 * 4, 2 * 4)
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    GLES20.glDisableVertexAttribArray(aSkyPosLoc)
    GLES20.glDisableVertexAttribArray(aSkyColorLoc)
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    GLES20.glEnable(GLES20.GL_CULL_FACE)
    GLES20.glEnable(GLES20.GL_DEPTH_TEST)
  }

  private fun drawSubwayEnvironment() {
    // Batched static ground, outer slabs, platforms, curbs & 6 gleaming steel rails (5 draws total vs 13)
    drawCube(0f, -0.26f, -90f, 12.2f, 0.5f, 210f, 0f, 0f, 0f, 0.14f, 0.17f, 0.25f, 1f, 0f, 0.05f)
    Matrix.setIdentityM(modelMatrix, 0)
    System.arraycopy(vpMatrix, 0, mvpMatrix, 0, 16)
    drawBuffer(outerGroundVbo, outerGroundVertCount, 0.09f, 0.11f, 0.19f, 1f, emissive = 0f, specular = 0.05f)
    drawBuffer(platformsVbo, platformsVertCount, 0.24f, 0.28f, 0.37f, 1f, emissive = 0f, specular = 0.14f)
    drawBuffer(curbsVbo, curbsVertCount, 0.98f, 0.78f, 0.12f, 1f, emissive = 0.18f, specular = 0.25f)
    drawBuffer(railsVbo, railsVertCount, 0.70f, 0.78f, 0.90f, 1f, emissive = 0.08f, specular = 0.90f)

    // Batched crossties (1 single VBO draw call for all 3 lanes)
    val tieSpacing = 2.4f
    val tieOffset = engine.worldDistance % tieSpacing
    Matrix.setIdentityM(modelMatrix, 0)
    Matrix.translateM(modelMatrix, 0, 0f, 0f, tieOffset)
    Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
    drawBuffer(tieChunkVbo, tieChunkVertCount, 0.30f, 0.22f, 0.18f, 1f, emissive = 0f, specular = 0.05f)

    // Batched scrolling subway arches, lamps, trees & multi-tier skyline (11 draws total vs ~90)
    val periodLen = 132f // 4 * 33f repeating cityscape period
    val scroll = engine.worldDistance % periodLen
    Matrix.setIdentityM(modelMatrix, 0)
    Matrix.translateM(modelMatrix, 0, 0f, 0f, scroll)
    Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)

    drawBuffer(envPillarVbo, envPillarVertCount, 0.28f, 0.35f, 0.48f, 1f, emissive = 0f, specular = 0.50f)
    drawBuffer(envNeonVbo, envNeonVertCount, 0.0f, 0.92f, 1.0f, 1f, emissive = 0.85f, specular = 0.2f)
    drawBuffer(envLampPoleVbo, envLampPoleVertCount, 0.22f, 0.28f, 0.38f, 1f, emissive = 0f, specular = 0.55f)
    drawBuffer(envLampOrbVbo, envLampOrbVertCount, 1.0f, 0.94f, 0.72f, 1f, emissive = 0.95f, specular = 0.3f)
    drawBuffer(envTreeTrunkVbo, envTreeTrunkVertCount, 0.38f, 0.24f, 0.15f, 1f, emissive = 0f, specular = 0.08f)
    drawBuffer(envTreeCanopyVbo, envTreeCanopyVertCount, 0.14f, 0.60f, 0.34f, 1f, emissive = 0.05f, specular = 0.15f)
    drawBuffer(envBuildingDarkVbo, envBuildingDarkVertCount, 0.13f, 0.16f, 0.23f, 1f, emissive = 0f, specular = 0.18f)
    drawBuffer(envBuildingMidVbo, envBuildingMidVertCount, 0.18f, 0.22f, 0.31f, 1f, emissive = 0f, specular = 0.24f)
    drawBuffer(envCyanWinVbo, envCyanWinVertCount, 0.15f, 0.88f, 1.0f, 1f, emissive = 0.82f, specular = 0.1f)
    drawBuffer(envGoldWinVbo, envGoldWinVertCount, 1.0f, 0.78f, 0.35f, 1f, emissive = 0.82f, specular = 0.1f)
    drawBuffer(envBeaconVbo, envBeaconVertCount, 1.0f, 0.22f, 0.28f, 1f, emissive = 0.95f, specular = 0.3f)
  }

  private fun drawTrain(x: Float, z: Float, length: Float, variant: Int, isMoving: Boolean, nearDetail: Boolean) {
    val palette = TRAIN_PALETTES[variant and 3]
    val br = palette[0]
    val bg = palette[1]
    val bb = palette[2]
    val sr = palette[3]
    val sg = palette[4]
    val sb = palette[5]

    // Undercarriage + 2 bogie wheel axles when within near LOD
    drawCube(x, 0.28f, z, 1.82f, 0.44f, length - 0.5f, 0f, 0f, 0f, 0.11f, 0.13f, 0.18f, 1f, 0f, 0.30f)
    if (nearDetail) {
      drawCylinder(x, 0.25f, z + length * 0.35f, 0.46f, 1.92f, 0.46f, 0f, 0f, 90f, 0.48f, 0.54f, 0.64f, 1f, 0f, 0.80f)
      drawCylinder(x, 0.25f, z - length * 0.35f, 0.46f, 1.92f, 0.46f, 0f, 0f, 90f, 0.48f, 0.54f, 0.64f, 1f, 0f, 0.80f)
    }

    // Main carriage hull + rounded cylindrical roof crown
    drawCube(x, 1.56f, z, 2.16f, 2.12f, length, 0f, 0f, 0f, br, bg, bb, 1f, 0.03f, 0.42f)
    drawCylinder(x, 2.66f, z, 2.08f, length - 0.15f, 0.62f, 90f, 0f, 0f, 0.78f, 0.83f, 0.90f, 1f, 0.05f, 0.78f)

    // Racing stripe & glass window band
    drawCube(x, 1.22f, z, 2.20f, 0.24f, length - 0.1f, 0f, 0f, 0f, sr, sg, sb, 1f, 0.16f, 0.35f)
    drawCube(x, 1.88f, z, 2.19f, 0.62f, length * 0.82f, 0f, 0f, 0f, 0.10f, 0.16f, 0.26f, 1f, 0.18f, 0.90f)

    // Front nose windshield, LED sign, & twin headlights
    val frontZ = z + length * 0.5f + 0.03f
    drawCube(x, 2.00f, frontZ, 1.68f, 0.74f, 0.10f, 0f, 0f, 0f, 0.08f, 0.14f, 0.22f, 1f, 0.25f, 0.92f)
    drawCube(x, 2.55f, frontZ + 0.02f, 0.95f, 0.19f, 0.06f, 0f, 0f, 0f, sr, sg, sb, 1f, 0.85f, 0.2f)

    val lightEmissive = if (isMoving) 1.0f else 0.45f
    val lr = if (isMoving) 1.0f else 0.95f
    val lg = if (isMoving) 0.96f else 0.78f
    val lb = if (isMoving) 0.72f else 0.45f
    drawSphere(x - 0.64f, 1.08f, frontZ + 0.03f, 0.34f, 0.26f, 0.14f, lr, lg, lb, 1f, emissive = lightEmissive, specular = 0.6f)
    drawSphere(x + 0.64f, 1.08f, frontZ + 0.03f, 0.34f, 0.26f, 0.14f, lr, lg, lb, 1f, emissive = lightEmissive, specular = 0.6f)
  }

  private fun drawRamp(x: Float, z: Float, length: Float, height: Float) {
    Matrix.setIdentityM(modelMatrix, 0)
    Matrix.translateM(modelMatrix, 0, x, 0f, z)
    Matrix.scaleM(modelMatrix, 0, 2.08f, height, length)
    Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
    drawBuffer(rampVbo, rampVertCount, 0.96f, 0.68f, 0.10f, 1f, emissive = 0.08f, specular = 0.45f)

    val slopeAngle = Math.toDegrees(atan2(height.toDouble(), length.toDouble())).toFloat()
    drawCube(x - 0.95f, height * 0.52f, z, 0.14f, 0.09f, length * 1.02f, slopeAngle, 0f, 0f, 0.0f, 0.92f, 1.0f, 1f, 0.7f, 0.3f)
    drawCube(x + 0.95f, height * 0.52f, z, 0.14f, 0.09f, length * 1.02f, slopeAngle, 0f, 0f, 0.0f, 0.92f, 1.0f, 1f, 0.7f, 0.3f)
  }

  private fun drawLowBarrier(x: Float, z: Float) {
    drawCylinder(x - 0.82f, 0.45f, z, 0.18f, 0.90f, 0.18f, 0f, 0f, 0f, 0.95f, 0.42f, 0.08f, 1f, 0.08f, 0.35f)
    drawCylinder(x + 0.82f, 0.45f, z, 0.18f, 0.90f, 0.18f, 0f, 0f, 0f, 0.95f, 0.42f, 0.08f, 1f, 0.08f, 0.35f)
    drawCube(x, 0.82f, z, 2.08f, 0.38f, 0.20f, 0f, 0f, 0f, 0.94f, 0.16f, 0.24f, 1f, 0.14f, 0.3f)
    drawCube(x, 0.82f, z + 0.11f, 1.45f, 0.16f, 0.04f, 0f, 0f, 0f, 0.98f, 0.98f, 0.98f, 1f, 0.25f, 0.2f)
  }

  private fun drawHighBarrier(x: Float, z: Float) {
    drawCylinder(x - 0.96f, 1.35f, z, 0.18f, 2.70f, 0.18f, 0f, 0f, 0f, 0.34f, 0.40f, 0.54f, 1f, 0f, 0.6f)
    drawCylinder(x + 0.96f, 1.35f, z, 0.18f, 2.70f, 0.18f, 0f, 0f, 0f, 0.34f, 0.40f, 0.54f, 1f, 0f, 0.6f)
    drawCube(x, 2.02f, z, 2.12f, 1.25f, 0.26f, 0f, 0f, 0f, 0.98f, 0.72f, 0.08f, 1f, 0.18f, 0.35f)
    drawCube(x, 1.48f, z + 0.14f, 1.90f, 0.20f, 0.05f, 0f, 0f, 0f, 0.96f, 0.16f, 0.24f, 1f, 0.65f, 0.2f)
  }

  private fun drawTallBarrier(x: Float, z: Float) {
    drawCube(x, 1.32f, z, 2.12f, 2.64f, 0.45f, 0f, 0f, 0f, 0.28f, 0.33f, 0.44f, 1f, 0.02f, 0.3f)
    drawCube(x, 2.10f, z + 0.24f, 1.85f, 0.32f, 0.05f, 0f, 0f, 0f, 0.98f, 0.25f, 0.22f, 1f, 0.55f, 0.2f)
    drawCube(x, 1.10f, z + 0.24f, 1.85f, 0.32f, 0.05f, 0f, 0f, 0f, 0.98f, 0.78f, 0.12f, 1f, 0.35f, 0.2f)
  }

  private fun drawCoin(x: Float, y: Float, z: Float, spinDeg: Float) {
    Matrix.setIdentityM(modelMatrix, 0)
    Matrix.translateM(modelMatrix, 0, x, y, z)
    Matrix.rotateM(modelMatrix, 0, spinDeg, 0f, 1f, 0f)
    Matrix.scaleM(modelMatrix, 0, 0.44f, 0.44f, 0.14f)
    Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
    drawBuffer(coinVbo, coinVertCount, 1.0f, 0.84f, 0.12f, 1f, emissive = 0.35f, specular = 1.0f)
  }

  private fun drawPowerUpToken(x: Float, y: Float, z: Float, kind: PowerUpKind, spinDeg: Float) {
    val rgb = POWERUP_COLORS[kind.ordinal]
    drawSphere(x, y, z, 0.74f, 0.74f, 0.74f, rgb[0], rgb[1], rgb[2], 1f, emissive = 0.65f, specular = 0.95f)
    drawCube(x, y, z, 1.02f, 0.18f, 0.18f, 25f, -spinDeg * 1.4f, 45f, 1f, 1f, 1f, 1f, emissive = 0.85f, specular = 0.5f)
  }

  private fun drawRunnerRig(dt: Float) {
    if (engine.invulnerableTimer > 0f && ((engine.elapsedTime * 16f).toInt() % 2 == 0)) {
      return
    }

    val charDef = getRunnerDef(engine.selectedRunnerId)
    val boardDef = getBoardDef(engine.selectedBoardId)

    val rx = engine.runnerX
    val ry = engine.runnerY
    val cycle = engine.sprintCycle
    val onBoard = engine.hoverboardActive
    val flying = engine.jetpackRemaining > 0f
    val rolling = engine.isRolling
    val grounded = engine.isGrounded

    if (grounded && !wasGroundedPrev && !flying && !rolling && !onBoard) {
      landingSquash = 1f
    }
    wasGroundedPrev = grounded
    landingSquash *= exp(-15f * dt)

    val swing = if (grounded && !rolling && !onBoard) sin(cycle) else 0.15f
    val bob = if (grounded && !rolling && !onBoard) abs(sin(cycle)) * 0.11f else 0f

    // Hoverboard deck rendered underneath feet
    if (onBoard) {
      val hoverBob = sin(engine.elapsedTime * 8f) * 0.06f
      val boardY = ry + 0.15f + hoverBob
      val tiltZ = -engine.laneTiltDeg * 1.15f
      drawCylinder(rx, boardY, 0f, 0.68f, 1.55f, 0.10f, 90f, engine.laneYawDeg, tiltZ, boardDef.bodyRgb[0], boardDef.bodyRgb[1], boardDef.bodyRgb[2], 1f, 0.35f, 0.88f)
      drawSphere(rx, boardY, -0.78f, 0.66f, 0.10f, 0.42f, boardDef.bodyRgb[0], boardDef.bodyRgb[1], boardDef.bodyRgb[2], 1f, 0.35f, 0.88f)
      drawSphere(rx, boardY, 0.78f, 0.66f, 0.10f, 0.42f, boardDef.bodyRgb[0], boardDef.bodyRgb[1], boardDef.bodyRgb[2], 1f, 0.35f, 0.88f)
    }

    // Target pose parameters
    val targetHipOffset = (if (onBoard) 1.06f else if (rolling) 0.56f else 0.94f) + bob - landingSquash * 0.16f
    val targetBodyPitch =
      when {
        rolling -> -62f
        flying -> 22f
        onBoard -> -6f
        !grounded -> -6f
        else -> -13f - landingSquash * 10f
      }
    val targetLeftShoulder = if (rolling) 35f else if (onBoard) 28f else -swing * 52f
    val targetRightShoulder = if (rolling) 35f else if (onBoard) -24f else swing * 52f
    val targetLeftElbow = if (rolling) 85f else if (onBoard) 35f else 78f - swing * 18f
    val targetRightElbow = if (rolling) 85f else if (onBoard) 35f else 78f + swing * 18f

    val targetLeftHip: Float
    val targetRightHip: Float
    val targetLeftKnee: Float
    val targetRightKnee: Float

    when {
      rolling -> {
        targetLeftHip = 68f
        targetRightHip = 58f
        targetLeftKnee = -95f
        targetRightKnee = -90f
      }
      onBoard -> {
        targetLeftHip = 22f
        targetRightHip = -20f
        targetLeftKnee = -28f
        targetRightKnee = -24f
      }
      !grounded -> {
        targetLeftHip = 48f
        targetRightHip = -26f
        targetLeftKnee = -72f
        targetRightKnee = -32f
      }
      else -> {
        targetLeftHip = swing * 56f + landingSquash * 18f
        targetRightHip = -swing * 56f + landingSquash * 18f
        targetLeftKnee = -(18f + max(0f, -swing) * 58f + landingSquash * 32f)
        targetRightKnee = -(18f + max(0f, swing) * 58f + landingSquash * 32f)
      }
    }

    if (!poseInitialized) {
      poseInitialized = true
      animHipOffset = targetHipOffset
      animBodyPitch = targetBodyPitch
      animLeftShoulder = targetLeftShoulder
      animRightShoulder = targetRightShoulder
      animLeftElbow = targetLeftElbow
      animRightElbow = targetRightElbow
      animLeftHip = targetLeftHip
      animRightHip = targetRightHip
      animLeftKnee = targetLeftKnee
      animRightKnee = targetRightKnee
    } else {
      val poseAlpha = 1f - exp(-22f * dt)
      val jointAlpha = 1f - exp(-26f * dt)
      animHipOffset += (targetHipOffset - animHipOffset) * poseAlpha
      animBodyPitch += (targetBodyPitch - animBodyPitch) * poseAlpha
      animLeftShoulder += (targetLeftShoulder - animLeftShoulder) * jointAlpha
      animRightShoulder += (targetRightShoulder - animRightShoulder) * jointAlpha
      animLeftElbow += (targetLeftElbow - animLeftElbow) * jointAlpha
      animRightElbow += (targetRightElbow - animRightElbow) * jointAlpha
      animLeftHip += (targetLeftHip - animLeftHip) * jointAlpha
      animRightHip += (targetRightHip - animRightHip) * jointAlpha
      animLeftKnee += (targetLeftKnee - animLeftKnee) * jointAlpha
      animRightKnee += (targetRightKnee - animRightKnee) * jointAlpha
    }

    // Articulated runner root matrix (pivoted at pelvis, facing -Z)
    val hipHeight = ry + animHipOffset
    val bodyPitch = animBodyPitch

    Matrix.setIdentityM(runnerRootMatrix, 0)
    Matrix.translateM(runnerRootMatrix, 0, rx, hipHeight, 0f)
    Matrix.rotateM(runnerRootMatrix, 0, engine.laneYawDeg, 0f, 1f, 0f)
    Matrix.rotateM(runnerRootMatrix, 0, -engine.laneTiltDeg, 0f, 0f, 1f)
    Matrix.rotateM(runnerRootMatrix, 0, bodyPitch, 1f, 0f, 0f)

    // Pelvis + Torso + Chest + Athletic Trim
    drawLocal(runnerRootMatrix, sphereVbo, sphereVertCount, 0f, 0.04f, 0f, 0f, 0f, 0f, 0.46f, 0.24f, 0.30f, charDef.pantsRgb[0], charDef.pantsRgb[1], charDef.pantsRgb[2], 1f, 0f, 0.2f)
    drawLocal(runnerRootMatrix, cylinderVbo, cylinderVertCount, 0f, 0.36f, 0f, 0f, 0f, 0f, 0.52f, 0.60f, 0.32f, charDef.jacketRgb[0], charDef.jacketRgb[1], charDef.jacketRgb[2], 1f, 0.08f, 0.35f)
    drawLocal(runnerRootMatrix, sphereVbo, sphereVertCount, 0f, 0.62f, 0f, 0f, 0f, 0f, 0.56f, 0.26f, 0.34f, charDef.jacketRgb[0], charDef.jacketRgb[1], charDef.jacketRgb[2], 1f, 0.08f, 0.35f)
    drawLocal(runnerRootMatrix, cylinderVbo, cylinderVertCount, 0f, 0.42f, -0.02f, 0f, 0f, 0f, 0.53f, 0.14f, 0.33f, charDef.accentRgb[0], charDef.accentRgb[1], charDef.accentRgb[2], 1f, 0.28f, 0.35f)

    // Backpack or Twin-Jetpack
    if (flying) {
      drawLocal(runnerRootMatrix, cylinderVbo, cylinderVertCount, -0.15f, 0.42f, 0.25f, 0f, 0f, 0f, 0.18f, 0.50f, 0.18f, 0.96f, 0.48f, 0.10f, 1f, 0.45f, 0.75f)
      drawLocal(runnerRootMatrix, cylinderVbo, cylinderVertCount, 0.15f, 0.42f, 0.25f, 0f, 0f, 0f, 0.18f, 0.50f, 0.18f, 0.96f, 0.48f, 0.10f, 1f, 0.45f, 0.75f)
      drawLocal(runnerRootMatrix, coneVbo, coneVertCount, -0.15f, 0.12f, 0.25f, 0f, 0f, 0f, 0.20f, 0.16f, 0.20f, 0.35f, 0.38f, 0.45f, 1f, 0.1f, 0.8f)
      drawLocal(runnerRootMatrix, coneVbo, coneVertCount, 0.15f, 0.12f, 0.25f, 0f, 0f, 0f, 0.20f, 0.16f, 0.20f, 0.35f, 0.38f, 0.45f, 1f, 0.1f, 0.8f)
    } else {
      drawLocal(runnerRootMatrix, sphereVbo, sphereVertCount, 0f, 0.42f, 0.22f, 0f, 0f, 0f, 0.38f, 0.46f, 0.22f, charDef.accentRgb[0], charDef.accentRgb[1], charDef.accentRgb[2], 1f, 0.12f, 0.35f)
    }

    // Neck + Rounded 3D Head + Shades + Cap Dome + Snapback Brim + Headphones
    drawLocal(runnerRootMatrix, cylinderVbo, cylinderVertCount, 0f, 0.80f, 0f, 0f, 0f, 0f, 0.16f, 0.14f, 0.16f, charDef.skinRgb[0], charDef.skinRgb[1], charDef.skinRgb[2], 1f, 0f, 0.2f)
    drawLocal(runnerRootMatrix, sphereVbo, sphereVertCount, 0f, 1.00f, -0.02f, -bodyPitch * 0.35f, 0f, 0f, 0.39f, 0.42f, 0.41f, charDef.skinRgb[0], charDef.skinRgb[1], charDef.skinRgb[2], 1f, 0f, 0.22f)
    drawLocal(runnerRootMatrix, cubeVbo, cubeVertCount, 0f, 1.02f, -0.20f, -bodyPitch * 0.35f, 0f, 0f, 0.32f, 0.10f, 0.08f, 0.06f, 0.10f, 0.16f, 1f, 0.15f, 0.95f)
    drawLocal(runnerRootMatrix, sphereVbo, sphereVertCount, 0f, 1.08f, 0.00f, -bodyPitch * 0.35f, 0f, 0f, 0.42f, 0.30f, 0.44f, charDef.capRgb[0], charDef.capRgb[1], charDef.capRgb[2], 1f, 0.12f, 0.38f)
    drawLocal(runnerRootMatrix, cylinderVbo, cylinderVertCount, 0f, 1.07f, 0.20f, -bodyPitch * 0.35f, 0f, 0f, 0.34f, 0.05f, 0.22f, charDef.accentRgb[0], charDef.accentRgb[1], charDef.accentRgb[2], 1f, 0.20f, 0.35f)
    drawLocal(runnerRootMatrix, cylinderVbo, cylinderVertCount, -0.22f, 1.01f, -0.02f, 0f, 0f, 90f, 0.16f, 0.08f, 0.16f, charDef.accentRgb[0], charDef.accentRgb[1], charDef.accentRgb[2], 1f, 0.35f, 0.7f)
    drawLocal(runnerRootMatrix, cylinderVbo, cylinderVertCount, 0.22f, 1.01f, -0.02f, 0f, 0f, 90f, 0.16f, 0.08f, 0.16f, charDef.accentRgb[0], charDef.accentRgb[1], charDef.accentRgb[2], 1f, 0.35f, 0.7f)

    // Articulated Arms
    drawArticulatedArm(side = -1f, shoulderPitch = animLeftShoulder, elbowBend = animLeftElbow, sleeveRgb = charDef.jacketRgb, skinRgb = charDef.skinRgb)
    drawArticulatedArm(side = 1f, shoulderPitch = animRightShoulder, elbowBend = animRightElbow, sleeveRgb = charDef.jacketRgb, skinRgb = charDef.skinRgb)

    // Articulated Legs
    val shoeR = if (engine.sneakersRemaining > 0f) 0.15f else charDef.accentRgb[0]
    val shoeG = if (engine.sneakersRemaining > 0f) 1.0f else charDef.accentRgb[1]
    val shoeB = if (engine.sneakersRemaining > 0f) 0.55f else charDef.accentRgb[2]
    val shoeEmissive = if (engine.sneakersRemaining > 0f) 0.85f else 0.18f

    drawArticulatedLeg(side = -1f, hipPitch = animLeftHip, kneeFlex = animLeftKnee, pantsRgb = charDef.pantsRgb, shoeR = shoeR, shoeG = shoeG, shoeB = shoeB, shoeEmissive = shoeEmissive)
    drawArticulatedLeg(side = 1f, hipPitch = animRightHip, kneeFlex = animRightKnee, pantsRgb = charDef.pantsRgb, shoeR = shoeR, shoeG = shoeG, shoeB = shoeB, shoeEmissive = shoeEmissive)
  }

  private fun drawArticulatedArm(
    side: Float,
    shoulderPitch: Float,
    elbowBend: Float,
    sleeveRgb: FloatArray,
    skinRgb: FloatArray,
  ) {
    System.arraycopy(runnerRootMatrix, 0, limbMatrix, 0, 16)
    Matrix.translateM(limbMatrix, 0, side * 0.34f, 0.58f, 0f)
    Matrix.rotateM(limbMatrix, 0, side * 9f, 0f, 0f, 1f)
    Matrix.rotateM(limbMatrix, 0, shoulderPitch, 1f, 0f, 0f)

    drawLocal(limbMatrix, sphereVbo, sphereVertCount, 0f, 0f, 0f, 0f, 0f, 0f, 0.17f, 0.17f, 0.17f, sleeveRgb[0], sleeveRgb[1], sleeveRgb[2], 1f, 0.06f, 0.35f)
    drawLocal(limbMatrix, cylinderVbo, cylinderVertCount, 0f, -0.14f, 0f, 0f, 0f, 0f, 0.14f, 0.28f, 0.14f, sleeveRgb[0], sleeveRgb[1], sleeveRgb[2], 1f, 0.05f, 0.32f)

    Matrix.translateM(limbMatrix, 0, 0f, -0.28f, 0f)
    drawLocal(limbMatrix, sphereVbo, sphereVertCount, 0f, 0f, 0f, 0f, 0f, 0f, 0.13f, 0.13f, 0.13f, sleeveRgb[0], sleeveRgb[1], sleeveRgb[2], 1f, 0.05f, 0.3f)
    Matrix.rotateM(limbMatrix, 0, elbowBend, 1f, 0f, 0f)
    drawLocal(limbMatrix, cylinderVbo, cylinderVertCount, 0f, -0.13f, 0f, 0f, 0f, 0f, 0.12f, 0.26f, 0.12f, skinRgb[0], skinRgb[1], skinRgb[2], 1f, 0f, 0.22f)
    drawLocal(limbMatrix, sphereVbo, sphereVertCount, 0f, -0.28f, 0f, 0f, 0f, 0f, 0.13f, 0.13f, 0.13f, skinRgb[0], skinRgb[1], skinRgb[2], 1f, 0f, 0.22f)
  }

  private fun drawArticulatedLeg(
    side: Float,
    hipPitch: Float,
    kneeFlex: Float,
    pantsRgb: FloatArray,
    shoeR: Float,
    shoeG: Float,
    shoeB: Float,
    shoeEmissive: Float,
  ) {
    System.arraycopy(runnerRootMatrix, 0, limbMatrix, 0, 16)
    Matrix.translateM(limbMatrix, 0, side * 0.15f, -0.02f, 0f)
    Matrix.rotateM(limbMatrix, 0, hipPitch, 1f, 0f, 0f)

    drawLocal(limbMatrix, sphereVbo, sphereVertCount, 0f, 0f, 0f, 0f, 0f, 0f, 0.20f, 0.20f, 0.20f, pantsRgb[0], pantsRgb[1], pantsRgb[2], 1f, 0f, 0.2f)
    drawLocal(limbMatrix, cylinderVbo, cylinderVertCount, 0f, -0.20f, 0f, 0f, 0f, 0f, 0.19f, 0.40f, 0.19f, pantsRgb[0], pantsRgb[1], pantsRgb[2], 1f, 0f, 0.2f)

    Matrix.translateM(limbMatrix, 0, 0f, -0.40f, 0f)
    drawLocal(limbMatrix, sphereVbo, sphereVertCount, 0f, 0f, 0f, 0f, 0f, 0f, 0.17f, 0.17f, 0.17f, pantsRgb[0], pantsRgb[1], pantsRgb[2], 1f, 0f, 0.2f)
    Matrix.rotateM(limbMatrix, 0, kneeFlex, 1f, 0f, 0f)
    drawLocal(limbMatrix, cylinderVbo, cylinderVertCount, 0f, -0.18f, 0f, 0f, 0f, 0f, 0.15f, 0.36f, 0.15f, pantsRgb[0], pantsRgb[1], pantsRgb[2], 1f, 0f, 0.2f)

    Matrix.translateM(limbMatrix, 0, 0f, -0.40f, -0.04f)
    drawLocal(limbMatrix, sphereVbo, sphereVertCount, 0f, 0.02f, 0.04f, 0f, 0f, 0f, 0.20f, 0.18f, 0.22f, shoeR, shoeG, shoeB, 1f, shoeEmissive, 0.55f)
    drawLocal(limbMatrix, sphereVbo, sphereVertCount, 0f, -0.02f, -0.11f, 0f, 0f, 0f, 0.21f, 0.15f, 0.26f, shoeR, shoeG, shoeB, 1f, shoeEmissive, 0.55f)
    drawLocal(limbMatrix, cubeVbo, cubeVertCount, 0f, -0.08f, -0.04f, 0f, 0f, 0f, 0.21f, 0.06f, 0.35f, 0.96f, 0.97f, 1.0f, 1f, 0.1f, 0.3f)
  }

  private fun drawChaserGuard(x: Float, y: Float, z: Float, cycle: Float) {
    val swing = sin(cycle)
    val bob = abs(sin(cycle)) * 0.10f

    Matrix.setIdentityM(runnerRootMatrix, 0)
    Matrix.translateM(runnerRootMatrix, 0, x, y + 0.96f + bob, z)
    Matrix.rotateM(runnerRootMatrix, 0, -12f, 1f, 0f, 0f)

    drawLocal(runnerRootMatrix, cylinderVbo, cylinderVertCount, 0f, 0.38f, 0f, 0f, 0f, 0f, 0.64f, 0.68f, 0.38f, 0.12f, 0.25f, 0.58f, 1f, 0.05f, 0.32f)
    drawLocal(runnerRootMatrix, sphereVbo, sphereVertCount, 0f, 0.66f, 0f, 0f, 0f, 0f, 0.66f, 0.28f, 0.40f, 0.12f, 0.25f, 0.58f, 1f, 0.05f, 0.32f)
    drawLocal(runnerRootMatrix, sphereVbo, sphereVertCount, 0f, 1.04f, 0f, 0f, 0f, 0f, 0.40f, 0.42f, 0.40f, 0.85f, 0.66f, 0.52f, 1f, 0f, 0.2f)
    drawLocal(runnerRootMatrix, cylinderVbo, cylinderVertCount, 0f, 1.24f, 0f, 0f, 0f, 0f, 0.46f, 0.15f, 0.46f, 0.09f, 0.16f, 0.38f, 1f, 0.05f, 0.4f)

    drawArticulatedArm(side = -1f, shoulderPitch = -swing * 44f, elbowBend = 70f, sleeveRgb = GUARD_NAVY, skinRgb = GUARD_SKIN)
    drawArticulatedArm(side = 1f, shoulderPitch = 48f, elbowBend = 45f, sleeveRgb = GUARD_NAVY, skinRgb = GUARD_SKIN)
    drawLocal(runnerRootMatrix, cylinderVbo, cylinderVertCount, 0.38f, 0.30f, -0.45f, 90f, 0f, 0f, 0.12f, 0.30f, 0.12f, 1.0f, 0.92f, 0.35f, 1f, 0.95f, 0.4f)

    drawArticulatedLeg(side = -1f, hipPitch = swing * 48f, kneeFlex = -(25f + max(0f, -swing) * 45f), pantsRgb = GUARD_PANTS, shoeR = 0.06f, shoeG = 0.08f, shoeB = 0.12f, shoeEmissive = 0f)
    drawArticulatedLeg(side = 1f, hipPitch = -swing * 48f, kneeFlex = -(25f + max(0f, swing) * 45f), pantsRgb = GUARD_PANTS, shoeR = 0.06f, shoeG = 0.08f, shoeB = 0.12f, shoeEmissive = 0f)
  }

  private fun drawLocal(
    parent: FloatArray,
    vboId: Int,
    vertCount: Int,
    lx: Float,
    ly: Float,
    lz: Float,
    rxDeg: Float,
    ryDeg: Float,
    rzDeg: Float,
    sx: Float,
    sy: Float,
    sz: Float,
    r: Float,
    g: Float,
    b: Float,
    a: Float,
    emissive: Float,
    specular: Float,
  ) {
    System.arraycopy(parent, 0, subLimbMatrix, 0, 16)
    Matrix.translateM(subLimbMatrix, 0, lx, ly, lz)
    if (rzDeg != 0f) Matrix.rotateM(subLimbMatrix, 0, rzDeg, 0f, 0f, 1f)
    if (ryDeg != 0f) Matrix.rotateM(subLimbMatrix, 0, ryDeg, 0f, 1f, 0f)
    if (rxDeg != 0f) Matrix.rotateM(subLimbMatrix, 0, rxDeg, 1f, 0f, 0f)
    Matrix.scaleM(subLimbMatrix, 0, sx, sy, sz)
    System.arraycopy(subLimbMatrix, 0, modelMatrix, 0, 16)
    Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
    drawBuffer(vboId, vertCount, r, g, b, a, emissive, specular)
  }

  private fun drawCube(
    x: Float,
    y: Float,
    z: Float,
    sx: Float,
    sy: Float,
    sz: Float,
    rxDeg: Float,
    ryDeg: Float,
    rzDeg: Float,
    r: Float,
    g: Float,
    b: Float,
    a: Float,
    emissive: Float,
    specular: Float,
  ) = drawPrimitive(cubeVbo, cubeVertCount, x, y, z, sx, sy, sz, rxDeg, ryDeg, rzDeg, r, g, b, a, emissive, specular)

  private fun drawSphere(
    x: Float,
    y: Float,
    z: Float,
    sx: Float,
    sy: Float,
    sz: Float,
    r: Float,
    g: Float,
    b: Float,
    a: Float,
    emissive: Float,
    specular: Float,
  ) = drawPrimitive(sphereVbo, sphereVertCount, x, y, z, sx, sy, sz, 0f, 0f, 0f, r, g, b, a, emissive, specular)

  private fun drawCylinder(
    x: Float,
    y: Float,
    z: Float,
    sx: Float,
    sy: Float,
    sz: Float,
    rxDeg: Float,
    ryDeg: Float,
    rzDeg: Float,
    r: Float,
    g: Float,
    b: Float,
    a: Float,
    emissive: Float,
    specular: Float,
  ) = drawPrimitive(cylinderVbo, cylinderVertCount, x, y, z, sx, sy, sz, rxDeg, ryDeg, rzDeg, r, g, b, a, emissive, specular)

  private fun drawCone(
    x: Float,
    y: Float,
    z: Float,
    sx: Float,
    sy: Float,
    sz: Float,
    rxDeg: Float,
    ryDeg: Float,
    rzDeg: Float,
    r: Float,
    g: Float,
    b: Float,
    a: Float,
    emissive: Float,
    specular: Float,
  ) = drawPrimitive(coneVbo, coneVertCount, x, y, z, sx, sy, sz, rxDeg, ryDeg, rzDeg, r, g, b, a, emissive, specular)

  private fun drawPrimitive(
    vboId: Int,
    vertCount: Int,
    x: Float,
    y: Float,
    z: Float,
    sx: Float,
    sy: Float,
    sz: Float,
    rxDeg: Float,
    ryDeg: Float,
    rzDeg: Float,
    r: Float,
    g: Float,
    b: Float,
    a: Float,
    emissive: Float,
    specular: Float,
  ) {
    Matrix.setIdentityM(modelMatrix, 0)
    Matrix.translateM(modelMatrix, 0, x, y, z)
    if (rzDeg != 0f) Matrix.rotateM(modelMatrix, 0, rzDeg, 0f, 0f, 1f)
    if (ryDeg != 0f) Matrix.rotateM(modelMatrix, 0, ryDeg, 0f, 1f, 0f)
    if (rxDeg != 0f) Matrix.rotateM(modelMatrix, 0, rxDeg, 1f, 0f, 0f)
    Matrix.scaleM(modelMatrix, 0, sx, sy, sz)
    Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
    drawBuffer(vboId, vertCount, r, g, b, a, emissive, specular)
  }

  private fun drawDisc(x: Float, y: Float, z: Float, radius: Float, r: Float, g: Float, b: Float, a: Float) {
    Matrix.setIdentityM(modelMatrix, 0)
    Matrix.translateM(modelMatrix, 0, x, y, z)
    Matrix.scaleM(modelMatrix, 0, radius, 1f, radius)
    Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
    drawBuffer(discVbo, discVertCount, r, g, b, a, emissive = 0.0f, specular = 0.0f)
  }

  private fun drawBuffer(
    vboId: Int,
    vertCount: Int,
    r: Float,
    g: Float,
    b: Float,
    a: Float,
    emissive: Float,
    specular: Float,
  ) {
    GLES20.glUniformMatrix4fv(uMvpLoc, 1, false, mvpMatrix, 0)
    GLES20.glUniformMatrix4fv(uModelLoc, 1, false, modelMatrix, 0)
    GLES20.glUniform4f(uColorLoc, r, g, b, a)
    GLES20.glUniform1f(uEmissiveLoc, emissive)
    GLES20.glUniform1f(uSpecularLoc, specular)

    if (lastBoundVbo != vboId) {
      GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId)
      GLES20.glVertexAttribPointer(aPosLoc, 3, GLES20.GL_FLOAT, false, 6 * 4, 0)
      GLES20.glVertexAttribPointer(aNormLoc, 3, GLES20.GL_FLOAT, false, 6 * 4, 3 * 4)
      lastBoundVbo = vboId
    }

    GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertCount)
  }

  private fun buildGeometryVbos() {
    fun writeVert(arr: FloatArray, ptr: Int, x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float): Int {
      arr[ptr] = x
      arr[ptr + 1] = y
      arr[ptr + 2] = z
      arr[ptr + 3] = nx
      arr[ptr + 4] = ny
      arr[ptr + 5] = nz
      return ptr + 6
    }

    val cubeVerts =
      floatArrayOf(
        -0.5f, -0.5f, 0.5f, 0f, 0f, 1f, 0.5f, -0.5f, 0.5f, 0f, 0f, 1f, 0.5f, 0.5f, 0.5f, 0f, 0f, 1f,
        -0.5f, -0.5f, 0.5f, 0f, 0f, 1f, 0.5f, 0.5f, 0.5f, 0f, 0f, 1f, -0.5f, 0.5f, 0.5f, 0f, 0f, 1f,
        0.5f, -0.5f, -0.5f, 0f, 0f, -1f, -0.5f, -0.5f, -0.5f, 0f, 0f, -1f, -0.5f, 0.5f, -0.5f, 0f, 0f, -1f,
        0.5f, -0.5f, -0.5f, 0f, 0f, -1f, -0.5f, 0.5f, -0.5f, 0f, 0f, -1f, 0.5f, 0.5f, -0.5f, 0f, 0f, -1f,
        -0.5f, -0.5f, -0.5f, -1f, 0f, 0f, -0.5f, -0.5f, 0.5f, -1f, 0f, 0f, -0.5f, 0.5f, 0.5f, -1f, 0f, 0f,
        -0.5f, -0.5f, -0.5f, -1f, 0f, 0f, -0.5f, 0.5f, 0.5f, -1f, 0f, 0f, -0.5f, 0.5f, -0.5f, -1f, 0f, 0f,
        0.5f, -0.5f, 0.5f, 1f, 0f, 0f, 0.5f, -0.5f, -0.5f, 1f, 0f, 0f, 0.5f, 0.5f, -0.5f, 1f, 0f, 0f,
        0.5f, -0.5f, 0.5f, 1f, 0f, 0f, 0.5f, 0.5f, -0.5f, 1f, 0f, 0f, 0.5f, 0.5f, 0.5f, 1f, 0f, 0f,
        -0.5f, 0.5f, 0.5f, 0f, 1f, 0f, 0.5f, 0.5f, 0.5f, 0f, 1f, 0f, 0.5f, 0.5f, -0.5f, 0f, 1f, 0f,
        -0.5f, 0.5f, 0.5f, 0f, 1f, 0f, 0.5f, 0.5f, -0.5f, 0f, 1f, 0f, -0.5f, 0.5f, -0.5f, 0f, 1f, 0f,
        -0.5f, -0.5f, -0.5f, 0f, -1f, 0f, 0.5f, -0.5f, -0.5f, 0f, -1f, 0f, 0.5f, -0.5f, 0.5f, 0f, -1f, 0f,
        -0.5f, -0.5f, -0.5f, 0f, -1f, 0f, 0.5f, -0.5f, 0.5f, 0f, -1f, 0f, -0.5f, -0.5f, 0.5f, 0f, -1f, 0f,
      )
    cubeVertCount = cubeVerts.size / 6
    cubeVbo = uploadVbo(cubeVerts)

    // Smooth low-poly UV Sphere (6x10 = 120 triangles; smooth outward vertex normals)
    val stacks = 6
    val slices = 10
    val sphereArr = FloatArray(stacks * slices * 6 * 6)
    var sPtr = 0
    for (st in 0 until stacks) {
      val phi0 = (-PI / 2.0 + (st.toDouble() / stacks) * PI).toFloat()
      val phi1 = (-PI / 2.0 + ((st + 1).toDouble() / stacks) * PI).toFloat()
      val y0 = sin(phi0) * 0.5f
      val r0 = cos(phi0) * 0.5f
      val y1 = sin(phi1) * 0.5f
      val r1 = cos(phi1) * 0.5f
      for (sl in 0 until slices) {
        val th0 = (sl.toDouble() / slices * 2.0 * PI).toFloat()
        val th1 = ((sl + 1).toDouble() / slices * 2.0 * PI).toFloat()
        val c0 = cos(th0)
        val s0 = sin(th0)
        val c1 = cos(th1)
        val s1 = sin(th1)

        val ax = r0 * c0
        val az = r0 * s0
        val bx = r0 * c1
        val bz = r0 * s1
        val cx = r1 * c1
        val cz = r1 * s1
        val dx = r1 * c0
        val dz = r1 * s0

        sPtr = writeVert(sphereArr, sPtr, ax, y0, az, ax * 2f, y0 * 2f, az * 2f)
        sPtr = writeVert(sphereArr, sPtr, cx, y1, cz, cx * 2f, y1 * 2f, cz * 2f)
        sPtr = writeVert(sphereArr, sPtr, bx, y0, bz, bx * 2f, y0 * 2f, bz * 2f)

        sPtr = writeVert(sphereArr, sPtr, ax, y0, az, ax * 2f, y0 * 2f, az * 2f)
        sPtr = writeVert(sphereArr, sPtr, dx, y1, dz, dx * 2f, y1 * 2f, dz * 2f)
        sPtr = writeVert(sphereArr, sPtr, cx, y1, cz, cx * 2f, y1 * 2f, cz * 2f)
      }
    }
    sphereVertCount = sphereArr.size / 6
    sphereVbo = uploadVbo(sphereArr)

    // Smooth Cylinder (10-sided)
    val cylSides = 10
    val cylArr = FloatArray(cylSides * 12 * 6)
    var cyPtr = 0
    for (i in 0 until cylSides) {
      val a0 = (i.toFloat() / cylSides) * 2f * PI.toFloat()
      val a1 = ((i + 1).toFloat() / cylSides) * 2f * PI.toFloat()
      val c0 = cos(a0)
      val s0 = sin(a0)
      val c1 = cos(a1)
      val s1 = sin(a1)
      val x0 = c0 * 0.5f
      val z0 = s0 * 0.5f
      val x1 = c1 * 0.5f
      val z1 = s1 * 0.5f

      cyPtr = writeVert(cylArr, cyPtr, x0, -0.5f, z0, c0, 0f, s0)
      cyPtr = writeVert(cylArr, cyPtr, x0, 0.5f, z0, c0, 0f, s0)
      cyPtr = writeVert(cylArr, cyPtr, x1, 0.5f, z1, c1, 0f, s1)
      cyPtr = writeVert(cylArr, cyPtr, x0, -0.5f, z0, c0, 0f, s0)
      cyPtr = writeVert(cylArr, cyPtr, x1, 0.5f, z1, c1, 0f, s1)
      cyPtr = writeVert(cylArr, cyPtr, x1, -0.5f, z1, c1, 0f, s1)

      cyPtr = writeVert(cylArr, cyPtr, 0f, 0.5f, 0f, 0f, 1f, 0f)
      cyPtr = writeVert(cylArr, cyPtr, x1, 0.5f, z1, 0f, 1f, 0f)
      cyPtr = writeVert(cylArr, cyPtr, x0, 0.5f, z0, 0f, 1f, 0f)

      cyPtr = writeVert(cylArr, cyPtr, 0f, -0.5f, 0f, 0f, -1f, 0f)
      cyPtr = writeVert(cylArr, cyPtr, x0, -0.5f, z0, 0f, -1f, 0f)
      cyPtr = writeVert(cylArr, cyPtr, x1, -0.5f, z1, 0f, -1f, 0f)
    }
    cylinderVertCount = cylArr.size / 6
    cylinderVbo = uploadVbo(cylArr)

    // Smooth Cone (10-sided)
    val coneSides = 10
    val coneArr = FloatArray(coneSides * 6 * 6)
    var coPtr = 0
    val nySlope = 0.4472f
    val nrSlope = 0.8944f
    for (i in 0 until coneSides) {
      val a0 = (i.toFloat() / coneSides) * 2f * PI.toFloat()
      val a1 = ((i + 1).toFloat() / coneSides) * 2f * PI.toFloat()
      val c0 = cos(a0)
      val s0 = sin(a0)
      val c1 = cos(a1)
      val s1 = sin(a1)
      val x0 = c0 * 0.5f
      val z0 = s0 * 0.5f
      val x1 = c1 * 0.5f
      val z1 = s1 * 0.5f
      val cMid = cos((a0 + a1) * 0.5f)
      val sMid = sin((a0 + a1) * 0.5f)

      coPtr = writeVert(coneArr, coPtr, x0, -0.5f, z0, c0 * nrSlope, nySlope, s0 * nrSlope)
      coPtr = writeVert(coneArr, coPtr, 0f, 0.5f, 0f, cMid * nrSlope, nySlope, sMid * nrSlope)
      coPtr = writeVert(coneArr, coPtr, x1, -0.5f, z1, c1 * nrSlope, nySlope, s1 * nrSlope)

      coPtr = writeVert(coneArr, coPtr, 0f, -0.5f, 0f, 0f, -1f, 0f)
      coPtr = writeVert(coneArr, coPtr, x0, -0.5f, z0, 0f, -1f, 0f)
      coPtr = writeVert(coneArr, coPtr, x1, -0.5f, z1, 0f, -1f, 0f)
    }
    coneVertCount = coneArr.size / 6
    coneVbo = uploadVbo(coneArr)

    // Ultra-fast 8-triangle Octahedron for spark & dust particles (strict CCW outward winding)
    val n = 0.5773f
    val octaVerts =
      floatArrayOf(
        0f, 0.5f, 0f, n, n, n, 0f, 0f, 0.5f, n, n, n, 0.5f, 0f, 0f, n, n, n,
        0f, 0.5f, 0f, -n, n, n, -0.5f, 0f, 0f, -n, n, n, 0f, 0f, 0.5f, -n, n, n,
        0f, 0.5f, 0f, -n, n, -n, 0f, 0f, -0.5f, -n, n, -n, -0.5f, 0f, 0f, -n, n, -n,
        0f, 0.5f, 0f, n, n, -n, 0.5f, 0f, 0f, n, n, -n, 0f, 0f, -0.5f, n, n, -n,
        0f, -0.5f, 0f, n, -n, n, 0.5f, 0f, 0f, n, -n, n, 0f, 0f, 0.5f, n, -n, n,
        0f, -0.5f, 0f, -n, -n, n, 0f, 0f, 0.5f, -n, -n, n, -0.5f, 0f, 0f, -n, -n, n,
        0f, -0.5f, 0f, -n, -n, -n, -0.5f, 0f, 0f, -n, -n, -n, 0f, 0f, -0.5f, -n, -n, -n,
        0f, -0.5f, 0f, n, -n, -n, 0f, 0f, -0.5f, n, -n, -n, 0.5f, 0f, 0f, n, -n, -n,
      )
    octaVertCount = octaVerts.size / 6
    octaVbo = uploadVbo(octaVerts)

    fun appendScaledCube(dest: FloatArray, startPtr: Int, cx: Float, cy: Float, cz: Float, sx: Float, sy: Float, sz: Float): Int {
      var p = startPtr
      var v = 0
      while (v < cubeVerts.size) {
        dest[p++] = cubeVerts[v] * sx + cx
        dest[p++] = cubeVerts[v + 1] * sy + cy
        dest[p++] = cubeVerts[v + 2] * sz + cz
        dest[p++] = cubeVerts[v + 3]
        dest[p++] = cubeVerts[v + 4]
        dest[p++] = cubeVerts[v + 5]
        v += 6
      }
      return p
    }

    // Batched static outer grounds (2 slabs -> 1 VBO)
    val outerArr = FloatArray(2 * cubeVerts.size)
    var p = appendScaledCube(outerArr, 0, -15f, -0.30f, -90f, 16f, 0.4f, 210f)
    appendScaledCube(outerArr, p, 15f, -0.30f, -90f, 16f, 0.4f, 210f)
    outerGroundVertCount = outerArr.size / 6
    outerGroundVbo = uploadVbo(outerArr)

    // Batched static raised platforms (2 platforms -> 1 VBO)
    val platArr = FloatArray(2 * cubeVerts.size)
    p = appendScaledCube(platArr, 0, -7.3f, 0.45f, -90f, 5.2f, 1.3f, 210f)
    appendScaledCube(platArr, p, 7.3f, 0.45f, -90f, 5.2f, 1.3f, 210f)
    platformsVertCount = platArr.size / 6
    platformsVbo = uploadVbo(platArr)

    // Batched static yellow curbs (2 curbs -> 1 VBO)
    val curbArr = FloatArray(2 * cubeVerts.size)
    p = appendScaledCube(curbArr, 0, -4.78f, 1.12f, -90f, 0.24f, 0.07f, 210f)
    appendScaledCube(curbArr, p, 4.78f, 1.12f, -90f, 0.24f, 0.07f, 210f)
    curbsVertCount = curbArr.size / 6
    curbsVbo = uploadVbo(curbArr)

    // Batched 6 gleaming steel rails -> 1 VBO
    val railsArr = FloatArray(6 * cubeVerts.size)
    p = 0
    for (lane in -1..1) {
      val lx = lane * DashWorldEngine.LANE_SPACING
      p = appendScaledCube(railsArr, p, lx - 0.62f, 0.06f, -90f, 0.11f, 0.12f, 210f)
      p = appendScaledCube(railsArr, p, lx + 0.62f, 0.06f, -90f, 0.11f, 0.12f, 210f)
    }
    railsVertCount = railsArr.size / 6
    railsVbo = uploadVbo(railsArr)

    // Pre-batched repeating track crossties (60 rows x 3 lanes = 1 single draw call)
    val tieRows = 60
    val tieSpacing = 2.4f
    val tiesArr = FloatArray(tieRows * 3 * cubeVerts.size)
    var tiePtr = 0
    for (i in 0 until tieRows) {
      val tz = 10f - i * tieSpacing
      for (lane in -1..1) {
        val lx = lane * DashWorldEngine.LANE_SPACING
        tiePtr = appendScaledCube(tiesArr, tiePtr, lx, 0.015f, tz, 1.72f, 0.04f, 0.34f)
      }
    }
    tieChunkVertCount = tiesArr.size / 6
    tieChunkVbo = uploadVbo(tiesArr)

    // Pre-batched repeating subway arches, lamps, trees & multi-tier skyline (11 segments, 4-segment period = 132m)
    val pillarParts = ArrayList<Float>(11 * 3 * cylArr.size)
    val neonParts = ArrayList<Float>(6 * cubeVerts.size)
    val lampPoleParts = ArrayList<Float>(11 * cylArr.size)
    val lampOrbParts = ArrayList<Float>(11 * sphereArr.size)
    val treeTrunkParts = ArrayList<Float>(11 * cylArr.size)
    val treeCanopyParts = ArrayList<Float>(11 * sphereArr.size)
    val buildingDarkParts = ArrayList<Float>(30 * cubeVerts.size)
    val buildingMidParts = ArrayList<Float>(30 * cubeVerts.size)
    val cyanWinParts = ArrayList<Float>(25 * cubeVerts.size)
    val goldWinParts = ArrayList<Float>(25 * cubeVerts.size)
    val beaconParts = ArrayList<Float>(10 * sphereArr.size)

    fun appendTransformedMesh(
      dest: ArrayList<Float>,
      src: FloatArray,
      cx: Float,
      cy: Float,
      cz: Float,
      sx: Float,
      sy: Float,
      sz: Float,
      rzDeg: Float = 0f,
    ) {
      val rad = rzDeg * (PI.toFloat() / 180f)
      val cr = cos(rad)
      val sr = sin(rad)
      val invSx = 1f / sx
      val invSy = 1f / sy
      val invSz = 1f / sz
      var v = 0
      while (v < src.size) {
        val px = src[v] * sx
        val py = src[v + 1] * sy
        val pz = src[v + 2] * sz
        val rxp = px * cr - py * sr + cx
        val ryp = px * sr + py * cr + cy
        val rzp = pz + cz

        val nx = src[v + 3] * invSx
        val ny = src[v + 4] * invSy
        val nz = src[v + 5] * invSz
        val rxn = nx * cr - ny * sr
        val ryn = nx * sr + ny * cr
        val rzn = nz
        val len = kotlin.math.sqrt(rxn * rxn + ryn * ryn + rzn * rzn).coerceAtLeast(1e-5f)

        dest.add(rxp)
        dest.add(ryp)
        dest.add(rzp)
        dest.add(rxn / len)
        dest.add(ryn / len)
        dest.add(rzn / len)
        v += 6
      }
    }

    val segLen = 33f
    for (seg in 0 until 11) {
      val z = 12f - seg * segLen
      val modIdx = seg and 3

      // Pillars + overhead crossbeam
      appendTransformedMesh(pillarParts, cylArr, -5.05f, 3.65f, z, 0.34f, 6.2f, 0.34f)
      appendTransformedMesh(pillarParts, cylArr, 5.05f, 3.65f, z, 0.34f, 6.2f, 0.34f)
      appendTransformedMesh(pillarParts, cylArr, 0f, 6.65f, z, 0.28f, 10.3f, 0.28f, rzDeg = 90f)
      if ((modIdx and 1) == 0) {
        appendTransformedMesh(neonParts, cubeVerts, 0f, 6.46f, z + 0.16f, 8.6f, 0.08f, 0.05f)
      }

      // Platform streetlamps
      val lampSide = if ((modIdx and 1) == 0) -1f else 1f
      val lampX = lampSide * 6.0f
      val lampZ = z - 9f
      appendTransformedMesh(lampPoleParts, cylArr, lampX, 2.75f, lampZ, 0.14f, 3.3f, 0.14f)
      appendTransformedMesh(lampOrbParts, sphereArr, lampX - lampSide * 0.22f, 4.42f, lampZ, 0.48f, 0.34f, 0.48f)

      // Stylized park trees
      val treeX = -lampSide * 7.8f
      val treeZ = z - 17f
      appendTransformedMesh(treeTrunkParts, cylArr, treeX, 1.75f, treeZ, 0.28f, 1.3f, 0.28f)
      appendTransformedMesh(treeCanopyParts, sphereArr, treeX, 2.95f, treeZ, 1.55f, 1.45f, 1.55f)

      // 4 repeating city skyscrapers per segment
      val placements =
        arrayOf(
          intArrayOf(-1, 7, modIdx * 7 + 1, 0),
          intArrayOf(1, 14, modIdx * 11 + 3, 0),
          intArrayOf(-1, 21, modIdx * 13 + 5, 1),
          intArrayOf(1, 23, modIdx * 17 + 9, 1),
        )
      for (b in placements) {
        val side = b[0]
        val bz = z - b[1].toFloat()
        val seed = b[2]
        val farRow = b[3] == 1
        val h = 15f + ((seed * 37) and 0x1F) * 0.90f
        val w = if (farRow) 8.4f else 6.6f
        val d = if (farRow) 11.5f else 10.2f
        val bx = side * (if (farRow) 19.2f else 13.8f)
        val towerDest = if ((seed and 1) == 0) buildingDarkParts else buildingMidParts
        appendTransformedMesh(towerDest, cubeVerts, bx, h * 0.5f - 1f, bz, w, h, d)

        val winX = bx - side * (w * 0.5f + 0.04f)
        val winDest = if (seed % 3 == 0) cyanWinParts else goldWinParts
        appendTransformedMesh(winDest, cubeVerts, winX, h * 0.48f, bz, 0.08f, 1.4f, d * 0.72f)

        if (!farRow) {
          val topH = h * 0.28f
          appendTransformedMesh(buildingMidParts, cubeVerts, bx, h - 1f + topH * 0.5f, bz, w * 0.68f, topH, d * 0.68f)
          if (seed % 3 == 0) {
            val spireY = h - 1f + topH + 1.6f
            appendTransformedMesh(pillarParts, cylArr, bx, spireY, bz, 0.18f, 3.2f, 0.18f)
            appendTransformedMesh(beaconParts, sphereArr, bx, spireY + 1.7f, bz, 0.34f, 0.34f, 0.34f)
          }
        }
      }
    }

    fun uploadListVbo(list: ArrayList<Float>): Pair<Int, Int> {
      val arr = list.toFloatArray()
      return uploadVbo(arr) to (arr.size / 6)
    }

    uploadListVbo(pillarParts).let { envPillarVbo = it.first; envPillarVertCount = it.second }
    uploadListVbo(neonParts).let { envNeonVbo = it.first; envNeonVertCount = it.second }
    uploadListVbo(lampPoleParts).let { envLampPoleVbo = it.first; envLampPoleVertCount = it.second }
    uploadListVbo(lampOrbParts).let { envLampOrbVbo = it.first; envLampOrbVertCount = it.second }
    uploadListVbo(treeTrunkParts).let { envTreeTrunkVbo = it.first; envTreeTrunkVertCount = it.second }
    uploadListVbo(treeCanopyParts).let { envTreeCanopyVbo = it.first; envTreeCanopyVertCount = it.second }
    uploadListVbo(buildingDarkParts).let { envBuildingDarkVbo = it.first; envBuildingDarkVertCount = it.second }
    uploadListVbo(buildingMidParts).let { envBuildingMidVbo = it.first; envBuildingMidVertCount = it.second }
    uploadListVbo(cyanWinParts).let { envCyanWinVbo = it.first; envCyanWinVertCount = it.second }
    uploadListVbo(goldWinParts).let { envGoldWinVbo = it.first; envGoldWinVertCount = it.second }
    uploadListVbo(beaconParts).let { envBeaconVbo = it.first; envBeaconVertCount = it.second }

    // Ramp wedge
    val rampVerts =
      floatArrayOf(
        -0.5f, 0f, 0.5f, 0f, 0.707f, 0.707f, 0.5f, 0f, 0.5f, 0f, 0.707f, 0.707f, 0.5f, 1f, -0.5f, 0f, 0.707f, 0.707f,
        -0.5f, 0f, 0.5f, 0f, 0.707f, 0.707f, 0.5f, 1f, -0.5f, 0f, 0.707f, 0.707f, -0.5f, 1f, -0.5f, 0f, 0.707f, 0.707f,
        0.5f, 0f, -0.5f, 0f, 0f, -1f, -0.5f, 0f, -0.5f, 0f, 0f, -1f, -0.5f, 1f, -0.5f, 0f, 0f, -1f,
        0.5f, 0f, -0.5f, 0f, 0f, -1f, -0.5f, 1f, -0.5f, 0f, 0f, -1f, 0.5f, 1f, -0.5f, 0f, 0f, -1f,
        -0.5f, 0f, -0.5f, -1f, 0f, 0f, -0.5f, 0f, 0.5f, -1f, 0f, 0f, -0.5f, 1f, -0.5f, -1f, 0f, 0f,
        0.5f, 0f, 0.5f, 1f, 0f, 0f, 0.5f, 0f, -0.5f, 1f, 0f, 0f, 0.5f, 1f, -0.5f, 1f, 0f, 0f,
      )
    rampVertCount = rampVerts.size / 6
    rampVbo = uploadVbo(rampVerts)

    // 12-sided 3D coin cylinder + ground shadow disc
    val sides = 12
    val coinArr = FloatArray(sides * 12 * 6)
    val discArr = FloatArray(sides * 3 * 6)
    var cPtr = 0
    var dPtr = 0
    for (i in 0 until sides) {
      val a0 = (i.toFloat() / sides) * 2f * PI.toFloat()
      val a1 = ((i + 1).toFloat() / sides) * 2f * PI.toFloat()
      val x0 = cos(a0)
      val y0 = sin(a0)
      val x1 = cos(a1)
      val y1 = sin(a1)
      cPtr = writeVert(coinArr, cPtr, 0f, 0f, 0.5f, 0f, 0f, 1f)
      cPtr = writeVert(coinArr, cPtr, x0, y0, 0.5f, 0f, 0f, 1f)
      cPtr = writeVert(coinArr, cPtr, x1, y1, 0.5f, 0f, 0f, 1f)
      cPtr = writeVert(coinArr, cPtr, 0f, 0f, -0.5f, 0f, 0f, -1f)
      cPtr = writeVert(coinArr, cPtr, x1, y1, -0.5f, 0f, 0f, -1f)
      cPtr = writeVert(coinArr, cPtr, x0, y0, -0.5f, 0f, 0f, -1f)
      cPtr = writeVert(coinArr, cPtr, x0, y0, 0.5f, x0, y0, 0f)
      cPtr = writeVert(coinArr, cPtr, x0, y0, -0.5f, x0, y0, 0f)
      cPtr = writeVert(coinArr, cPtr, x1, y1, -0.5f, x1, y1, 0f)
      cPtr = writeVert(coinArr, cPtr, x0, y0, 0.5f, x0, y0, 0f)
      cPtr = writeVert(coinArr, cPtr, x1, y1, -0.5f, x1, y1, 0f)
      cPtr = writeVert(coinArr, cPtr, x1, y1, 0.5f, x1, y1, 0f)

      dPtr = writeVert(discArr, dPtr, 0f, 0f, 0f, 0f, 1f, 0f)
      dPtr = writeVert(discArr, dPtr, x1, 0f, y1, 0f, 1f, 0f)
      dPtr = writeVert(discArr, dPtr, x0, 0f, y0, 0f, 1f, 0f)
    }
    coinVertCount = coinArr.size / 6
    coinVbo = uploadVbo(coinArr)
    discVertCount = discArr.size / 6
    discVbo = uploadVbo(discArr)

    val skyVerts =
      floatArrayOf(
        -1f, -1f, 0.64f, 0.36f, 0.30f, 1f,
        1f, -1f, 0.64f, 0.36f, 0.30f, 1f,
        -1f, 1f, 0.04f, 0.07f, 0.17f, 1f,
        1f, 1f, 0.04f, 0.07f, 0.17f, 1f,
      )
    skyVbo = uploadVbo(skyVerts)
  }

  private fun uploadVbo(arr: FloatArray): Int {
    val buf =
      ByteBuffer.allocateDirect(arr.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(arr)
        position(0)
      }
    val ids = IntArray(1)
    GLES20.glGenBuffers(1, ids, 0)
    val vboId = ids[0]
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId)
    GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, arr.size * 4, buf, GLES20.GL_STATIC_DRAW)
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    return vboId
  }

  private fun buildProgram(vsSource: String, fsSource: String): Int {
    val vs = compileShader(GLES20.GL_VERTEX_SHADER, vsSource)
    val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fsSource)
    val prog = GLES20.glCreateProgram()
    GLES20.glAttachShader(prog, vs)
    GLES20.glAttachShader(prog, fs)
    GLES20.glLinkProgram(prog)
    val linkStatus = IntArray(1)
    GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linkStatus, 0)
    if (linkStatus[0] == 0) {
      Log.e("DashGLRenderer", "Program link failed: ${GLES20.glGetProgramInfoLog(prog)}")
    }
    return prog
  }

  private fun compileShader(type: Int, src: String): Int {
    val shader = GLES20.glCreateShader(type)
    GLES20.glShaderSource(shader, src)
    GLES20.glCompileShader(shader)
    val compiled = IntArray(1)
    GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
    if (compiled[0] == 0) {
      Log.e("DashGLRenderer", "Shader compile failed: ${GLES20.glGetShaderInfoLog(shader)}")
    }
    return shader
  }

  private companion object {
    val TRAIN_PALETTES =
      arrayOf(
        floatArrayOf(0.84f, 0.16f, 0.22f, 0.98f, 0.82f, 0.22f),
        floatArrayOf(0.10f, 0.44f, 0.88f, 0.00f, 0.90f, 1.00f),
        floatArrayOf(0.92f, 0.46f, 0.08f, 0.98f, 0.95f, 0.88f),
        floatArrayOf(0.08f, 0.62f, 0.46f, 0.45f, 1.00f, 0.68f),
      )

    val POWERUP_COLORS =
      arrayOf(
        floatArrayOf(1.0f, 0.22f, 0.35f),
        floatArrayOf(1.0f, 0.52f, 0.08f),
        floatArrayOf(0.08f, 0.94f, 0.55f),
        floatArrayOf(0.05f, 0.88f, 1.0f),
      )

    val GUARD_NAVY = floatArrayOf(0.12f, 0.25f, 0.58f)
    val GUARD_SKIN = floatArrayOf(0.85f, 0.66f, 0.52f)
    val GUARD_PANTS = floatArrayOf(0.08f, 0.12f, 0.24f)

    const val VERTEX_SHADER =
      """
      uniform mat4 uMVPMatrix;
      uniform mat4 uModelMatrix;
      uniform vec3 uVertCamPos;
      uniform float uFogDensity;
      attribute vec4 aPosition;
      attribute vec3 aNormal;
      varying vec3 vWorldPos;
      varying vec3 vWorldNormal;
      varying float vFogFactor;
      void main() {
        vec4 wp = uModelMatrix * aPosition;
        vWorldPos = wp.xyz;
        mat3 normalMat = mat3(uModelMatrix[0].xyz, uModelMatrix[1].xyz, uModelMatrix[2].xyz);
        vWorldNormal = normalize(normalMat * aNormal);
        float d = length(uVertCamPos - wp.xyz) * uFogDensity;
        vFogFactor = clamp(1.0 - exp(-(d * d)), 0.0, 1.0);
        gl_Position = uMVPMatrix * aPosition;
      }
      """

    const val FRAGMENT_SHADER =
      """
      precision mediump float;
      uniform vec4 uColor;
      uniform float uEmissive;
      uniform float uSpecular;
      uniform vec3 uSunDir;
      uniform vec3 uCamPos;
      uniform vec3 uFogColor;
      varying vec3 vWorldPos;
      varying vec3 vWorldNormal;
      varying float vFogFactor;
      void main() {
        vec3 N = normalize(vWorldNormal);
        vec3 L = normalize(uSunDir);
        vec3 V = normalize(uCamPos - vWorldPos);
        vec3 H = normalize(L + V);

        float hemi = N.y * 0.5 + 0.5;
        vec3 ambient = mix(vec3(0.22, 0.22, 0.32), vec3(0.56, 0.64, 0.82), hemi);
        float diff = max(dot(N, L), 0.0);
        vec3 sunLight = vec3(1.0, 0.92, 0.80) * diff * 0.84;

        float ndh = max(dot(N, H), 0.0);
        float ndh4 = ndh * ndh;
        ndh4 = ndh4 * ndh4;
        float spec = (ndh4 * ndh4) * uSpecular * 0.68;

        float f = 1.0 - max(dot(N, V), 0.0);
        float rim = (f * f * f) * 0.18;

        vec3 lit = uColor.rgb * (ambient + sunLight + uEmissive) + vec3(spec + rim * 0.35);
        vec3 finalRgb = mix(lit, uFogColor, vFogFactor);
        gl_FragColor = vec4(finalRgb, uColor.a);
      }
      """

    const val SKY_VERTEX_SHADER =
      """
      attribute vec2 aPosition;
      attribute vec4 aColor;
      varying vec4 vColor;
      void main() {
        vColor = aColor;
        gl_Position = vec4(aPosition, 0.99, 1.0);
      }
      """

    const val SKY_FRAGMENT_SHADER =
      """
      precision mediump float;
      varying vec4 vColor;
      void main() {
        gl_FragColor = vColor;
      }
      """
  }
}
