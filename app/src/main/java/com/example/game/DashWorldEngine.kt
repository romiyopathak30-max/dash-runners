package com.example.game

import com.example.audio.SoundCue
import com.example.audio.SoundEffectsEngine
import com.example.data.model.RunnerProfile
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

data class WorldObstacle(
  val id: Int,
  val kind: ObstacleKind,
  val lane: Int,
  var zCenter: Float,
  val length: Float,
  val height: Float,
  val extraSpeed: Float = 0f,
  val colorVariant: Int = 0,
  var hitConsumed: Boolean = false,
) {
  val xCenter: Float
    get() = lane * DashWorldEngine.LANE_SPACING

  val zNear: Float
    get() = zCenter + length * 0.5f

  val zFar: Float
    get() = zCenter - length * 0.5f
}

data class WorldCoin(
  val id: Int,
  var x: Float,
  var y: Float,
  var z: Float,
  var collected: Boolean = false,
  var magnetized: Boolean = false,
)

data class WorldPowerUp(
  val id: Int,
  val kind: PowerUpKind,
  val lane: Int,
  var y: Float,
  var z: Float,
  var collected: Boolean = false,
) {
  val x: Float
    get() = lane * DashWorldEngine.LANE_SPACING
}

data class WorldParticle(
  var x: Float,
  var y: Float,
  var z: Float,
  var vx: Float,
  var vy: Float,
  var vz: Float,
  var r: Float,
  var g: Float,
  var b: Float,
  var life: Float,
  var maxLife: Float,
  var size: Float,
)

