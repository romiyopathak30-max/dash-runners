package com.example.data

import com.example.data.model.RunRecord
import com.example.data.model.RunnerProfile
import com.example.data.model.UpgradeKind
import kotlinx.coroutines.flow.Flow

interface ProgressRepository {
  fun observeProfile(userId: String): Flow<RunnerProfile?>

  fun observeRecentRuns(userId: String, limitCount: Long = 20L): Flow<List<RunRecord>>

  suspend fun ensureProfile(defaultDisplayName: String): Result<RunnerProfile>

  suspend fun recordRunResult(
    profile: RunnerProfile,
    score: Long,
    distanceMeters: Long,
    coinsCollected: Long,
    boardsRemaining: Long,
    boardUsed: Boolean,
  ): Result<Unit>

  suspend fun selectOrBuyRunner(profile: RunnerProfile, runnerId: String): Result<Unit>

  suspend fun selectOrBuyBoard(profile: RunnerProfile, boardId: String): Result<Unit>

  suspend fun buyHoverboardCharge(profile: RunnerProfile): Result<Unit>

  suspend fun upgradePowerUp(profile: RunnerProfile, kind: UpgradeKind): Result<Unit>
}
