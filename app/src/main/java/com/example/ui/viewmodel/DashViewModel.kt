package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ProgressRepository
import com.example.data.model.RunRecord
import com.example.data.model.RunnerProfile
import com.example.data.model.UpgradeKind
import com.example.game.RunSummary
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

class DashViewModel(
  private val repository: ProgressRepository,
  private val currentUserId: String,
  private val defaultDisplayName: String,
) : ViewModel() {

  private val _actionMessage = MutableStateFlow<String?>(null)
  val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

  private val _lastRunSummary = MutableStateFlow<RunSummary?>(null)
  val lastRunSummary: StateFlow<RunSummary?> = _lastRunSummary.asStateFlow()

  val profileState: StateFlow<UiState<RunnerProfile>> =
    repository
      .observeProfile(currentUserId)
      .map<RunnerProfile?, UiState<RunnerProfile>> { profile ->
        if (profile != null) {
          UiState.Success(profile)
        } else {
          UiState.Loading
        }
      }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing profile", rootError)
        emit(UiState.Error(rootError.message ?: "Failed to load runner profile"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  val recentRunsState: StateFlow<UiState<List<RunRecord>>> =
    repository
      .observeRecentRuns(currentUserId)
      .map<List<RunRecord>, UiState<List<RunRecord>>> { UiState.Success(it) }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing runs", rootError)
        emit(UiState.Error(rootError.message ?: "Failed to load run records"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  init {
    viewModelScope.launch {
      val result = repository.ensureProfile(defaultDisplayName)
      result.onFailure { err ->
        _actionMessage.value = err.localizedMessage ?: "Failed to initialize profile"
      }
    }
  }

  fun onRunFinished(summary: RunSummary) {
    _lastRunSummary.value = summary
    val profile = (profileState.value as? UiState.Success)?.data ?: return
    viewModelScope.launch {
      val res =
        repository.recordRunResult(
          profile = profile,
          score = summary.score,
          distanceMeters = summary.distanceMeters,
          coinsCollected = summary.coinsCollected,
          boardsRemaining = summary.boardsRemaining,
          boardUsed = summary.boardUsed,
        )
      res.onFailure { err ->
        _actionMessage.value = err.localizedMessage ?: "Could not sync run score"
      }
    }
  }

  fun clearRunSummary() {
    _lastRunSummary.value = null
  }

  fun selectOrBuyRunner(runnerId: String) {
    val profile = (profileState.value as? UiState.Success)?.data ?: return
    viewModelScope.launch {
      repository
        .selectOrBuyRunner(profile, runnerId)
        .onFailure { _actionMessage.value = it.localizedMessage ?: "Action failed" }
    }
  }

  fun selectOrBuyBoard(boardId: String) {
    val profile = (profileState.value as? UiState.Success)?.data ?: return
    viewModelScope.launch {
      repository
        .selectOrBuyBoard(profile, boardId)
        .onFailure { _actionMessage.value = it.localizedMessage ?: "Action failed" }
    }
  }

  fun buyHoverboardCharge() {
    val profile = (profileState.value as? UiState.Success)?.data ?: return
    viewModelScope.launch {
      repository
        .buyHoverboardCharge(profile)
        .onSuccess { _actionMessage.value = "+1 Hoverboard added!" }
        .onFailure { _actionMessage.value = it.localizedMessage ?: "Purchase failed" }
    }
  }

  fun upgradePowerUp(kind: UpgradeKind) {
    val profile = (profileState.value as? UiState.Success)?.data ?: return
    viewModelScope.launch {
      repository
        .upgradePowerUp(profile, kind)
        .onSuccess { _actionMessage.value = "${kind.title} upgraded!" }
        .onFailure { _actionMessage.value = it.localizedMessage ?: "Upgrade failed" }
    }
  }

  fun dismissMessage() {
    _actionMessage.value = null
  }

  private companion object {
    const val TAG = "DashViewModel"
    const val STOP_TIMEOUT_MILLIS = 5000L
  }
}