class DashWorldEngine(
  private val soundEngine: SoundEffectsEngine? = null,
) {
  @Volatile
  var phase: GamePhase = GamePhase.LOBBY
    private set

  // Runner state
  @Volatile
  var targetLane: Int = 0
    private set
  var prevLane: Int = 0
    private set
  @Volatile
  var runnerX: Float = 0f
    private set
  @Volatile
  var runnerY: Float = 0f
    private set
  var runnerVy: Float = 0f
    private set
  @Volatile
  var laneTiltDeg: Float = 0f
    private set
  @Volatile
  var laneYawDeg: Float = 0f
    private set
  var laneSwitchTimer: Float = 0f
    private set

  @Volatile
  var isGrounded: Boolean = true
    private set
  @Volatile
  var isRolling: Boolean = false
    private set
  var rollTimer: Float = 0f
    private set
  var coyoteTimer: Float = 0f
    private set
  @Volatile
  var sprintCycle: Float = 0f
    private set

  // World & scoring
  @Volatile
  var worldDistance: Float = 0f
    private set
  @Volatile
  var worldSpeed: Float = BASE_SPEED
    private set
  var scoreFloat: Float = 0f
    private set
  @Volatile
  var coinsThisRun: Long = 0L
    private set
  @Volatile
  var elapsedTime: Float = 0f
    private set

  // Power-ups & survivability
  var magnetRemaining: Float = 0f
    private set
  var magnetTotal: Float = 8f
    private set
  var jetpackRemaining: Float = 0f
    private set
  var jetpackTotal: Float = 7f
    private set
  var sneakersRemaining: Float = 0f
    private set
  var sneakersTotal: Float = 9f
    private set
  var multiplierRemaining: Float = 0f
    private set
  var multiplierTotal: Float = 10f
    private set

  var hoverboardActive: Boolean = false
    private set
  var hoverboardRemaining: Float = 0f
    private set
  var hoverboardsAvailable: Long = 3L
    private set
  var boardUsedThisRun: Boolean = false
    private set

  var invulnerableTimer: Float = 0f
    private set
  var chaserDangerTimer: Float = 3.2f
    private set
  var cameraShake: Float = 0f
    private set
  var bannerText: String? = null
    private set
  var bannerTimer: Float = 0f
    private set

  var selectedRunnerId: String = "blaze"
  var selectedBoardId: String = "aero"
  private var profileSnapshot: RunnerProfile = RunnerProfile()

  val obstacles = ArrayList<WorldObstacle>(64)
  val coins = ArrayList<WorldCoin>(128)
  val powerUps = ArrayList<WorldPowerUp>(16)
  val particles = ArrayList<WorldParticle>(64)

  private var nextEntityId = 1
  private var nextWaveDistance = 32f
  private var nextJetpackCoinZ = -35f
  private var jetpackCoinLane = 0
  private var queuedSwipe: SwipeDirection? = null
  private var onGameOverCallback: ((RunSummary) -> Unit)? = null
  private val rng = Random(System.currentTimeMillis())

  @Synchronized
  fun applyProfile(profile: RunnerProfile) {
    profileSnapshot = profile
    selectedRunnerId = profile.selectedRunnerId
    selectedBoardId = profile.selectedBoardId
    if (phase == GamePhase.LOBBY) {
      hoverboardsAvailable = profile.hoverboardsCount
    }
  }

  @Synchronized
  fun startNewRun(profile: RunnerProfile, onGameOver: (RunSummary) -> Unit) {
    applyProfile(profile)
    onGameOverCallback = onGameOver
    obstacles.clear()
    coins.clear()
    powerUps.clear()
    particles.clear()

    targetLane = 0
    prevLane = 0
    runnerX = 0f
    runnerY = 0f
    runnerVy = 0f
    laneTiltDeg = 0f
    laneYawDeg = 0f
    laneSwitchTimer = 0f
    isGrounded = true
    isRolling = false
    rollTimer = 0f
    coyoteTimer = 0f
    sprintCycle = 0f

    worldDistance = 0f
    worldSpeed = BASE_SPEED
    scoreFloat = 0f
    coinsThisRun = 0L
    elapsedTime = 0f

    magnetTotal = 8f + (profile.magnetLevel - 1).coerceAtLeast(0) * 2.5f
    jetpackTotal = 6.5f + (profile.jetpackLevel - 1).coerceAtLeast(0) * 2.0f
    sneakersTotal = 9f + (profile.sneakersLevel - 1).coerceAtLeast(0) * 2.5f
    multiplierTotal = 10f + (profile.multiplierLevel - 1).coerceAtLeast(0) * 2.5f

    magnetRemaining = 0f
    jetpackRemaining = 0f
    sneakersRemaining = 0f
    multiplierRemaining = 0f

    hoverboardActive = false
    hoverboardRemaining = 0f
    hoverboardsAvailable = profile.hoverboardsCount
    boardUsedThisRun = false

    invulnerableTimer = 0f
    chaserDangerTimer = 3.2f
    cameraShake = 0f
    queuedSwipe = null
    showBanner("GO! SWIPE TO DODGE", 1.8f)

    nextWaveDistance = 28f
    seedInitialTrack()
    phase = GamePhase.PLAYING
    soundEngine?.play(SoundCue.UI_TAP)
  }

  @Synchronized
  fun pauseGame() {
    if (phase == GamePhase.PLAYING) {
      phase = GamePhase.PAUSED
    }
  }

  @Synchronized
  fun resumeGame() {
    if (phase == GamePhase.PAUSED) {
      phase = GamePhase.PLAYING
    }
  }

  @Synchronized
  fun returnToLobby() {
    obstacles.clear()
    coins.clear()
    powerUps.clear()
    particles.clear()
    targetLane = 0
    runnerX = 0f
    runnerY = 0f
    runnerVy = 0f
    isGrounded = true
    isRolling = false
    hoverboardActive = false
    jetpackRemaining = 0f
    magnetRemaining = 0f
    sneakersRemaining = 0f
    multiplierRemaining = 0f
    phase = GamePhase.LOBBY
  }

  @Synchronized
  fun handleSwipe(direction: SwipeDirection) {
    if (phase != GamePhase.PLAYING) return

    when (direction) {
      SwipeDirection.LEFT -> {
        if (targetLane > -1) {
          prevLane = targetLane
          targetLane -= 1
          laneSwitchTimer = 0.18f
          soundEngine?.play(SoundCue.LANE_SWIPE)
        } else {
          // Wall bump nudge without lethal crash unless already stumbling
          triggerSideBump(-1)
        }
      }
      SwipeDirection.RIGHT -> {
        if (targetLane < 1) {
          prevLane = targetLane
          targetLane += 1
          laneSwitchTimer = 0.18f
          soundEngine?.play(SoundCue.LANE_SWIPE)
        } else {
          triggerSideBump(1)
        }
      }
      SwipeDirection.UP -> {
        if (jetpackRemaining > 0f) return
        if (isGrounded || coyoteTimer > 0f || runnerY <= currentSupportFloorY() + 0.28f) {
          isRolling = false
          rollTimer = 0f
          coyoteTimer = 0f
          isGrounded = false
          val jumpV = if (sneakersRemaining > 0f) SNEAKERS_JUMP_VEL else NORMAL_JUMP_VEL
          runnerVy = jumpV
          spawnBurst(runnerX, runnerY + 0.1f, 0f, 0.2f, 0.9f, 1.0f, 8)
          soundEngine?.play(SoundCue.JUMP)
        } else {
          queuedSwipe = SwipeDirection.UP
        }
      }
      SwipeDirection.DOWN -> {
        if (jetpackRemaining > 0f) return
        if (!isGrounded && runnerY > currentSupportFloorY() + 0.15f) {
          // Snappy fast-fall slam
          runnerVy = -38f
        }
        isRolling = true
        rollTimer = 0.68f
        soundEngine?.play(SoundCue.ROLL)
      }
    }
  }

  @Synchronized
  fun activateHoverboard() {
    if (phase != GamePhase.PLAYING) return
    if (hoverboardActive || jetpackRemaining > 0f) return
    if (hoverboardsAvailable <= 0L) {
      showBanner("NO HOVERBOARDS LEFT", 1.2f)
      return
    }
    hoverboardsAvailable -= 1L
    hoverboardActive = true
    hoverboardRemaining = 24f
    boardUsedThisRun = true
    showBanner("HOVERBOARD ENGAGED!", 1.6f)
    spawnBurst(runnerX, runnerY + 0.3f, 0f, 0.1f, 0.95f, 1.0f, 16)
    soundEngine?.play(SoundCue.HOVERBOARD_ON)
  }

  @Synchronized
  fun step(rawDt: Float) {
    val dt = rawDt.coerceIn(0f, 0.033f)
    elapsedTime += dt

    if (phase == GamePhase.LOBBY) {
      // Gentle lobby idle jog & track scroll
      worldSpeed = 14f
      worldDistance += worldSpeed * dt
      sprintCycle += dt * 7.5f
      runnerX += (0f - runnerX) * min(1f, dt * 8f)
      runnerY = 0f
      return
    }

    if (phase != GamePhase.PLAYING) {
      updateParticles(dt)
      cameraShake = max(0f, cameraShake - dt * 2.8f)
      return
    }

    // Ramp speed smoothly from 23 m/s up to 52 m/s
    val targetSpeed = min(MAX_SPEED, BASE_SPEED + worldDistance * 0.013f + (if (jetpackRemaining > 0f) 11f else 0f))
    worldSpeed += (targetSpeed - worldSpeed) * (1f - exp(-2.8f * dt))
    val dz = worldSpeed * dt
    worldDistance += dz

    val activeMult = if (multiplierRemaining > 0f) 2 else 1
    scoreFloat += dz * 1.4f * activeMult

    // Timers
    if (laneSwitchTimer > 0f) laneSwitchTimer = max(0f, laneSwitchTimer - dt)
    if (invulnerableTimer > 0f) invulnerableTimer = max(0f, invulnerableTimer - dt)
    if (chaserDangerTimer > 0f) chaserDangerTimer = max(0f, chaserDangerTimer - dt)
    if (cameraShake > 0f) cameraShake = max(0f, cameraShake - dt * 3.2f)
    if (bannerTimer > 0f) {
      bannerTimer -= dt
      if (bannerTimer <= 0f) bannerText = null
    }

    if (magnetRemaining > 0f) magnetRemaining = max(0f, magnetRemaining - dt)
    if (sneakersRemaining > 0f) sneakersRemaining = max(0f, sneakersRemaining - dt)
    if (multiplierRemaining > 0f) multiplierRemaining = max(0f, multiplierRemaining - dt)
    if (hoverboardActive) {
      hoverboardRemaining -= dt
      if (hoverboardRemaining <= 0f) {
        hoverboardActive = false
        hoverboardRemaining = 0f
      } else if (rng.nextFloat() < 0.45f) {
        spawnTrailParticle(runnerX, runnerY + 0.15f, 0.5f, 0.1f, 0.9f, 1.0f)
      }
    }

    // Lane lateral exponential smoothing + banking roll & turn yaw
    val desiredX = targetLane * LANE_SPACING
    val dx = desiredX - runnerX
    runnerX += dx * (1f - exp(-22f * dt))
    val targetTilt = (dx * 9.5f).coerceIn(-22f, 22f)
    laneTiltDeg += (targetTilt - laneTiltDeg) * (1f - exp(-18f * dt))
    val targetYaw = (dx * -7.0f).coerceIn(-18f, 18f)
    laneYawDeg += (targetYaw - laneYawDeg) * (1f - exp(-17f * dt))

    // Roll timer
    if (isRolling) {
      rollTimer -= dt
      if (rollTimer <= 0f) {
        isRolling = false
      }
    }

    // Advance obstacles, coins, powerups toward +Z
    advanceWorldEntities(dz, dt)

    // Vertical physics (Jetpack vs Gravity + train roof / ramp support)
    if (jetpackRemaining > 0f) {
      jetpackRemaining -= dt
      isRolling = false
      isGrounded = false
      coyoteTimer = 0f
      val targetJetY = 7.6f + sin(elapsedTime * 4f) * 0.25f
      runnerY += (targetJetY - runnerY) * (1f - exp(-7f * dt))
      runnerVy = 0f
      spawnTrailParticle(runnerX - 0.25f, runnerY + 0.7f, 0.45f, 1.0f, 0.55f, 0.1f)
      spawnTrailParticle(runnerX + 0.25f, runnerY + 0.7f, 0.45f, 1.0f, 0.78f, 0.2f)

      // Spawn continuous sky coin ribbons ahead
      nextJetpackCoinZ += dz
      if (nextJetpackCoinZ >= -110f) {
        if (rng.nextFloat() < 0.35f) {
          jetpackCoinLane = (jetpackCoinLane + (if (rng.nextBoolean()) 1 else -1)).coerceIn(-1, 1)
        }
        val spawnZ = -135f
        for (i in 0 until 4) {
          coins.add(WorldCoin(nextId(), jetpackCoinLane * LANE_SPACING, 7.6f, spawnZ - i * 3.0f))
        }
        nextJetpackCoinZ = -135f
      }

      if (jetpackRemaining <= 0f) {
        jetpackRemaining = 0f
        invulnerableTimer = max(invulnerableTimer, 1.6f)
        clearObstaclesNear(zMin = -18f, zMax = 10f)
        showBanner("LANDING SHIELD ACTIVE", 1.3f)
      }
    } else {
      val wasGrounded = isGrounded
      val supportY = currentSupportFloorY()
      runnerVy -= GRAVITY * dt
      runnerY += runnerVy * dt
      if (runnerY <= supportY) {
        if (!wasGrounded && runnerVy < -14f) {
          spawnBurst(runnerX, supportY + 0.05f, 0f, 0.75f, 0.80f, 0.90f, 6)
        }
        runnerY = supportY
        runnerVy = 0f
        isGrounded = true
        coyoteTimer = 0.10f
        if (queuedSwipe == SwipeDirection.UP) {
          queuedSwipe = null
          handleSwipe(SwipeDirection.UP)
        }
      } else {
        isGrounded = false
        coyoteTimer = max(0f, coyoteTimer - dt)
      }
    }

    sprintCycle += dt * (worldSpeed * 0.56f)

    // Spawn upcoming obstacle waves
    if (worldDistance >= nextWaveDistance) {
      spawnNextPatternWave()
      val waveSpacing = max(23f, 34f - (worldSpeed - BASE_SPEED) * 0.22f)
      nextWaveDistance = worldDistance + waveSpacing
    }

    // Coin pickup + magnet pull + power-up pickup
    processCollectibles(dt)

    // Obstacle collisions (skipped while flying high on Jetpack)
    if (jetpackRemaining <= 0f) {
      processCollisions()
    }

    updateParticles(dt)
  }

  private fun advanceWorldEntities(dz: Float, dt: Float) {
    for (i in obstacles.size - 1 downTo 0) {
      val obs = obstacles[i]
      obs.zCenter += dz + obs.extraSpeed * dt
      if (obs.zFar > 18f) {
        obstacles.removeAt(i)
      }
    }

    for (i in coins.size - 1 downTo 0) {
      val coin = coins[i]
      coin.z += dz
      if (coin.collected || coin.z > 16f) {
        coins.removeAt(i)
      }
    }

    for (i in powerUps.size - 1 downTo 0) {
      val pu = powerUps[i]
      pu.z += dz
      if (pu.collected || pu.z > 16f) {
        powerUps.removeAt(i)
      }
    }
  }

  fun currentSupportFloorY(): Float {
    var floor = 0f
    for (i in 0 until obstacles.size) {
      val obs = obstacles[i]
      if (abs(runnerX - obs.xCenter) > 1.05f) continue
      if (obs.zNear < -0.55f || obs.zFar > 0.55f) continue
      when (obs.kind) {
        ObstacleKind.TRAIN_RAMP -> {
          val u = ((obs.zNear - 0f) / obs.length).coerceIn(0f, 1f)
          val rampY = u * TRAIN_HEIGHT
          if (runnerY >= rampY - 0.85f) {
            floor = max(floor, rampY)
          }
        }
        ObstacleKind.PARKED_TRAIN, ObstacleKind.MOVING_TRAIN -> {
          if (runnerY >= TRAIN_HEIGHT - 0.65f) {
            floor = max(floor, TRAIN_HEIGHT)
          }
        }
        else -> {}
      }
    }
    return floor
  }

  private fun processCollectibles(dt: Float) {
    val magnetActive = magnetRemaining > 0f
    val runnerCenterY = runnerY + if (isRolling) 0.45f else 0.95f

    for (i in 0 until coins.size) {
      val coin = coins[i]
      if (coin.collected) continue
      if (magnetActive) {
        val distSq =
          (coin.x - runnerX) * (coin.x - runnerX) +
            (coin.y - runnerCenterY) * (coin.y - runnerCenterY) +
            coin.z * coin.z
        if (distSq < 21f * 21f && coin.z > -22f && coin.z < 5f) {
          coin.magnetized = true
        }
      }
      if (coin.magnetized) {
        val pull = min(1f, dt * 15f)
        coin.x += (runnerX - coin.x) * pull
        coin.y += (runnerCenterY - coin.y) * pull
        coin.z += (0f - coin.z) * pull
      }

      val dx = abs(coin.x - runnerX)
      val dy = abs(coin.y - runnerCenterY)
      val dz = abs(coin.z)
      if (dx < 1.05f && dy < 1.15f && dz < 1.15f) {
        coin.collected = true
        coinsThisRun += 1L
        val mult = if (multiplierRemaining > 0f) 2 else 1
        scoreFloat += 25f * mult
        spawnBurst(coin.x, coin.y, coin.z, 1.0f, 0.86f, 0.15f, 5)
        soundEngine?.play(SoundCue.COIN)
      }
    }

    for (i in 0 until powerUps.size) {
      val pu = powerUps[i]
      if (pu.collected) continue
      val dx = abs(pu.x - runnerX)
      val dy = abs(pu.y - runnerCenterY)
      val dz = abs(pu.z)
      if (dx < 1.15f && dy < 1.25f && dz < 1.25f) {
        pu.collected = true
        applyPowerUp(pu.kind)
      }
    }
  }

  private fun applyPowerUp(kind: PowerUpKind) {
    soundEngine?.play(SoundCue.POWERUP)
    spawnBurst(runnerX, runnerY + 1.0f, 0f, 0.2f, 0.95f, 1.0f, 14)
    when (kind) {
      PowerUpKind.MAGNET -> {
        magnetRemaining = magnetTotal
        showBanner("COIN MAGNET!", 1.5f)
      }
      PowerUpKind.JETPACK -> {
        jetpackRemaining = jetpackTotal
        hoverboardActive = false
        isRolling = false
        showBanner("JETPACK BOOST!", 1.5f)
      }
      PowerUpKind.SNEAKERS -> {
        sneakersRemaining = sneakersTotal
        showBanner("SUPER SNEAKERS!", 1.5f)
      }
      PowerUpKind.MULTIPLIER -> {
        multiplierRemaining = multiplierTotal
        showBanner("2X SCORE MULTIPLIER!", 1.5f)
      }
    }
  }

  private fun processCollisions() {
    for (i in 0 until obstacles.size) {
      val obs = obstacles[i]
      if (obs.hitConsumed) continue
      if (obs.kind == ObstacleKind.TRAIN_RAMP) continue

      val xDist = abs(runnerX - obs.xCenter)
      val halfTrack = if (obs.kind == ObstacleKind.PARKED_TRAIN || obs.kind == ObstacleKind.MOVING_TRAIN) 0.96f else 0.90f
      if (xDist > halfTrack) continue
      if (obs.zNear < -0.45f || obs.zFar > 0.45f) continue

      val lethal =
        when (obs.kind) {
          ObstacleKind.PARKED_TRAIN, ObstacleKind.MOVING_TRAIN -> {
            runnerY < TRAIN_HEIGHT - 0.62f
          }
          ObstacleKind.LOW_BARRIER -> {
            runnerY < 0.85f
          }
          ObstacleKind.HIGH_BARRIER -> {
            !isRolling && runnerY < 2.45f
          }
          ObstacleKind.TALL_BARRIER -> {
            runnerY < 2.35f
          }
          ObstacleKind.TRAIN_RAMP -> false
        }

      if (!lethal) continue
      obs.hitConsumed = true

      // Side-swipe stumble check when player just changed lanes and clipped edge
      val isEdgeClip = laneSwitchTimer > 0f && xDist > 0.68f
      if (isEdgeClip && chaserDangerTimer <= 0f) {
        triggerStumble(obs)
        return
      }

      if (invulnerableTimer > 0f) {
        continue
      }

      if (hoverboardActive) {
        triggerHoverboardShieldBlast()
        return
      }

      // Fatal crash
      triggerGameOver()
      return
    }
  }

  private fun triggerSideBump(wallDir: Int) {
    if (chaserDangerTimer > 0f) {
      if (hoverboardActive) {
        triggerHoverboardShieldBlast()
      } else {
        triggerGameOver()
      }
    } else {
      chaserDangerTimer = 4.5f
      cameraShake = 0.45f
      laneTiltDeg = -wallDir * 14f
      showBanner("WATCH OUT! GUARD CLOSE!", 1.4f)
      soundEngine?.play(SoundCue.STUMBLE)
    }
  }

  private fun triggerStumble(obs: WorldObstacle) {
    targetLane = prevLane
    laneSwitchTimer = 0f
    cameraShake = 0.55f
    chaserDangerTimer = 5.0f
    invulnerableTimer = 0.65f
    showBanner("STUMBLE! GUARD RIGHT BEHIND!", 1.6f)
    spawnBurst(obs.xCenter, runnerY + 0.9f, 0f, 1.0f, 0.5f, 0.1f, 10)
    soundEngine?.play(SoundCue.STUMBLE)
  }

  private fun triggerHoverboardShieldBlast() {
    hoverboardActive = false
    hoverboardRemaining = 0f
    invulnerableTimer = 1.8f
    cameraShake = 0.75f
    clearObstaclesNear(zMin = -22f, zMax = 10f)
    showBanner("BOARD SHIELD SAVED YOU!", 1.7f)
    spawnBurst(runnerX, runnerY + 0.8f, 0f, 0.1f, 0.95f, 1.0f, 24)
    soundEngine?.play(SoundCue.BOARD_SHIELD)
  }

  private fun clearObstaclesNear(zMin: Float, zMax: Float) {
    for (i in obstacles.size - 1 downTo 0) {
      val obs = obstacles[i]
      if (obs.zNear >= zMin && obs.zFar <= zMax) {
        obstacles.removeAt(i)
      }
    }
  }

  private fun triggerGameOver() {
    phase = GamePhase.GAME_OVER
    cameraShake = 1.15f
    spawnBurst(runnerX, runnerY + 0.9f, 0f, 1.0f, 0.25f, 0.2f, 26)
    soundEngine?.play(SoundCue.CRASH)

    val finalScore = scoreFloat.toLong()
    val finalDistance = worldDistance.toLong()
    val summary =
      RunSummary(
        score = finalScore,
        distanceMeters = finalDistance,
        coinsCollected = coinsThisRun,
        isNewHighScore = finalScore > profileSnapshot.highScore,
        boardsRemaining = hoverboardsAvailable,
        boardUsed = boardUsedThisRun,
      )
    onGameOverCallback?.invoke(summary)
  }

  private fun seedInitialTrack() {
    val z = -55f
    spawnCoinLine(lane = 0, startZ = z, count = 5, y = 0.85f)
    nextWaveDistance = 30f
  }

  private fun spawnNextPatternWave() {
    val baseZ = -215f
    val pattern = rng.nextInt(8)
    when (pattern) {
      0 -> {
        // Ramp + Parked Train on 1 lane, Low Barrier + arc on another, Coin line on open lane
        val lanes = listOf(-1, 0, 1).shuffled(rng)
        val trainLane = lanes[0]
        val barrierLane = lanes[1]
        val coinLane = lanes[2]

        obstacles.add(WorldObstacle(nextId(), ObstacleKind.TRAIN_RAMP, trainLane, baseZ + 12.5f, RAMP_LENGTH, TRAIN_HEIGHT))
        obstacles.add(WorldObstacle(nextId(), ObstacleKind.PARKED_TRAIN, trainLane, baseZ - 1.5f, 19f, TRAIN_HEIGHT, colorVariant = rng.nextInt(4)))
        spawnCoinLine(trainLane, baseZ + 6f, 6, TRAIN_HEIGHT + 0.85f, stepZ = 2.9f)

        obstacles.add(WorldObstacle(nextId(), ObstacleKind.LOW_BARRIER, barrierLane, baseZ + 4f, 1.2f, 1.05f))
        spawnCoinArc(barrierLane, baseZ + 4f, peakY = 2.5f)

        spawnCoinLine(coinLane, baseZ + 10f, 5, 0.85f)
      }
      1 -> {
        // Double Parked Trains (one with ramp) + 1 open/roll lane
        val openLane = rng.nextInt(-1, 2)
        val rampLane = listOf(-1, 0, 1).first { it != openLane }
        for (l in -1..1) {
          if (l == openLane) {
            if (rng.nextBoolean()) {
              obstacles.add(WorldObstacle(nextId(), ObstacleKind.HIGH_BARRIER, l, baseZ + 2f, 1.2f, 2.75f))
            }
            spawnCoinLine(l, baseZ + 8f, 6, 0.75f)
          } else {
            if (l == rampLane) {
              obstacles.add(WorldObstacle(nextId(), ObstacleKind.TRAIN_RAMP, l, baseZ + 12.5f, RAMP_LENGTH, TRAIN_HEIGHT))
              spawnCoinLine(l, baseZ + 4f, 6, TRAIN_HEIGHT + 0.85f, stepZ = 2.8f)
            }
            obstacles.add(WorldObstacle(nextId(), ObstacleKind.PARKED_TRAIN, l, baseZ - 1.5f, 19f, TRAIN_HEIGHT, colorVariant = rng.nextInt(4)))
          }
        }
      }
      2 -> {
        // Oncoming Moving Express Train + flanking obstacles
        val expressLane = rng.nextInt(-1, 2)
        obstacles.add(
          WorldObstacle(
            id = nextId(),
            kind = ObstacleKind.MOVING_TRAIN,
            lane = expressLane,
            zCenter = baseZ - 26f,
            length = 22f,
            height = TRAIN_HEIGHT,
            extraSpeed = 16f,
            colorVariant = rng.nextInt(4),
          )
        )
        val safeLane = listOf(-1, 0, 1).first { it != expressLane }
        spawnCoinLine(safeLane, baseZ + 8f, 6, 0.85f)
        val otherLane = listOf(-1, 0, 1).first { it != expressLane && it != safeLane }
        obstacles.add(WorldObstacle(nextId(), ObstacleKind.LOW_BARRIER, otherLane, baseZ + 3f, 1.2f, 1.05f))
      }
      3 -> {
        // Triple Hurdle Slalom (Low + High + Open/Coins)
        val lanes = listOf(-1, 0, 1).shuffled(rng)
        obstacles.add(WorldObstacle(nextId(), ObstacleKind.LOW_BARRIER, lanes[0], baseZ + 4f, 1.2f, 1.05f))
        spawnCoinArc(lanes[0], baseZ + 4f, peakY = 2.45f)

        obstacles.add(WorldObstacle(nextId(), ObstacleKind.HIGH_BARRIER, lanes[1], baseZ + 4f, 1.2f, 2.75f))
        spawnCoinLine(lanes[1], baseZ + 8f, 4, 0.6f)

        spawnCoinLine(lanes[2], baseZ + 10f, 5, 0.85f)
      }
      4 -> {
        // Power-Up Showcase + Tall Barrier dodge
        val puLane = rng.nextInt(-1, 2)
        val kinds = PowerUpKind.entries
        val kind = kinds[rng.nextInt(kinds.size)]
        powerUps.add(WorldPowerUp(nextId(), kind, puLane, 1.15f, baseZ + 2f))
        spawnCoinLine(puLane, baseZ + 11f, 3, 0.85f)

        val blockLane = listOf(-1, 0, 1).first { it != puLane }
        obstacles.add(WorldObstacle(nextId(), ObstacleKind.TALL_BARRIER, blockLane, baseZ + 2f, 1.2f, 2.65f))
      }
      5 -> {
        // Double Express Staggered + safe center
        val l1 = if (rng.nextBoolean()) -1 else 1
        val l2 = 0
        obstacles.add(WorldObstacle(nextId(), ObstacleKind.MOVING_TRAIN, l1, baseZ - 18f, 20f, TRAIN_HEIGHT, extraSpeed = 14f, colorVariant = rng.nextInt(4)))
        obstacles.add(WorldObstacle(nextId(), ObstacleKind.LOW_BARRIER, l2, baseZ + 5f, 1.2f, 1.05f))
        spawnCoinArc(l2, baseZ + 5f, peakY = 2.4f)
        spawnCoinLine(-l1, baseZ + 9f, 5, 0.85f)
      }
      6 -> {
        // Long Express Roof Run (Ramp + 2-car train)
        val lane = rng.nextInt(-1, 2)
        obstacles.add(WorldObstacle(nextId(), ObstacleKind.TRAIN_RAMP, lane, baseZ + 14.5f, RAMP_LENGTH, TRAIN_HEIGHT))
        obstacles.add(WorldObstacle(nextId(), ObstacleKind.PARKED_TRAIN, lane, baseZ - 6.0f, 32f, TRAIN_HEIGHT, colorVariant = rng.nextInt(4)))
        spawnCoinLine(lane, baseZ + 7f, 10, TRAIN_HEIGHT + 0.85f, stepZ = 2.7f)
        val sideLane = listOf(-1, 0, 1).first { it != lane }
        obstacles.add(WorldObstacle(nextId(), ObstacleKind.HIGH_BARRIER, sideLane, baseZ + 2f, 1.2f, 2.75f))
      }
      else -> {
        // Double Low Barriers + 1 High Barrier
        val highLane = rng.nextInt(-1, 2)
        for (l in -1..1) {
          if (l == highLane) {
            obstacles.add(WorldObstacle(nextId(), ObstacleKind.HIGH_BARRIER, l, baseZ + 3f, 1.2f, 2.75f))
            spawnCoinLine(l, baseZ + 8f, 4, 0.55f)
          } else {
            obstacles.add(WorldObstacle(nextId(), ObstacleKind.LOW_BARRIER, l, baseZ + 3f, 1.2f, 1.05f))
            spawnCoinArc(l, baseZ + 3f, peakY = 2.4f)
          }
        }
      }
    }
  }

  private fun spawnCoinLine(lane: Int, startZ: Float, count: Int, y: Float, stepZ: Float = 2.8f) {
    val x = lane * LANE_SPACING
    for (i in 0 until count) {
      coins.add(WorldCoin(nextId(), x, y, startZ - i * stepZ))
    }
  }

  private fun spawnCoinArc(lane: Int, centerZ: Float, peakY: Float) {
    val x = lane * LANE_SPACING
    for (i in -2..2) {
      val t = i / 2.0f
      val y = 0.85f + (peakY - 0.85f) * (1f - t * t)
      coins.add(WorldCoin(nextId(), x, y, centerZ - i * 2.2f))
    }
  }

  private fun spawnBurst(x: Float, y: Float, z: Float, r: Float, g: Float, b: Float, count: Int) {
    for (i in 0 until count) {
      if (particles.size >= MAX_PARTICLES) particles.removeAt(0)
      val angle = rng.nextFloat() * 2f * PI.toFloat()
      val spd = 2.5f + rng.nextFloat() * 4.5f
      particles.add(
        WorldParticle(
          x = x,
          y = y,
          z = z,
          vx = cos(angle) * spd,
          vy = 2.2f + rng.nextFloat() * 4.5f,
          vz = sin(angle) * spd,
          r = r,
          g = g,
          b = b,
          life = 0.45f,
          maxLife = 0.45f,
          size = 0.22f + rng.nextFloat() * 0.14f,
        )
      )
    }
  }

  private fun spawnTrailParticle(x: Float, y: Float, z: Float, r: Float, g: Float, b: Float) {
    if (particles.size >= MAX_PARTICLES) particles.removeAt(0)
    particles.add(
      WorldParticle(
        x = x + (rng.nextFloat() - 0.5f) * 0.22f,
        y = y + (rng.nextFloat() - 0.5f) * 0.15f,
        z = z,
        vx = (rng.nextFloat() - 0.5f) * 1.2f,
        vy = -0.8f + rng.nextFloat() * 0.6f,
        vz = 6.5f,
        r = r,
        g = g,
        b = b,
        life = 0.28f,
        maxLife = 0.28f,
        size = 0.24f,
      )
    )
  }

  private fun updateParticles(dt: Float) {
    for (i in particles.size - 1 downTo 0) {
      val p = particles[i]
      p.life -= dt
      if (p.life <= 0f) {
        particles.removeAt(i)
      } else {
        p.x += p.vx * dt
        p.y += p.vy * dt
        p.z += p.vz * dt
        p.vy -= 9.5f * dt
      }
    }
  }

  private fun showBanner(text: String, duration: Float) {
    bannerText = text
    bannerTimer = duration
  }

  private fun nextId(): Int = nextEntityId++

  @Synchronized
  fun copyRenderSnapshot(
    obsOut: ArrayList<WorldObstacle>,
    coinsOut: ArrayList<WorldCoin>,
    puOut: ArrayList<WorldPowerUp>,
    partOut: ArrayList<WorldParticle>,
  ) {
    obsOut.clear()
    obsOut.addAll(obstacles)
    coinsOut.clear()
    coinsOut.addAll(coins)
    puOut.clear()
    puOut.addAll(powerUps)
    partOut.clear()
    partOut.addAll(particles)
  }

  @Synchronized
  fun hudSnapshot(): HudSnapshot {
    val activePowers =
      if (magnetRemaining <= 0f && jetpackRemaining <= 0f && sneakersRemaining <= 0f && multiplierRemaining <= 0f) {
        emptyList()
      } else {
        buildList(4) {
          if (magnetRemaining > 0f) add(ActivePowerUpUi(PowerUpKind.MAGNET, magnetRemaining, magnetTotal))
          if (jetpackRemaining > 0f) add(ActivePowerUpUi(PowerUpKind.JETPACK, jetpackRemaining, jetpackTotal))
          if (sneakersRemaining > 0f) add(ActivePowerUpUi(PowerUpKind.SNEAKERS, sneakersRemaining, sneakersTotal))
          if (multiplierRemaining > 0f) add(ActivePowerUpUi(PowerUpKind.MULTIPLIER, multiplierRemaining, multiplierTotal))
        }
      }
    return HudSnapshot(
      phase = phase,
      score = scoreFloat.toLong(),
      distanceMeters = worldDistance.toLong(),
      coinsThisRun = coinsThisRun,
      scoreMultiplier = if (multiplierRemaining > 0f) 2 else 1,
      speedKmh = (worldSpeed * 2.4f).toInt(),
      hoverboardActive = hoverboardActive,
      hoverboardRemainingSec = hoverboardRemaining,
      hoverboardsAvailable = hoverboardsAvailable,
      chaserAlert = chaserDangerTimer > 0f,
      invulnerableShieldSec = invulnerableTimer,
      activePowerUps = activePowers,
      bannerEvent = bannerText,
    )
  }

  companion object {
    const val LANE_SPACING = 2.65f
    const val TRAIN_HEIGHT = 2.95f
    const val RAMP_LENGTH = 8.5f
    const val BASE_SPEED = 23f
    const val MAX_SPEED = 52f
    const val GRAVITY = 42f
    const val NORMAL_JUMP_VEL = 15.8f
    const val SNEAKERS_JUMP_VEL = 21.8f
    private const val MAX_PARTICLES = 60
  }
}
