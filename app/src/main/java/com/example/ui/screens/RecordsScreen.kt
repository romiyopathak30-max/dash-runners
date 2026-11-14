package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.MonetizationOn
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.RunRecord
import com.example.data.model.RunnerCatalog
import com.example.data.model.RunnerProfile
import com.example.ui.theme.CloudWhite
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.DeepAsphalt
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedCard
import com.example.ui.theme.HyperOrange
import com.example.ui.theme.MidnightSlate
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.SolarGold
import com.example.ui.viewmodel.UiState

@Composable
fun RecordsScreen(
  profile: RunnerProfile,
  runsState: UiState<List<RunRecord>>,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onClose)

  Box(
    modifier =
      modifier
        .fillMaxSize()
        .background(DeepAsphalt.copy(alpha = 0.96f))
        .statusBarsPadding()
        .navigationBarsPadding()
  ) {
    Column(
      modifier = Modifier.fillMaxSize().widthIn(max = 600.dp).align(Alignment.Center)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        IconButton(
          onClick = onClose,
          modifier =
            Modifier.size(48.dp)
              .clip(CircleShape)
              .background(MidnightSlate)
              .testTag("close_records_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "Back to lobby",
            tint = CloudWhite,
          )
        }
        Text(
          text = "CAREER & TOP RUNS",
          style = MaterialTheme.typography.headlineMedium,
          color = CloudWhite,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        CareerTile("BEST SCORE", profile.highScore.toString(), ElectricCyan, Modifier.weight(1f))
        CareerTile("BEST DIST", "${profile.bestDistance}m", HyperOrange, Modifier.weight(1f))
        CareerTile("TOTAL RUNS", profile.totalRuns.toString(), SolarGold, Modifier.weight(1f))
      }

      LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        when (runsState) {
          is UiState.Loading -> {
            item {
              Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ElectricCyan)
              }
            }
          }
          is UiState.Error -> {
            item {
              Surface(
                color = CrimsonAlert.copy(alpha = 0.2f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text = runsState.message,
                  color = CloudWhite,
                  style = MaterialTheme.typography.bodyMedium,
                  modifier = Modifier.padding(14.dp),
                )
              }
            }
          }
          is UiState.Success -> {
            if (runsState.data.isEmpty()) {
              item {
                Surface(
                  color = MidnightSlate,
                  shape = RoundedCornerShape(18.dp),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.DirectionsRun,
                      contentDescription = null,
                      tint = ElectricCyan,
                      modifier = Modifier.size(36.dp),
                    )
                    Text(
                      text = "No runs logged yet",
                      style = MaterialTheme.typography.titleLarge,
                      color = CloudWhite,
                    )
                    Text(
                      text = "Hit TAP TO RUN and set your first high score on the subway tracks!",
                      style = MaterialTheme.typography.bodyMedium,
                      color = MutedSteel,
                    )
                  }
                }
              }
            } else {
              itemsIndexed(runsState.data, key = { idx, item -> item.id.ifBlank { "run_$idx" } }) { index, run ->
                val runnerName = RunnerCatalog.runnerById(run.runnerId).name
                Surface(
                  color = ElevatedCard,
                  shape = RoundedCornerShape(16.dp),
                  modifier =
                    Modifier.fillMaxWidth()
                      .border(
                        1.dp,
                        if (index == 0) SolarGold.copy(alpha = 0.6f) else CloudWhite.copy(alpha = 0.1f),
                        RoundedCornerShape(16.dp),
                      ),
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                      Surface(
                        color = if (index == 0) SolarGold else MidnightSlate,
                        shape = CircleShape,
                        modifier = Modifier.size(38.dp),
                      ) {
                        Box(contentAlignment = Alignment.Center) {
                          if (index == 0) {
                            Icon(
                              imageVector = Icons.Rounded.EmojiEvents,
                              contentDescription = null,
                              tint = DeepAsphalt,
                              modifier = Modifier.size(20.dp),
                            )
                          } else {
                            Text(
                              text = "#${index + 1}",
                              style = MaterialTheme.typography.labelLarge,
                              color = ElectricCyan,
                            )
                          }
                        }
                      }

                      Column {
                        Text(
                          text = "${run.score} pts",
                          style = MaterialTheme.typography.titleLarge,
                          color = CloudWhite,
                          fontWeight = FontWeight.Bold,
                        )
                        Text(
                          text = "${run.distanceMeters}m • $runnerName" + if (run.boardUsed) " • Board" else "",
                          style = MaterialTheme.typography.bodyMedium,
                          color = MutedSteel,
                        )
                      }
                    }

                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                      Icon(
                        imageVector = Icons.Rounded.MonetizationOn,
                        contentDescription = null,
                        tint = SolarGold,
                        modifier = Modifier.size(16.dp),
                      )
                      Text(
                        text = "+${run.coinsCollected}",
                        style = MaterialTheme.typography.titleMedium,
                        color = SolarGold,
                        fontWeight = FontWeight.Bold,
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun CareerTile(
  label: String,
  value: String,
  accent: androidx.compose.ui.graphics.Color,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = MidnightSlate,
    shape = RoundedCornerShape(14.dp),
    modifier = modifier.border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp)),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MutedSteel,
      )
      Text(
        text = value,
        style = MaterialTheme.typography.titleLarge,
        color = accent,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}
