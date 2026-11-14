package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.UpgradeKind
import com.example.data.remote.RunnerRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunnerRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun ensureProfile_authenticatedUser_createsAndObservesProfile() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = RunnerRepository(firestore)

    val result = withTimeout(DEFAULT_TIMEOUT_MS) { repo.ensureProfile("AliceRunner") }
    assertTrue(result.isSuccess)
    val profile = result.getOrThrow()
    assertEquals(uid, profile.userId)
    assertEquals("blaze", profile.selectedRunnerId)

    val observed =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeProfile(uid).first { it != null }
      }
    assertNotNull(observed)
    assertEquals(uid, observed?.userId)
  }

  @Test
  fun recordRunResult_authenticatedOwner_updatesProfileAndAddsRunRecord() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = RunnerRepository(firestore)
    val profile = withTimeout(DEFAULT_TIMEOUT_MS) { repo.ensureProfile("AliceRunner").getOrThrow() }

    val saveResult =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.recordRunResult(
          profile = profile,
          score = 12800L,
          distanceMeters = 640L,
          coinsCollected = 95L,
          boardsRemaining = 2L,
          boardUsed = true,
        )
      }
    assertTrue(saveResult.isSuccess)

    val updatedProfile =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeProfile(uid).first { it != null && it.highScore >= 12800L }
      }
    assertEquals(profile.coins + 95L, updatedProfile?.coins)
    assertEquals(2L, updatedProfile?.hoverboardsCount)

    val runs =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeRecentRuns(uid).first { it.isNotEmpty() }
      }
    assertTrue(runs.any { it.score == 12800L && it.boardUsed })
  }

  @Test
  fun upgradeAndPurchase_authenticatedOwner_updatesUnlockedRunnersAndLevels() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = RunnerRepository(firestore)
    val initial = withTimeout(DEFAULT_TIMEOUT_MS) { repo.ensureProfile("AliceRunner").getOrThrow() }
    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.recordRunResult(
        profile = initial,
        score = 5000L,
        distanceMeters = 300L,
        coinsCollected = 500L,
        boardsRemaining = initial.hoverboardsCount,
        boardUsed = false,
      )
    }
    val enriched =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeProfile(uid).first { it != null && it.coins >= 500L }
      }!!

    val unlockResult = withTimeout(DEFAULT_TIMEOUT_MS) { repo.selectOrBuyRunner(enriched, "nova") }
    assertTrue(unlockResult.isSuccess)

    val afterUnlock =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeProfile(uid).first { it != null && it.selectedRunnerId == "nova" }
      }!!
    val upgradeResult = withTimeout(DEFAULT_TIMEOUT_MS) { repo.upgradePowerUp(afterUnlock, UpgradeKind.MAGNET) }
    assertTrue(upgradeResult.isSuccess)
  }

  @Test
  fun observeProfile_crossUserAccess_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceRepo = RunnerRepository(firestore)
    withTimeout(DEFAULT_TIMEOUT_MS) { aliceRepo.ensureProfile("AliceRunner").getOrThrow() }

    signInTestUser(BOB_EMAIL)
    val bobRepo = RunnerRepository(firestore)
    var caught: Exception? = null
    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        bobRepo.observeProfile(aliceUid).first()
      }
    } catch (e: Exception) {
      caught = e
    }
    val firestoreException = checkNotNull(caught?.unwrapFirestoreException()) {
      "Expected FirebaseFirestoreException, got $caught"
    }
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
  }

  @Test
  fun observeRecentRuns_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    auth.signOut()
    val repo = RunnerRepository(firestore)
    var caught: Exception? = null
    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeRecentRuns(aliceUid).first()
      }
    } catch (e: Exception) {
      caught = e
    }
    val firestoreException = checkNotNull(caught?.unwrapFirestoreException()) {
      "Expected FirebaseFirestoreException, got $caught"
    }
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
  }

  private companion object {
    const val ALICE_EMAIL = "alice_dash@test.com"
    const val BOB_EMAIL = "bob_dash@test.com"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 4000L
  }
}
