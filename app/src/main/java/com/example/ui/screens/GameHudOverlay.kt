package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.MonetizationOn
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Skateboarding
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.game.ActivePowerUpUi
import com.example.game.HudSnapshot
import com.example.game.PowerUpKind
import com.example.ui.theme.CloudWhite
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.DeepAsphalt
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.HyperOrange
import com.example.ui.theme.MidnightSlate
import com.example.ui.theme.SolarGold

@Composable
fun GameHudOverlay(
  hud: HudSnapshot,
  onPauseClick: () -> Unit,
  onHoverboardClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier =
      modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding()
        .padding(horizontal = 16.dp, vertical = 12.dp)
  ) {
    // Top Row: Pause + Speed on left, Score + Coins on right
    Row(
      modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.Top,
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
          onClick = onPauseClick,
          color = MidnightSlate.copy(alpha = 0.80f),
          shape = CircleShape,
          modifier =
            Modifier.size(50.dp)
              .border(1.5.dp, CloudWhite.copy(alpha = 0.25f), CircleShape)
              .testTag("pause_game_button"),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Rounded.Pause,
              contentDescription = "Pause game",
              tint = CloudWhite,
              modifier = Modifier.size(26.dp),
            )
          }
        }

        Surface(
          color = DeepAsphalt.copy(alpha = 0.72f),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.border(1.dp, ElectricCyan.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
        ) {
          Text(
            text = "${hud.distanceMeters}m • ${hud.speedKmh} km/h",
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            color = ElectricCyan,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
          )
        }
      }

      Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Surface(
          color = MidnightSlate.copy(alpha = 0.82f),
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.border(1.5.dp, ElectricCyan.copy(alpha = 0.45f), RoundedCornerShape(16.dp)),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            if (hud.scoreMultiplier > 1) {
              Surface(
                color = ElectricCyan,
                shape = RoundedCornerShape(8.dp),
              ) {
                Text(
                  text = "${hud.scoreMultiplier}X",
                  style = MaterialTheme.typography.labelLarge,
                  color = DeepAsphalt,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
              }
            }
            Text(
              text = hud.score.toString().padStart(6, '0'),
              style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
              color = CloudWhite,
              fontWeight = FontWeight.Bold,
            )
          }
        }

        Surface(
          color = MidnightSlate.copy(alpha = 0.82f),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.border(1.dp, SolarGold.copy(alpha = 0.45f), RoundedCornerShape(14.dp)),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.MonetizationOn,
              contentDescription = "Coins collected",
              tint = SolarGold,
              modifier = Modifier.size(18.dp),
            )
            Text(
              text = hud.coinsThisRun.toString(),
              style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
              color = SolarGold,
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }
    }

    // Center Banner + Chaser Guard Warning
    Column(
      modifier = Modifier.align(Alignment.TopCenter).padding(top = 86.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      AnimatedVisibility(
        visible = hud.chaserAlert,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
      ) {
        Surface(
          color = CrimsonAlert.copy(alpha = 0.86f),
          shape = RoundedCornerShape(12.dp),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Warning,
              contentDescription = null,
              tint = CloudWhite,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = "GUARD ON YOUR TAIL!",
              style = MaterialTheme.typography.labelLarge,
              color = CloudWhite,
            )
          }
        }
      }

      AnimatedVisibility(
        visible = hud.bannerEvent != null,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
      ) {
        Surface(
          color = DeepAsphalt.copy(alpha = 0.84f),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.border(1.5.dp, ElectricCyan, RoundedCornerShape(14.dp)),
        ) {
          Text(
            text = hud.bannerEvent ?: "",
            style = MaterialTheme.typography.titleLarge,
            color = ElectricCyan,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
          )
        }
      }
    }

    // Left Side: Active Power-Up Timers
    Column(
      modifier = Modifier.align(Alignment.CenterStart),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      hud.activePowerUps.forEach { power ->
        PowerUpTimerCard(power = power)
      }
      if (hud.hoverboardActive) {
        Surface(
          color = MidnightSlate.copy(alpha = 0.86f),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.border(1.dp, ElectricCyan, RoundedCornerShape(12.dp)),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Shield,
              contentDescription = null,
              tint = ElectricCyan,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = "BOARD ${hud.hoverboardRemainingSec.toInt()}s",
              style = MaterialTheme.typography.labelLarge,
              color = ElectricCyan,
            )
          }
        }
      }
    }

    // Bottom Right: Hoverboard Activation Button (leaving full viewport open for finger swipes)
    Box(
      modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
      contentAlignment = Alignment.BottomEnd,
    ) {
      Surface(
        onClick = onHoverboardClick,
        enabled = !hud.hoverboardActive && hud.hoverboardsAvailable > 0L,
        color =
          if (hud.hoverboardActive) ElectricCyan.copy(alpha = 0.25f)
          else if (hud.hoverboardsAvailable > 0L) HyperOrange.copy(alpha = 0.90f)
          else MidnightSlate.copy(alpha = 0.65f),
        shape = RoundedCornerShape(18.dp),
        modifier =
          Modifier.minimumInteractiveComponentSize()
            .border(
              1.5.dp,
              if (hud.hoverboardActive) ElectricCyan else SolarGold.copy(alpha = 0.7f),
              RoundedCornerShape(18.dp),
            )
            .testTag("activate_hoverboard_button"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.Skateboarding,
            contentDescription = "Activate hoverboard",
            tint = CloudWhite,
            modifier = Modifier.size(22.dp),
          )
          Column {
            Text(
              text = if (hud.hoverboardActive) "SHIELD ON" else "BOARD",
              style = MaterialTheme.typography.labelLarge,
              color = CloudWhite,
            )
            Text(
              text = "x${hud.hoverboardsAvailable} • 2x Tap",
              style = MaterialTheme.typography.labelMedium,
              color = CloudWhite.copy(alpha = 0.85f),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun PowerUpTimerCard(power: ActivePowerUpUi) {
  val accent =
    when (power.kind) {
      PowerUpKind.MAGNET -> CrimsonAlert
      PowerUpKind.JETPACK -> HyperOrange
      PowerUpKind.SNEAKERS -> EmeraldMint
      PowerUpKind.MULTIPLIER -> ElectricCyan
    }
  Surface(
    color = MidnightSlate.copy(alpha = 0.84f),
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier.width(126.dp).border(1.dp, accent.copy(alpha = 0.65f), RoundedCornerShape(12.dp)),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.Bolt,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(14.dp),
          )
          Text(
            text = power.kind.label,
            style = MaterialTheme.typography.labelMedium,
            color = CloudWhite,
            fontWeight = FontWeight.Bold,
          )
        }
        Text(
          text = "${power.remainingSeconds.toInt() + 1}s",
          style = MaterialTheme.typography.labelMedium,
          color = accent,
        )
      }
      LinearProgressIndicator(
        progress = { power.progress },
        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
        color = accent,
        trackColor = DeepAsphalt,
      )
    }
  }
}
