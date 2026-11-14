package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.data.ProgressRepository
import com.example.data.model.RunRecord
import com.example.data.model.RunnerCatalog
import com.example.data.model.RunnerProfile
import com.example.data.model.UpgradeKind
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class RunnerRepository(private val db: FirebaseFirestore) : ProgressRepository {
  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private val auth = Firebase.auth

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
  }

  override fun observeProfile(userId: String): Flow<RunnerProfile?> = flow {
    val docRef = db.collection("users").document(userId)
    emitAll(
      docRef
        .snapshots()
        .map { snapshot ->
          if (snapshot.exists()) {
            snapshot.toObject(
              RunnerProfile::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
          } else {
            null
          }
        }
        .catch { error ->
          val firestoreError =
            generateSequence(error) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == userId) {
            handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
          }
          throw error
        }
    )
  }

  override fun observeRecentRuns(userId: String, limitCount: Long): Flow<List<RunRecord>> = flow {
    val colRef = db.collection("users").document(userId).collection("runs")
    emitAll(
      colRef
        .orderBy("score", Query.Direction.DESCENDING)
        .limit(limitCount)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            RunRecord::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        }
        .catch { error ->
          val firestoreError =
            generateSequence(error) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == userId) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
          }
          throw error
        }
    )
  }

  override suspend fun ensureProfile(defaultDisplayName: String): Result<RunnerProfile> {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid)
    return try {
      val snapshot = docRef.get().await()
      if (snapshot.exists()) {
        val existing =
          snapshot.toObject(
            RunnerProfile::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          ) ?: throw IllegalStateException("Failed to parse RunnerProfile")
        Result.success(existing)
      } else {
        val cleanName = defaultDisplayName.trim().ifBlank { "Runner" }.take(60)
        val payload =
          mapOf(
            "userId" to uid,
            "displayName" to cleanName,
            "coins" to 150L,
            "highScore" to 0L,
            "bestDistance" to 0L,
            "totalRuns" to 0L,
            "selectedRunnerId" to "blaze",
            "selectedBoardId" to "aero",
            "unlockedRunners" to listOf("blaze"),
            "unlockedBoards" to listOf("aero"),
            "hoverboardsCount" to 3L,
            "magnetLevel" to 1L,
            "jetpackLevel" to 1L,
            "sneakersLevel" to 1L,
            "multiplierLevel" to 1L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        docRef.set(payload).await()
        val created =
          docRef.get().await().toObject(
            RunnerProfile::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          ) ?: RunnerProfile(id = uid, userId = uid, displayName = cleanName, coins = 150L)
        Result.success(created)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      Result.failure(e)
    }
  }

  override suspend fun recordRunResult(
    profile: RunnerProfile,
    score: Long,
    distanceMeters: Long,
    coinsCollected: Long,
    boardsRemaining: Long,
    boardUsed: Boolean,
  ): Result<Unit> {
    val uid = requireUserId()
    val profileRef = db.collection("users").document(uid)
    val runId = UUID.randomUUID().toString()
    val runRef = profileRef.collection("runs").document(runId)

    val safeScore = score.coerceIn(0L, 999_999_999L)
    val safeDistance = distanceMeters.coerceIn(0L, 999_999_999L)
    val safeCoins = coinsCollected.coerceIn(0L, 9_999_999L)
    val newTotalCoins = (profile.coins + safeCoins).coerceIn(0L, 999_999_999L)
    val newHighScore = maxOf(profile.highScore, safeScore)
    val newBestDistance = maxOf(profile.bestDistance, safeDistance)
    val newTotalRuns = (profile.totalRuns + 1L).coerceIn(0L, 9_999_999L)
    val safeBoards = boardsRemaining.coerceIn(0L, 9999L)

    return try {
      val batch = db.batch()
      batch.update(
        profileRef,
        mapOf(
          "coins" to newTotalCoins,
          "highScore" to newHighScore,
          "bestDistance" to newBestDistance,
          "totalRuns" to newTotalRuns,
          "hoverboardsCount" to safeBoards,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.set(
        runRef,
        mapOf(
          "userId" to uid,
          "score" to safeScore,
          "distanceMeters" to safeDistance,
          "coinsCollected" to safeCoins,
          "runnerId" to profile.selectedRunnerId,
          "boardUsed" to boardUsed,
          "createdAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, profileRef.path)
      Result.failure(e)
    }
  }

  override suspend fun selectOrBuyRunner(profile: RunnerProfile, runnerId: String): Result<Unit> {
    val uid = requireUserId()
    val runner = RunnerCatalog.runnerById(runnerId)
    val profileRef = db.collection("users").document(uid)
    val alreadyUnlocked = profile.unlockedRunners.contains(runner.id)

    if (!alreadyUnlocked && profile.coins < runner.priceCoins) {
      return Result.failure(IllegalStateException("Not enough coins to unlock ${runner.name}"))
    }

    val updatedUnlocked =
      if (alreadyUnlocked) profile.unlockedRunners
      else (profile.unlockedRunners + runner.id).distinct().take(10)
    val updatedCoins = if (alreadyUnlocked) profile.coins else profile.coins - runner.priceCoins

    return try {
      profileRef
        .update(
          mapOf(
            "coins" to updatedCoins,
            "selectedRunnerId" to runner.id,
            "unlockedRunners" to updatedUnlocked,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, profileRef.path)
      Result.failure(e)
    }
  }

  override suspend fun selectOrBuyBoard(profile: RunnerProfile, boardId: String): Result<Unit> {
    val uid = requireUserId()
    val board = RunnerCatalog.boardById(boardId)
    val profileRef = db.collection("users").document(uid)
    val alreadyUnlocked = profile.unlockedBoards.contains(board.id)

    if (!alreadyUnlocked && profile.coins < board.priceCoins) {
      return Result.failure(IllegalStateException("Not enough coins to unlock ${board.name}"))
    }

    val updatedUnlocked =
      if (alreadyUnlocked) profile.unlockedBoards
      else (profile.unlockedBoards + board.id).distinct().take(10)
    val updatedCoins = if (alreadyUnlocked) profile.coins else profile.coins - board.priceCoins

    return try {
      profileRef
        .update(
          mapOf(
            "coins" to updatedCoins,
            "selectedBoardId" to board.id,
            "unlockedBoards" to updatedUnlocked,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, profileRef.path)
      Result.failure(e)
    }
  }

  override suspend fun buyHoverboardCharge(profile: RunnerProfile): Result<Unit> {
    val uid = requireUserId()
    val profileRef = db.collection("users").document(uid)
    if (profile.coins < RunnerCatalog.HOVERBOARD_CHARGE_COST) {
      return Result.failure(IllegalStateException("Need ${RunnerCatalog.HOVERBOARD_CHARGE_COST} coins"))
    }
    if (profile.hoverboardsCount >= 9999L) {
      return Result.failure(IllegalStateException("Hoverboard inventory is full"))
    }
    return try {
      profileRef
        .update(
          mapOf(
            "coins" to (profile.coins - RunnerCatalog.HOVERBOARD_CHARGE_COST),
            "hoverboardsCount" to (profile.hoverboardsCount + 1L),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, profileRef.path)
      Result.failure(e)
    }
  }

  override suspend fun upgradePowerUp(profile: RunnerProfile, kind: UpgradeKind): Result<Unit> {
    val uid = requireUserId()
    val profileRef = db.collection("users").document(uid)
    val (field, currentLevel) =
      when (kind) {
        UpgradeKind.MAGNET -> "magnetLevel" to profile.magnetLevel
        UpgradeKind.JETPACK -> "jetpackLevel" to profile.jetpackLevel
        UpgradeKind.SNEAKERS -> "sneakersLevel" to profile.sneakersLevel
        UpgradeKind.MULTIPLIER -> "multiplierLevel" to profile.multiplierLevel
      }
    if (currentLevel >= RunnerCatalog.MAX_UPGRADE_LEVEL) {
      return Result.failure(IllegalStateException("${kind.title} is already at max level"))
    }
    val cost = RunnerCatalog.upgradeCost(currentLevel)
    if (profile.coins < cost) {
      return Result.failure(IllegalStateException("Need $cost coins to upgrade ${kind.title}"))
    }
    return try {
      profileRef
        .update(
          mapOf(
            "coins" to (profile.coins - cost),
            field to (currentLevel + 1L),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, profileRef.path)
      Result.failure(e)
    }
  }
}
