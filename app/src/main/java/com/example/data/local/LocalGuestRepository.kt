package com.example.data.local

import android.content.Context
import com.example.data.ProgressRepository
import com.example.data.model.RunRecord
import com.example.data.model.RunnerCatalog
import com.example.data.model.RunnerProfile
import com.example.data.model.UpgradeKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class LocalGuestRepository(context: Context) : ProgressRepository {
  private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val profileFlow = MutableStateFlow(loadProfile())
  private val runsFlow = MutableStateFlow(loadRuns())

  override fun observeProfile(userId: String): Flow<RunnerProfile?> = profileFlow.asStateFlow()

  override fun observeRecentRuns(userId: String, limitCount: Long): Flow<List<RunRecord>> =
    runsFlow.asStateFlow()

  override suspend fun ensureProfile(defaultDisplayName: String): Result<RunnerProfile> {
    val current = profileFlow.value
    saveProfile(current)
    return Result.success(current)
  }

  override suspend fun recordRunResult(
    profile: RunnerProfile,
    score: Long,
    distanceMeters: Long,
    coinsCollected: Long,
    boardsRemaining: Long,
    boardUsed: Boolean,
  ): Result<Unit> {
    val safeScore = score.coerceIn(0L, 999_999_999L)
    val safeDistance = distanceMeters.coerceIn(0L, 999_999_999L)
    val safeCoins = coinsCollected.coerceIn(0L, 9_999_999L)

    val updatedProfile =
      profile.copy(
        coins = (profile.coins + safeCoins).coerceIn(0L, 999_999_999L),
        highScore = maxOf(profile.highScore, safeScore),
        bestDistance = maxOf(profile.bestDistance, safeDistance),
        totalRuns = (profile.totalRuns + 1L).coerceIn(0L, 9_999_999L),
        hoverboardsCount = boardsRemaining.coerceIn(0L, 9999L),
      )
    saveProfile(updatedProfile)

    val newRun =
      RunRecord(
        id = UUID.randomUUID().toString(),
        userId = GUEST_USER_ID,
        score = safeScore,
        distanceMeters = safeDistance,
        coinsCollected = safeCoins,
        runnerId = profile.selectedRunnerId,
        boardUsed = boardUsed,
      )
    val updatedRuns = (runsFlow.value + newRun).sortedByDescending { it.score }.take(20)
    saveRuns(updatedRuns)
    return Result.success(Unit)
  }

  override suspend fun selectOrBuyRunner(profile: RunnerProfile, runnerId: String): Result<Unit> {
    val runner = RunnerCatalog.runnerById(runnerId)
    val alreadyUnlocked = profile.unlockedRunners.contains(runner.id)
    if (!alreadyUnlocked && profile.coins < runner.priceCoins) {
      return Result.failure(IllegalStateException("Not enough coins to unlock ${runner.name}"))
    }
    val updated =
      profile.copy(
        coins = if (alreadyUnlocked) profile.coins else profile.coins - runner.priceCoins,
        selectedRunnerId = runner.id,
        unlockedRunners =
          if (alreadyUnlocked) profile.unlockedRunners
          else (profile.unlockedRunners + runner.id).distinct().take(10),
      )
    saveProfile(updated)
    return Result.success(Unit)
  }

  override suspend fun selectOrBuyBoard(profile: RunnerProfile, boardId: String): Result<Unit> {
    val board = RunnerCatalog.boardById(boardId)
    val alreadyUnlocked = profile.unlockedBoards.contains(board.id)
    if (!alreadyUnlocked && profile.coins < board.priceCoins) {
      return Result.failure(IllegalStateException("Not enough coins to unlock ${board.name}"))
    }
    val updated =
      profile.copy(
        coins = if (alreadyUnlocked) profile.coins else profile.coins - board.priceCoins,
        selectedBoardId = board.id,
        unlockedBoards =
          if (alreadyUnlocked) profile.unlockedBoards
          else (profile.unlockedBoards + board.id).distinct().take(10),
      )
    saveProfile(updated)
    return Result.success(Unit)
  }

  override suspend fun buyHoverboardCharge(profile: RunnerProfile): Result<Unit> {
    if (profile.coins < RunnerCatalog.HOVERBOARD_CHARGE_COST) {
      return Result.failure(IllegalStateException("Need ${RunnerCatalog.HOVERBOARD_CHARGE_COST} coins"))
    }
    if (profile.hoverboardsCount >= 9999L) {
      return Result.failure(IllegalStateException("Hoverboard inventory is full"))
    }
    val updated =
      profile.copy(
        coins = profile.coins - RunnerCatalog.HOVERBOARD_CHARGE_COST,
        hoverboardsCount = profile.hoverboardsCount + 1L,
      )
    saveProfile(updated)
    return Result.success(Unit)
  }

  override suspend fun upgradePowerUp(profile: RunnerProfile, kind: UpgradeKind): Result<Unit> {
    val currentLevel =
      when (kind) {
        UpgradeKind.MAGNET -> profile.magnetLevel
        UpgradeKind.JETPACK -> profile.jetpackLevel
        UpgradeKind.SNEAKERS -> profile.sneakersLevel
        UpgradeKind.MULTIPLIER -> profile.multiplierLevel
      }
    if (currentLevel >= RunnerCatalog.MAX_UPGRADE_LEVEL) {
      return Result.failure(IllegalStateException("${kind.title} is already at max level"))
    }
    val cost = RunnerCatalog.upgradeCost(currentLevel)
    if (profile.coins < cost) {
      return Result.failure(IllegalStateException("Need $cost coins to upgrade ${kind.title}"))
    }
    val updated =
      when (kind) {
        UpgradeKind.MAGNET -> profile.copy(coins = profile.coins - cost, magnetLevel = currentLevel + 1L)
        UpgradeKind.JETPACK -> profile.copy(coins = profile.coins - cost, jetpackLevel = currentLevel + 1L)
        UpgradeKind.SNEAKERS -> profile.copy(coins = profile.coins - cost, sneakersLevel = currentLevel + 1L)
        UpgradeKind.MULTIPLIER -> profile.copy(coins = profile.coins - cost, multiplierLevel = currentLevel + 1L)
      }
    saveProfile(updated)
    return Result.success(Unit)
  }

  private fun loadProfile(): RunnerProfile {
    val raw = prefs.getString(KEY_PROFILE, null)
    if (raw == null) {
      return RunnerProfile(
        id = GUEST_USER_ID,
        userId = GUEST_USER_ID,
        displayName = "Guest Runner",
        coins = 150L,
        hoverboardsCount = 3L,
      )
    }
    return try {
      val json = JSONObject(raw)
      RunnerProfile(
        id = GUEST_USER_ID,
        userId = GUEST_USER_ID,
        displayName = json.optString("displayName", "Guest Runner"),
        coins = json.optLong("coins", 150L),
        highScore = json.optLong("highScore", 0L),
        bestDistance = json.optLong("bestDistance", 0L),
        totalRuns = json.optLong("totalRuns", 0L),
        selectedRunnerId = json.optString("selectedRunnerId", "blaze"),
        selectedBoardId = json.optString("selectedBoardId", "aero"),
        unlockedRunners = jsonArrayToList(json.optJSONArray("unlockedRunners"), listOf("blaze")),
        unlockedBoards = jsonArrayToList(json.optJSONArray("unlockedBoards"), listOf("aero")),
        hoverboardsCount = json.optLong("hoverboardsCount", 3L),
        magnetLevel = json.optLong("magnetLevel", 1L),
        jetpackLevel = json.optLong("jetpackLevel", 1L),
        sneakersLevel = json.optLong("sneakersLevel", 1L),
        multiplierLevel = json.optLong("multiplierLevel", 1L),
      )
    } catch (_: Exception) {
      RunnerProfile(id = GUEST_USER_ID, userId = GUEST_USER_ID, displayName = "Guest Runner", coins = 150L)
    }
  }

  private fun saveProfile(profile: RunnerProfile) {
    profileFlow.value = profile
    val json =
      JSONObject().apply {
        put("displayName", profile.displayName)
        put("coins", profile.coins)
        put("highScore", profile.highScore)
        put("bestDistance", profile.bestDistance)
        put("totalRuns", profile.totalRuns)
        put("selectedRunnerId", profile.selectedRunnerId)
        put("selectedBoardId", profile.selectedBoardId)
        put("unlockedRunners", JSONArray(profile.unlockedRunners))
        put("unlockedBoards", JSONArray(profile.unlockedBoards))
        put("hoverboardsCount", profile.hoverboardsCount)
        put("magnetLevel", profile.magnetLevel)
        put("jetpackLevel", profile.jetpackLevel)
        put("sneakersLevel", profile.sneakersLevel)
        put("multiplierLevel", profile.multiplierLevel)
      }
    prefs.edit().putString(KEY_PROFILE, json.toString()).apply()
  }

  private fun loadRuns(): List<RunRecord> {
    val raw = prefs.getString(KEY_RUNS, null) ?: return emptyList()
    return try {
      val arr = JSONArray(raw)
      buildList {
        for (i in 0 until arr.length()) {
          val obj = arr.getJSONObject(i)
          add(
            RunRecord(
              id = obj.optString("id", "run_$i"),
              userId = GUEST_USER_ID,
              score = obj.optLong("score", 0L),
              distanceMeters = obj.optLong("distanceMeters", 0L),
              coinsCollected = obj.optLong("coinsCollected", 0L),
              runnerId = obj.optString("runnerId", "blaze"),
              boardUsed = obj.optBoolean("boardUsed", false),
            )
          )
        }
      }
    } catch (_: Exception) {
      emptyList()
    }
  }

  private fun saveRuns(runs: List<RunRecord>) {
    runsFlow.value = runs
    val arr = JSONArray()
    runs.forEach { run ->
      arr.put(
        JSONObject().apply {
          put("id", run.id)
          put("score", run.score)
          put("distanceMeters", run.distanceMeters)
          put("coinsCollected", run.coinsCollected)
          put("runnerId", run.runnerId)
          put("boardUsed", run.boardUsed)
        }
      )
    }
    prefs.edit().putString(KEY_RUNS, arr.toString()).apply()
  }

  private fun jsonArrayToList(arr: JSONArray?, fallback: List<String>): List<String> {
    if (arr == null || arr.length() == 0) return fallback
    return buildList {
      for (i in 0 until arr.length()) {
        add(arr.getString(i))
      }
    }
  }

  companion object {
    const val GUEST_USER_ID = "guest_local"
    private const val PREFS_NAME = "dash_runners_guest_prefs"
    private const val KEY_PROFILE = "guest_profile_v1"
    private const val KEY_RUNS = "guest_runs_v1"
  }
}
