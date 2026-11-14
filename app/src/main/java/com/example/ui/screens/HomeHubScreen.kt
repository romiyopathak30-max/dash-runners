package com.example.ui.screens

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.MonetizationOn
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Skateboarding
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.RunnerCatalog
import com.example.data.model.RunnerProfile
import com.example.ui.theme.CloudWhite
import com.example.ui.theme.DeepAsphalt
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedCard
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.HyperOrange
import com.example.ui.theme.MidnightSlate
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.SolarGold

@Composable
fun HomeHubScreen(
  profile: RunnerProfile,
  isGuest: Boolean,
  isSigningIn: Boolean,
  isMuted: Boolean,
  onStartRun: () -> Unit,
  onOpenShop: () -> Unit,
  onOpenRecords: () -> Unit,
  onToggleMute: () -> Unit,
  onSignInWithGoogle: () -> Unit,
  onSignOut: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val activeRunner = RunnerCatalog.runnerById(profile.selectedRunnerId)
  val activeBoard = RunnerCatalog.boardById(profile.selectedBoardId)

  Box(
    modifier =
      modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            colors =
              listOf(
                DeepAsphalt.copy(alpha = 0.72f),
                Color.Transparent,
                DeepAsphalt.copy(alpha = 0.45f),
                DeepAsphalt.copy(alpha = 0.92f),
              )
          )
        )
  ) {
    Column(
      modifier =
        Modifier.fillMaxSize()
          .statusBarsPadding()
          .navigationBarsPadding()
          .padding(horizontal = 20.dp, vertical = 14.dp)
          .widthIn(max = 580.dp)
          .align(Alignment.Center),
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      // Top Bar: Player badge, Stats, Audio/Controls/Auth
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "DASH RUNNERS",
              style = MaterialTheme.typography.headlineLarge,
              color = ElectricCyan,
            )
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = if (isGuest) Icons.Rounded.CloudQueue else Icons.Rounded.CloudDone,
                contentDescription = null,
                tint = if (isGuest) MutedSteel else EmeraldMint,
                modifier = Modifier.size(15.dp),
              )
              Text(
                text = if (isGuest) "Guest Mode • Local Save" else "Synced • ${profile.displayName}",
                style = MaterialTheme.typography.bodyMedium,
                color = CloudWhite.copy(alpha = 0.85f),
              )
            }
          }

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TopCircleAction(
              icon = if (isMuted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
              contentDescription = if (isMuted) "Unmute sound" else "Mute sound",
              tint = if (isMuted) MutedSteel else CloudWhite,
              tag = "toggle_mute_button",
              onClick = onToggleMute,
            )
            if (!isGuest) {
              TopCircleAction(
                icon = Icons.AutoMirrored.Rounded.Logout,
                contentDescription = "Sign out to guest mode",
                tint = CloudWhite,
                tag = "sign_out_button",
                onClick = onSignOut,
              )
            }
          }
        }

        if (isGuest) {
          Surface(
            onClick = onSignInWithGoogle,
            enabled = !isSigningIn,
            color = MidnightSlate.copy(alpha = 0.88f),
            shape = RoundedCornerShape(14.dp),
            modifier =
              Modifier.fillMaxWidth()
                .border(1.dp, ElectricCyan.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                .testTag("google_sign_in_button"),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Playing as Guest (No account needed)",
                  style = MaterialTheme.typography.labelLarge,
                  color = CloudWhite,
                )
                Text(
                  text = "Optional: Sign in with Google for cloud backup",
                  style = MaterialTheme.typography.labelMedium,
                  color = MutedSteel,
                )
              }
              Spacer(modifier = Modifier.size(8.dp))
              if (isSigningIn) {
                CircularProgressIndicator(
                  modifier = Modifier.size(20.dp),
                  color = ElectricCyan,
                  strokeWidth = 2.dp,
                )
              } else {
                Surface(
                  color = ElectricCyan,
                  shape = RoundedCornerShape(10.dp),
                ) {
                  Text(
                    text = "SIGN IN",
                    style = MaterialTheme.typography.labelLarge,
                    color = DeepAsphalt,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                  )
                }
              }
            }
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          StatPill(
            icon = Icons.Rounded.EmojiEvents,
            label = "HIGH SCORE",
            value = profile.highScore.toString(),
            accent = ElectricCyan,
            modifier = Modifier.weight(1f),
          )
          StatPill(
            icon = Icons.Rounded.MonetizationOn,
            label = "COINS",
            value = profile.coins.toString(),
            accent = SolarGold,
            modifier = Modifier.weight(1f),
          )
        }
      }

      // Bottom Dock: Active Gear Cards + Garage / Records + Giant RUN CTA
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Surface(
            onClick = onOpenShop,
            color = MidnightSlate.copy(alpha = 0.88f),
            shape = RoundedCornerShape(16.dp),
            modifier =
              Modifier.weight(1f)
                .border(1.dp, ElectricCyan.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .testTag("active_runner_card"),
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.DirectionsRun,
                contentDescription = null,
                tint = ElectricCyan,
                modifier = Modifier.size(24.dp),
              )
              Column {
                Text(
                  text = "RUNNER",
                  style = MaterialTheme.typography.labelMedium,
                  color = MutedSteel,
                )
                Text(
                  text = activeRunner.name,
                  style = MaterialTheme.typography.titleMedium,
                  color = CloudWhite,
                  fontWeight = FontWeight.Bold,
                )
              }
            }
          }

          Surface(
            onClick = onOpenShop,
            color = MidnightSlate.copy(alpha = 0.88f),
            shape = RoundedCornerShape(16.dp),
            modifier =
              Modifier.weight(1f)
                .border(1.dp, HyperOrange.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .testTag("active_board_card"),
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.Skateboarding,
                contentDescription = null,
                tint = HyperOrange,
                modifier = Modifier.size(24.dp),
              )
              Column {
                Text(
                  text = "BOARD (x${profile.hoverboardsCount})",
                  style = MaterialTheme.typography.labelMedium,
                  color = MutedSteel,
                )
                Text(
                  text = activeBoard.name,
                  style = MaterialTheme.typography.titleMedium,
                  color = CloudWhite,
                  fontWeight = FontWeight.Bold,
                )
              }
            }
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Surface(
            onClick = onOpenShop,
            color = ElevatedCard.copy(alpha = 0.92f),
            shape = RoundedCornerShape(16.dp),
            modifier =
              Modifier.weight(1f)
                .height(54.dp)
                .border(1.dp, CloudWhite.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
                .testTag("open_garage_button"),
          ) {
            Row(
              modifier = Modifier.fillMaxSize(),
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Rounded.Storefront,
                contentDescription = null,
                tint = SolarGold,
                modifier = Modifier.size(20.dp),
              )
              Spacer(modifier = Modifier.size(8.dp))
              Text(
                text = "GARAGE",
                style = MaterialTheme.typography.labelLarge,
                color = CloudWhite,
              )
            }
          }

          Surface(
            onClick = onOpenRecords,
            color = ElevatedCard.copy(alpha = 0.92f),
            shape = RoundedCornerShape(16.dp),
            modifier =
              Modifier.weight(1f)
                .height(54.dp)
                .border(1.dp, CloudWhite.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
                .testTag("open_records_button"),
          ) {
            Row(
              modifier = Modifier.fillMaxSize(),
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Rounded.EmojiEvents,
                contentDescription = null,
                tint = ElectricCyan,
                modifier = Modifier.size(20.dp),
              )
              Spacer(modifier = Modifier.size(8.dp))
              Text(
                text = "RECORDS",
                style = MaterialTheme.typography.labelLarge,
                color = CloudWhite,
              )
            }
          }
        }

        Button(
          onClick = onStartRun,
          shape = RoundedCornerShape(20.dp),
          colors =
            ButtonDefaults.buttonColors(
              containerColor = ElectricCyan,
              contentColor = DeepAsphalt,
            ),
          modifier =
            Modifier.fillMaxWidth()
              .height(64.dp)
              .clip(RoundedCornerShape(20.dp))
              .testTag("play_run_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(30.dp),
          )
          Spacer(modifier = Modifier.size(8.dp))
          Text(
            text = "TAP TO RUN",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
          )
        }
      }
    }
  }
}

@Composable
private fun TopCircleAction(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  contentDescription: String,
  tint: Color,
  tag: String,
  onClick: () -> Unit,
) {
  IconButton(
    onClick = onClick,
    modifier =
      Modifier.size(48.dp)
        .clip(CircleShape)
        .background(MidnightSlate.copy(alpha = 0.80f))
        .border(1.dp, CloudWhite.copy(alpha = 0.2f), CircleShape)
        .testTag(tag),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      tint = tint,
      modifier = Modifier.size(22.dp),
    )
  }
}

@Composable
private fun StatPill(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  value: String,
  accent: Color,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = MidnightSlate.copy(alpha = 0.85f),
    shape = RoundedCornerShape(14.dp),
    modifier = modifier.border(1.dp, accent.copy(alpha = 0.40f), RoundedCornerShape(14.dp)),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = accent,
        modifier = Modifier.size(20.dp),
      )
      Column {
        Text(
          text = label,
          style = MaterialTheme.typography.labelMedium,
          color = MutedSteel,
        )
        Text(
          text = value,
          style = MaterialTheme.typography.titleLarge,
          color = CloudWhite,
          fontWeight = FontWeight.Bold,
        )
      }
    }
  }
}
