package com.example.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.UpgradeKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalGuestRepositoryTest {

  @Test
  fun guestMode_savesRunsAndUnlocksLocallyWithoutAccount() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = LocalGuestRepository(context)
    val initial = repo.ensureProfile("Guest Runner").getOrThrow()

    val runResult =
      repo.recordRunResult(
        profile = initial,
        score = 8400L,
        distanceMeters = 420L,
        coinsCollected = 450L,
        boardsRemaining = 2L,
        boardUsed = true,
      )
    assertTrue(runResult.isSuccess)

    val updated = repo.observeProfile(LocalGuestRepository.GUEST_USER_ID).first()!!
    assertEquals(8400L, updated.highScore)
    assertEquals(initial.coins + 450L, updated.coins)
    assertEquals(2L, updated.hoverboardsCount)

    val buyRunner = repo.selectOrBuyRunner(updated, "nova")
    assertTrue(buyRunner.isSuccess)

    val afterBuy = repo.observeProfile(LocalGuestRepository.GUEST_USER_ID).first()!!
    assertEquals("nova", afterBuy.selectedRunnerId)

    val upgrade = repo.upgradePowerUp(afterBuy, UpgradeKind.MAGNET)
    assertTrue(upgrade.isSuccess)

    val runs = repo.observeRecentRuns(LocalGuestRepository.GUEST_USER_ID).first()
    assertTrue(runs.any { it.score == 8400L && it.boardUsed })
  }
}
