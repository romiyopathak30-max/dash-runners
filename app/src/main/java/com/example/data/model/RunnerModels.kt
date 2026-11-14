package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class RunnerProfile(
  @DocumentId val id: String = "",
  val userId: String = "",
  val displayName: String = "Runner",
  val coins: Long = 0L,
  val highScore: Long = 0L,
  val bestDistance: Long = 0L,
  val totalRuns: Long = 0L,
  val selectedRunnerId: String = "blaze",
  val selectedBoardId: String = "aero",
  val unlockedRunners: List<String> = listOf("blaze"),
  val unlockedBoards: List<String> = listOf("aero"),
  val hoverboardsCount: Long = 3L,
  val magnetLevel: Long = 1L,
  val jetpackLevel: Long = 1L,
  val sneakersLevel: Long = 1L,
  val multiplierLevel: Long = 1L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class RunRecord(
  @DocumentId val id: String = "",
  val userId: String = "",
  val score: Long = 0L,
  val distanceMeters: Long = 0L,
  val coinsCollected: Long = 0L,
  val runnerId: String = "blaze",
  val boardUsed: Boolean = false,
  @ServerTimestamp val createdAt: Timestamp? = null,
)

data class RunnerCharacter(
  val id: String,
  val name: String,
  val tagline: String,
  val priceCoins: Long,
  val jacketRgb: FloatArray,
  val accentRgb: FloatArray,
  val capRgb: FloatArray,
  val skinRgb: FloatArray,
  val pantsRgb: FloatArray,
)

data class HoverboardModel(
  val id: String,
  val name: String,
  val tagline: String,
  val priceCoins: Long,
  val bodyRgb: FloatArray,
  val glowRgb: FloatArray,
)

enum class UpgradeKind(val title: String, val description: String) {
  MAGNET("Coin Magnet", "Pulls coins from all 3 lanes"),
  JETPACK("Jetpack Boost", "Soars high with sky coin trails"),
  SNEAKERS("Super Sneakers", "Leap straight onto train roofs"),
  MULTIPLIER("2x Multiplier", "Doubles all distance & coin points"),
}

object RunnerCatalog {
  val runners: List<RunnerCharacter> =
    listOf(
      RunnerCharacter(
        id = "blaze",
        name = "Blaze",
        tagline = "Neon Metro Sprinter",
        priceCoins = 0L,
        jacketRgb = floatArrayOf(0.05f, 0.80f, 0.96f),
        accentRgb = floatArrayOf(1.00f, 0.45f, 0.05f),
        capRgb = floatArrayOf(0.08f, 0.72f, 0.92f),
        skinRgb = floatArrayOf(0.86f, 0.65f, 0.50f),
        pantsRgb = floatArrayOf(0.14f, 0.17f, 0.25f),
      ),
      RunnerCharacter(
        id = "nova",
        name = "Nova",
        tagline = "Solar Sunset Acrobat",
        priceCoins = 350L,
        jacketRgb = floatArrayOf(1.00f, 0.36f, 0.14f),
        accentRgb = floatArrayOf(1.00f, 0.86f, 0.15f),
        capRgb = floatArrayOf(0.95f, 0.24f, 0.42f),
        skinRgb = floatArrayOf(0.55f, 0.36f, 0.24f),
        pantsRgb = floatArrayOf(0.18f, 0.15f, 0.28f),
      ),
      RunnerCharacter(
        id = "volt",
        name = "Volt",
        tagline = "High-Voltage Rail Rider",
        priceCoins = 750L,
        jacketRgb = floatArrayOf(0.15f, 0.92f, 0.50f),
        accentRgb = floatArrayOf(0.98f, 0.95f, 0.25f),
        capRgb = floatArrayOf(0.10f, 0.75f, 0.42f),
        skinRgb = floatArrayOf(0.92f, 0.76f, 0.64f),
        pantsRgb = floatArrayOf(0.10f, 0.16f, 0.18f),
      ),
      RunnerCharacter(
        id = "shadow",
        name = "Shadow",
        tagline = "Midnight Express Phantom",
        priceCoins = 1400L,
        jacketRgb = floatArrayOf(0.16f, 0.18f, 0.28f),
        accentRgb = floatArrayOf(0.75f, 0.35f, 1.00f),
        capRgb = floatArrayOf(0.60f, 0.22f, 0.95f),
        skinRgb = floatArrayOf(0.80f, 0.62f, 0.52f),
        pantsRgb = floatArrayOf(0.08f, 0.09f, 0.14f),
      ),
    )

  val boards: List<HoverboardModel> =
    listOf(
      HoverboardModel(
        id = "aero",
        name = "Aero Mk1",
        tagline = "Crisp cyan street cruiser",
        priceCoins = 0L,
        bodyRgb = floatArrayOf(0.10f, 0.76f, 0.95f),
        glowRgb = floatArrayOf(0.00f, 0.95f, 1.00f),
      ),
      HoverboardModel(
        id = "pulse",
        name = "Pulse Edge",
        tagline = "Lime plasma rail deck",
        priceCoins = 250L,
        bodyRgb = floatArrayOf(0.12f, 0.88f, 0.48f),
        glowRgb = floatArrayOf(0.45f, 1.00f, 0.55f),
      ),
      HoverboardModel(
        id = "inferno",
        name = "Inferno GT",
        tagline = "Blazing solar thruster",
        priceCoins = 600L,
        bodyRgb = floatArrayOf(1.00f, 0.38f, 0.08f),
        glowRgb = floatArrayOf(1.00f, 0.82f, 0.18f),
      ),
      HoverboardModel(
        id = "phantom",
        name = "Phantom X",
        tagline = "Ultraviolet stealth deck",
        priceCoins = 1000L,
        bodyRgb = floatArrayOf(0.55f, 0.22f, 0.95f),
        glowRgb = floatArrayOf(0.85f, 0.45f, 1.00f),
      ),
    )

  fun runnerById(id: String): RunnerCharacter = runners.find { it.id == id } ?: runners.first()

  fun boardById(id: String): HoverboardModel = boards.find { it.id == id } ?: boards.first()

  fun upgradeCost(currentLevel: Long): Long = when (currentLevel.toInt()) {
    1 -> 150L
    2 -> 350L
    3 -> 700L
    4 -> 1200L
    else -> 0L
  }

  const val HOVERBOARD_CHARGE_COST = 75L
  const val MAX_UPGRADE_LEVEL = 5L
}
