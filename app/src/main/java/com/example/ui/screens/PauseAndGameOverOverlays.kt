package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MonetizationOn
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.game.RunSummary
import com.example.ui.theme.CloudWhite
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.DeepAsphalt
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedCard
import com.example.ui.theme.HyperOrange
import com.example.ui.theme.MidnightSlate
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.SolarGold

@Composable
fun PauseOverlay(
  isMuted: Boolean,
  onResume: () -> Unit,
  onToggleMute: () -> Unit,
  onQuitToLobby: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onResume)

  Box(
    modifier = modifier.fillMaxSize().background(DeepAsphalt.copy(alpha = 0.78f)).padding(24.dp),
    contentAlignment = Alignment.Center,
  ) {
    Surface(
      color = MidnightSlate,
      shape = RoundedCornerShape(24.dp),
      modifier =
        Modifier.fillMaxWidth()
          .widthIn(max = 420.dp)
          .border(1.5.dp, ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
    ) {
      Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Text(
          text = "RUN PAUSED",
          style = MaterialTheme.typography.headlineLarge,
          color = CloudWhite,
        )

        Button(
          onClick = onResume,
          shape = RoundedCornerShape(16.dp),
          colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = DeepAsphalt),
          modifier = Modifier.fillMaxWidth().height(54.dp).testTag("resume_game_button"),
        ) {
          Icon(Icons.Rounded.PlayArrow, contentDescription = null)
          Spacer(modifier = Modifier.size(8.dp))
          Text("RESUME", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        OutlinedButton(
          onClick = onToggleMute,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.fillMaxWidth().height(52.dp).testTag("pause_mute_button"),
        ) {
          Icon(
            imageVector = if (isMuted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
            contentDescription = null,
            tint = CloudWhite,
          )
          Spacer(modifier = Modifier.size(8.dp))
          Text(
            text = if (isMuted) "SOUND: MUTED" else "SOUND: ON",
            style = MaterialTheme.typography.labelLarge,
            color = CloudWhite,
          )
        }

        OutlinedButton(
          onClick = onQuitToLobby,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.fillMaxWidth().height(52.dp).testTag("quit_to_lobby_button"),
        ) {
          Icon(Icons.Rounded.Home, contentDescription = null, tint = CrimsonAlert)
          Spacer(modifier = Modifier.size(8.dp))
          Text("QUIT TO LOBBY", style = MaterialTheme.typography.labelLarge, color = CrimsonAlert)
        }
      }
    }
  }
}

@Composable
fun GameOverOverlay(
  summary: RunSummary,
  bestScore: Long,
  onPlayAgain: () -> Unit,
  onReturnLobby: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onReturnLobby)

  Box(
    modifier = modifier.fillMaxSize().background(DeepAsphalt.copy(alpha = 0.82f)).padding(24.dp),
    contentAlignment = Alignment.Center,
  ) {
    Surface(
      color = MidnightSlate,
      shape = RoundedCornerShape(26.dp),
      modifier =
        Modifier.fillMaxWidth()
          .widthIn(max = 440.dp)
          .border(
            2.dp,
            if (summary.isNewHighScore) SolarGold else ElectricCyan.copy(alpha = 0.55f),
            RoundedCornerShape(26.dp),
          ),
    ) {
      Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        if (summary.isNewHighScore) {
          Surface(
            color = SolarGold.copy(alpha = 0.2f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.border(1.dp, SolarGold, RoundedCornerShape(12.dp)),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = SolarGold, modifier = Modifier.size(18.dp))
              Text("NEW HIGH SCORE!", style = MaterialTheme.typography.labelLarge, color = SolarGold)
            }
          }
        } else {
          Text(
            text = "WIPEOUT!",
            style = MaterialTheme.typography.headlineLarge,
            color = CrimsonAlert,
          )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            text = "FINAL SCORE",
            style = MaterialTheme.typography.labelMedium,
            color = MutedSteel,
          )
          Text(
            text = summary.score.toString(),
            style = MaterialTheme.typography.displayLarge,
            color = CloudWhite,
          )
          Text(
            text = "BEST: ${maxOf(bestScore, summary.score)}",
            style = MaterialTheme.typography.labelLarge,
            color = ElectricCyan,
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Surface(
            color = ElevatedCard,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.weight(1f),
          ) {
            Column(
              modifier = Modifier.padding(14.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Text("DISTANCE", style = MaterialTheme.typography.labelMedium, color = MutedSteel)
              Text(
                text = "${summary.distanceMeters}m",
                style = MaterialTheme.typography.headlineMedium,
                color = HyperOrange,
              )
            }
          }

          Surface(
            color = ElevatedCard,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.weight(1f),
          ) {
            Column(
              modifier = Modifier.padding(14.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Text("COINS", style = MaterialTheme.typography.labelMedium, color = MutedSteel)
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(Icons.Rounded.MonetizationOn, contentDescription = null, tint = SolarGold, modifier = Modifier.size(20.dp))
                Text(
                  text = "+${summary.coinsCollected}",
                  style = MaterialTheme.typography.headlineMedium,
                  color = SolarGold,
                )
              }
            }
          }
        }

        Button(
          onClick = onPlayAgain,
          shape = RoundedCornerShape(18.dp),
          colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = DeepAsphalt),
          modifier = Modifier.fillMaxWidth().height(58.dp).testTag("play_again_button"),
        ) {
          Icon(Icons.Rounded.Replay, contentDescription = null)
          Spacer(modifier = Modifier.size(8.dp))
          Text("RUN AGAIN", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        OutlinedButton(
          onClick = onReturnLobby,
          shape = RoundedCornerShape(18.dp),
          modifier = Modifier.fillMaxWidth().height(52.dp).testTag("game_over_lobby_button"),
        ) {
          Icon(Icons.Rounded.Home, contentDescription = null, tint = CloudWhite)
          Spacer(modifier = Modifier.size(8.dp))
          Text("BACK TO LOBBY", style = MaterialTheme.typography.labelLarge, color = CloudWhite)
        }
      }
    }
  }
}
