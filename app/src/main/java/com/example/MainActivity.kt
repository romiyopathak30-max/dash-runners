package com.example

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.audio.SoundEffectsEngine
import com.example.data.local.LocalGuestRepository
import com.example.data.model.RunnerProfile
import com.example.data.remote.RunnerRepository
import com.example.game.DashGameSurface
import com.example.game.DashWorldEngine
import com.example.game.GamePhase
import com.example.game.HudSnapshot
import com.example.game.SwipeDirection
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.auth.onGoogleSignInClicked
import com.example.ui.auth.signOut
import com.example.ui.screens.GameHudOverlay
import com.example.ui.screens.GameOverOverlay
import com.example.ui.screens.HomeHubScreen
import com.example.ui.screens.PauseOverlay
import com.example.ui.screens.RecordsScreen
import com.example.ui.screens.ShopGarageSheet
import com.example.ui.theme.CloudWhite
import com.example.ui.theme.DashRunnersTheme
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MidnightSlate
import com.example.ui.viewmodel.DashViewModel
import com.example.ui.viewmodel.UiState
import androidx.compose.runtime.withFrameNanos
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    configurePreferred120HzRefreshRate()
    setContent {
      DashRunnersTheme {
        DashRunnersRoot()
      }
    }
  }

  private fun configurePreferred120HzRefreshRate() {
    runCatching {
      val display =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display
        else @Suppress("DEPRECATION") windowManager.defaultDisplay
      if (display != null) {
        val currentMode = display.mode
        val sameSizeModes =
          display.supportedModes.filter {
            it.physicalWidth == currentMode.physicalWidth &&
              it.physicalHeight == currentMode.physicalHeight
          }
        val candidates = if (sameSizeModes.isNotEmpty()) sameSizeModes else display.supportedModes.toList()
        val bestMode =
          candidates
            .filter { it.refreshRate <= 121f }
            .maxByOrNull { it.refreshRate }
            ?: candidates.minByOrNull { abs(it.refreshRate - 120f) }
        if (bestMode != null && bestMode.modeId != currentMode.modeId && bestMode.refreshRate > currentMode.refreshRate + 1f) {
          val lp = window.attributes
          lp.preferredDisplayModeId = bestMode.modeId
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            lp.preferredRefreshRate = bestMode.refreshRate
          }
          window.attributes = lp
        }
      }
    }
  }
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
  val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser) }
  addAuthStateListener(listener)
  awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun DashRunnersRoot(providedAuth: FirebaseAuth? = null) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }
  var resolvedAuth by remember(providedAuth) { mutableStateOf(providedAuth) }
  var currentUser by remember { mutableStateOf(providedAuth?.currentUser) }

  LaunchedEffect(providedAuth) {
    if (resolvedAuth == null) {
      withFrameNanos { }
      resolvedAuth = withContext(Dispatchers.IO) { Firebase.auth }
    }
    val auth = resolvedAuth ?: return@LaunchedEffect
    currentUser = auth.currentUser
    if (auth.currentUser == null) {
      attemptAutoSignIn(
        context = context,
        credentialManager = credentialManager,
        onAuthSuccess = {},
        onUnauthenticated = {},
        scope = scope,
      )
    }
    auth.authStateFlow().collect { user ->
      currentUser = user
    }
  }

  val user = currentUser
  val isGuest = user == null
  val uid = user?.uid ?: LocalGuestRepository.GUEST_USER_ID
  val displayName =
    if (user != null) {
      user.displayName ?: user.email?.substringBefore("@") ?: "Runner"
    } else {
      "Guest Runner"
    }

  key(uid) {
    DashRunnersSession(
      currentUserId = uid,
      defaultDisplayName = displayName,
      isGuest = isGuest,
      credentialManager = credentialManager,
    )
  }
}

private enum class LobbyOverlay {
  NONE,
  GARAGE,
  RECORDS,
}

