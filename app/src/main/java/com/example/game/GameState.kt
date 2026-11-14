package com.example.game

enum class GamePhase {
  LOBBY,
  PLAYING,
  PAUSED,
  GAME_OVER,
}

enum class PowerUpKind(val label: String) {
  MAGNET("MAGNET"),
  JETPACK("JETPACK"),
  SNEAKERS("SNEAKERS"),
  MULTIPLIER("2X SCORE"),
}

enum class ObstacleKind {
  LOW_BARRIER,
  HIGH_BARRIER,
  TALL_BARRIER,
  PARKED_TRAIN,
  MOVING_TRAIN,
  TRAIN_RAMP,
}

data class ActivePowerUpUi(
  val kind: PowerUpKind,
  val remainingSeconds: Float,
  val totalSeconds: Float,
) {
  val progress: Float
    get() = if (totalSeconds > 0f) (remainingSeconds / totalSeconds).coerceIn(0f, 1f) else 0f
}

data class HudSnapshot(
  val phase: GamePhase = GamePhase.LOBBY,
  val score: Long = 0L,
  val distanceMeters: Long = 0L,
  val coinsThisRun: Long = 0L,
  val scoreMultiplier: Int = 1,
  val speedKmh: Int = 38,
  val hoverboardActive: Boolean = false,
  val hoverboardRemainingSec: Float = 0f,
  val hoverboardsAvailable: Long = 0L,
  val chaserAlert: Boolean = false,
  val invulnerableShieldSec: Float = 0f,
  val activePowerUps: List<ActivePowerUpUi> = emptyList(),
  val bannerEvent: String? = null,
)

data class RunSummary(
  val score: Long,
  val distanceMeters: Long,
  val coinsCollected: Long,
  val isNewHighScore: Boolean,
  val boardsRemaining: Long,
  val boardUsed: Boolean,
)

enum class SwipeDirection {
  LEFT,
  RIGHT,
  UP,
  DOWN,
}