@Composable
fun DashRunnersSession(
  currentUserId: String,
  defaultDisplayName: String,
  isGuest: Boolean,
  credentialManager: CredentialManager,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  val viewModel: DashViewModel =
    viewModel(
      key = "${currentUserId}_$isGuest",
      factory =
        viewModelFactory {
          initializer {
            val app = checkNotNull(this[APPLICATION_KEY]) { "APPLICATION_KEY missing" }
            val repository =
              if (isGuest) {
                LocalGuestRepository(app)
              } else {
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                RunnerRepository(db)
              }
            DashViewModel(
              repository = repository,
              currentUserId = currentUserId,
              defaultDisplayName = defaultDisplayName,
            )
          }
        },
    )

  val profileState by viewModel.profileState.collectAsStateWithLifecycle()
  val runsState by viewModel.recentRunsState.collectAsStateWithLifecycle()
  val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
  val lastRunSummary by viewModel.lastRunSummary.collectAsStateWithLifecycle()

  val soundEngine = remember { SoundEffectsEngine() }
  val engine = remember { DashWorldEngine(soundEngine) }
  DisposableEffect(Unit) {
    onDispose {
      soundEngine.release()
    }
  }

  var currentPhase by remember { mutableStateOf(GamePhase.LOBBY) }
  val hudTickState = remember { mutableStateOf(HudSnapshot()) }
  var lobbyOverlay by remember { mutableStateOf(LobbyOverlay.NONE) }
  var isMuted by remember { mutableStateOf(false) }
  var isSigningIn by remember { mutableStateOf(false) }
  var signInError by remember { mutableStateOf<String?>(null) }
  val gameFocusRequester = remember { FocusRequester() }

  val activeProfile =
    (profileState as? UiState.Success)?.data
      ?: RunnerProfile(
        id = currentUserId,
        userId = currentUserId,
        displayName = defaultDisplayName,
        coins = 150L,
        hoverboardsCount = 3L,
      )

  LaunchedEffect(activeProfile.selectedRunnerId, activeProfile.selectedBoardId, activeProfile.hoverboardsCount) {
    engine.applyProfile(activeProfile)
  }

  LaunchedEffect(currentPhase) {
    if (currentPhase == GamePhase.PLAYING) {
      runCatching { gameFocusRequester.requestFocus() }
    }
  }

  Box(
    modifier =
      Modifier.fillMaxSize()
        .focusRequester(gameFocusRequester)
        .focusable()
        .onPreviewKeyEvent { keyEvent ->
          if (currentPhase != GamePhase.PLAYING || keyEvent.type != KeyEventType.KeyDown) {
            return@onPreviewKeyEvent false
          }
          when (keyEvent.key) {
            Key.DirectionLeft, Key.A -> {
              engine.handleSwipe(SwipeDirection.LEFT)
              true
            }
            Key.DirectionRight, Key.D -> {
              engine.handleSwipe(SwipeDirection.RIGHT)
              true
            }
            Key.DirectionUp, Key.W, Key.Spacebar -> {
              engine.handleSwipe(SwipeDirection.UP)
              true
            }
            Key.DirectionDown, Key.S -> {
              engine.handleSwipe(SwipeDirection.DOWN)
              true
            }
            Key.B, Key.ShiftLeft, Key.ShiftRight -> {
              engine.activateHoverboard()
              true
            }
            else -> false
          }
        }
  ) {
    // Live 3D OpenGL ES subway world runs continuously behind lobby & gameplay
    DashGameSurface(
      engine = engine,
      isPlaying = currentPhase == GamePhase.PLAYING,
      onHudTick = { snapshot ->
        hudTickState.value = snapshot
        if (snapshot.phase != currentPhase) {
          currentPhase = snapshot.phase
        }
      },
    )

    when (currentPhase) {
      GamePhase.LOBBY -> {
        HomeHubScreen(
          profile = activeProfile,
          isGuest = isGuest,
          isSigningIn = isSigningIn,
          isMuted = isMuted,
          onStartRun = {
            lobbyOverlay = LobbyOverlay.NONE
            viewModel.clearRunSummary()
            engine.startNewRun(activeProfile) { summary ->
              viewModel.onRunFinished(summary)
            }
            val snap = engine.hudSnapshot()
            hudTickState.value = snap
            currentPhase = snap.phase
          },
          onOpenShop = { lobbyOverlay = LobbyOverlay.GARAGE },
          onOpenRecords = { lobbyOverlay = LobbyOverlay.RECORDS },
          onToggleMute = {
            isMuted = !isMuted
            soundEngine.isMuted = isMuted
          },
          onSignInWithGoogle = {
            isSigningIn = true
            signInError = null
            onGoogleSignInClicked(
              context = context,
              credentialManager = credentialManager,
              onAuthSuccess = { isSigningIn = false },
              onAuthError = { msg ->
                isSigningIn = false
                signInError = msg
              },
              scope = scope,
              onAuthCancelled = { isSigningIn = false },
            )
          },
          onSignOut = {
            signOut(credentialManager = credentialManager, scope = scope)
          },
        )

        if (lobbyOverlay == LobbyOverlay.GARAGE) {
          ShopGarageSheet(
            profile = activeProfile,
            onSelectOrBuyRunner = viewModel::selectOrBuyRunner,
            onSelectOrBuyBoard = viewModel::selectOrBuyBoard,
            onBuyHoverboardCharge = viewModel::buyHoverboardCharge,
            onUpgradePowerUp = viewModel::upgradePowerUp,
            onClose = { lobbyOverlay = LobbyOverlay.NONE },
          )
        } else if (lobbyOverlay == LobbyOverlay.RECORDS) {
          RecordsScreen(
            profile = activeProfile,
            runsState = runsState,
            onClose = { lobbyOverlay = LobbyOverlay.NONE },
          )
        }
      }

      GamePhase.PLAYING -> {
        PlayingHudHost(
          hudState = hudTickState,
          onPauseClick = {
            engine.pauseGame()
            val snap = engine.hudSnapshot()
            hudTickState.value = snap
            currentPhase = snap.phase
          },
          onHoverboardClick = { engine.activateHoverboard() },
        )
      }

      GamePhase.PAUSED -> {
        PlayingHudHost(
          hudState = hudTickState,
          onPauseClick = {},
          onHoverboardClick = {},
        )
        PauseOverlay(
          isMuted = isMuted,
          onResume = {
            engine.resumeGame()
            val snap = engine.hudSnapshot()
            hudTickState.value = snap
            currentPhase = snap.phase
          },
          onToggleMute = {
            isMuted = !isMuted
            soundEngine.isMuted = isMuted
          },
          onQuitToLobby = {
            engine.returnToLobby()
            val snap = engine.hudSnapshot()
            hudTickState.value = snap
            currentPhase = snap.phase
          },
        )
      }

      GamePhase.GAME_OVER -> {
        val summary = lastRunSummary
        if (summary != null) {
          GameOverOverlay(
            summary = summary,
            bestScore = activeProfile.highScore,
            onPlayAgain = {
              viewModel.clearRunSummary()
              engine.startNewRun(activeProfile) { nextSummary ->
                viewModel.onRunFinished(nextSummary)
              }
              val snap = engine.hudSnapshot()
              hudTickState.value = snap
              currentPhase = snap.phase
            },
            onReturnLobby = {
              viewModel.clearRunSummary()
              engine.returnToLobby()
              val snap = engine.hudSnapshot()
              hudTickState.value = snap
              currentPhase = snap.phase
            },
          )
        }
      }
    }

    if (profileState is UiState.Loading) {
      Surface(
        color = MidnightSlate.copy(alpha = 0.85f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.align(Alignment.Center).padding(24.dp),
      ) {
        Box(modifier = Modifier.padding(18.dp), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(color = ElectricCyan)
        }
      }
    }

    val snackbarMsg = signInError ?: actionMessage
    if (snackbarMsg != null) {
      Snackbar(
        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        containerColor = MidnightSlate,
        contentColor = CloudWhite,
        action = {
          TextButton(
            onClick = {
              signInError = null
              viewModel.dismissMessage()
            }
          ) {
            Text("OK", color = ElectricCyan, style = MaterialTheme.typography.labelLarge)
          }
        },
      ) {
        Text(text = snackbarMsg, style = MaterialTheme.typography.bodyMedium)
      }
    }
  }
}

@Composable
private fun PlayingHudHost(
  hudState: State<HudSnapshot>,
  onPauseClick: () -> Unit,
  onHoverboardClick: () -> Unit,
) {
  GameHudOverlay(
    hud = hudState.value,
    onPauseClick = onPauseClick,
    onHoverboardClick = onHoverboardClick,
  )
}
