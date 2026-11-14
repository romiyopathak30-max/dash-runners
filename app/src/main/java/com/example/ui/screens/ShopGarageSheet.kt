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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.MonetizationOn
import androidx.compose.material.icons.rounded.Skateboarding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.RunnerCatalog
import com.example.data.model.RunnerProfile
import com.example.data.model.UpgradeKind
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
fun ShopGarageSheet(
  profile: RunnerProfile,
  onSelectOrBuyRunner: (String) -> Unit,
  onSelectOrBuyBoard: (String) -> Unit,
  onBuyHoverboardCharge: () -> Unit,
  onUpgradePowerUp: (UpgradeKind) -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onClose)
  var selectedTab by remember { mutableIntStateOf(0) }
  val tabs = listOf("Runners", "Boards", "Boosts")

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
      // Header
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          IconButton(
            onClick = onClose,
            modifier =
              Modifier.size(48.dp)
                .clip(CircleShape)
                .background(MidnightSlate)
                .testTag("close_garage_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
              contentDescription = "Back to lobby",
              tint = CloudWhite,
            )
          }
          Text(
            text = "METRO GARAGE",
            style = MaterialTheme.typography.headlineMedium,
            color = CloudWhite,
          )
        }

        Surface(
          color = MidnightSlate,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.border(1.dp, SolarGold.copy(alpha = 0.55f), RoundedCornerShape(14.dp)),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.MonetizationOn,
              contentDescription = null,
              tint = SolarGold,
              modifier = Modifier.size(18.dp),
            )
            Text(
              text = profile.coins.toString(),
              style = MaterialTheme.typography.titleMedium,
              color = SolarGold,
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }

      TabRow(
        selectedTabIndex = selectedTab,
        containerColor = MidnightSlate,
        contentColor = ElectricCyan,
        indicator = { tabPositions ->
          TabRowDefaults.SecondaryIndicator(
            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
            color = ElectricCyan,
          )
        },
      ) {
        tabs.forEachIndexed { idx, title ->
          Tab(
            selected = selectedTab == idx,
            onClick = { selectedTab = idx },
            modifier = Modifier.testTag("garage_tab_${title.lowercase()}"),
            text = {
              Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = if (selectedTab == idx) ElectricCyan else MutedSteel,
              )
            },
          )
        }
      }

      LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        when (selectedTab) {
          0 -> {
            items(RunnerCatalog.runners, key = { it.id }) { runner ->
              val unlocked = profile.unlockedRunners.contains(runner.id)
              val equipped = profile.selectedRunnerId == runner.id
              val swatch = Color(runner.jacketRgb[0], runner.jacketRgb[1], runner.jacketRgb[2])
              val accent = Color(runner.accentRgb[0], runner.accentRgb[1], runner.accentRgb[2])
              GarageItemCard(
                title = runner.name,
                subtitle = runner.tagline,
                swatchColor = swatch,
                accentColor = accent,
                icon = Icons.Rounded.DirectionsRun,
                isEquipped = equipped,
                isUnlocked = unlocked,
                priceCoins = runner.priceCoins,
                canAfford = profile.coins >= runner.priceCoins,
                tag = "runner_action_${runner.id}",
                onClick = { onSelectOrBuyRunner(runner.id) },
              )
            }
          }
          1 -> {
            item {
              Surface(
                color = ElevatedCard,
                shape = RoundedCornerShape(18.dp),
                modifier =
                  Modifier.fillMaxWidth()
                    .border(1.dp, HyperOrange.copy(alpha = 0.5f), RoundedCornerShape(18.dp)),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(16.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = "Hoverboard Charges (x${profile.hoverboardsCount})",
                      style = MaterialTheme.typography.titleMedium,
                      color = CloudWhite,
                      fontWeight = FontWeight.Bold,
                    )
                    Text(
                      text = "Double-tap mid-run for crash protection",
                      style = MaterialTheme.typography.bodyMedium,
                      color = MutedSteel,
                    )
                  }
                  Spacer(modifier = Modifier.size(10.dp))
                  Button(
                    onClick = onBuyHoverboardCharge,
                    enabled = profile.coins >= RunnerCatalog.HOVERBOARD_CHARGE_COST,
                    colors = ButtonDefaults.buttonColors(containerColor = SolarGold, contentColor = DeepAsphalt),
                    modifier = Modifier.height(48.dp).testTag("buy_hoverboard_charge_button"),
                  ) {
                    Text(
                      text = "+1 (${RunnerCatalog.HOVERBOARD_CHARGE_COST})",
                      style = MaterialTheme.typography.labelLarge,
                    )
                  }
                }
              }
            }

            items(RunnerCatalog.boards, key = { it.id }) { board ->
              val unlocked = profile.unlockedBoards.contains(board.id)
              val equipped = profile.selectedBoardId == board.id
              val swatch = Color(board.bodyRgb[0], board.bodyRgb[1], board.bodyRgb[2])
              val accent = Color(board.glowRgb[0], board.glowRgb[1], board.glowRgb[2])
              GarageItemCard(
                title = board.name,
                subtitle = board.tagline,
                swatchColor = swatch,
                accentColor = accent,
                icon = Icons.Rounded.Skateboarding,
                isEquipped = equipped,
                isUnlocked = unlocked,
                priceCoins = board.priceCoins,
                canAfford = profile.coins >= board.priceCoins,
                tag = "board_action_${board.id}",
                onClick = { onSelectOrBuyBoard(board.id) },
              )
            }
          }
          else -> {
            items(UpgradeKind.entries, key = { it.name }) { kind ->
              val level =
                when (kind) {
                  UpgradeKind.MAGNET -> profile.magnetLevel
                  UpgradeKind.JETPACK -> profile.jetpackLevel
                  UpgradeKind.SNEAKERS -> profile.sneakersLevel
                  UpgradeKind.MULTIPLIER -> profile.multiplierLevel
                }
              val isMax = level >= RunnerCatalog.MAX_UPGRADE_LEVEL
              val cost = RunnerCatalog.upgradeCost(level)
              UpgradeCard(
                kind = kind,
                level = level.toInt(),
                isMax = isMax,
                cost = cost,
                canAfford = profile.coins >= cost,
                onUpgrade = { onUpgradePowerUp(kind) },
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun GarageItemCard(
  title: String,
  subtitle: String,
  swatchColor: Color,
  accentColor: Color,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  isEquipped: Boolean,
  isUnlocked: Boolean,
  priceCoins: Long,
  canAfford: Boolean,
  tag: String,
  onClick: () -> Unit,
) {
  Surface(
    color = ElevatedCard,
    shape = RoundedCornerShape(18.dp),
    modifier =
      Modifier.fillMaxWidth()
        .border(
          width = if (isEquipped) 2.dp else 1.dp,
          color = if (isEquipped) ElectricCyan else CloudWhite.copy(alpha = 0.14f),
          shape = RoundedCornerShape(18.dp),
        ),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Box(
          modifier =
            Modifier.size(50.dp)
              .clip(RoundedCornerShape(14.dp))
              .background(swatchColor)
              .border(2.dp, accentColor, RoundedCornerShape(14.dp)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CloudWhite,
            modifier = Modifier.size(26.dp),
          )
        }
        Column {
          Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = CloudWhite,
          )
          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MutedSteel,
          )
        }
      }

      Spacer(modifier = Modifier.size(10.dp))

      Button(
        onClick = onClick,
        enabled = isUnlocked && !isEquipped || (!isUnlocked && canAfford),
        colors =
          ButtonDefaults.buttonColors(
            containerColor =
              when {
                isEquipped -> EmeraldMint
                isUnlocked -> ElectricCyan
                else -> SolarGold
              },
            contentColor = DeepAsphalt,
          ),
        modifier = Modifier.height(48.dp).testTag(tag),
      ) {
        if (isEquipped) {
          Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.size(4.dp))
          Text("EQUIPPED", style = MaterialTheme.typography.labelLarge)
        } else if (isUnlocked) {
          Text("EQUIP", style = MaterialTheme.typography.labelLarge)
        } else {
          Text("$priceCoins COINS", style = MaterialTheme.typography.labelLarge)
        }
      }
    }
  }
}

@Composable
private fun UpgradeCard(
  kind: UpgradeKind,
  level: Int,
  isMax: Boolean,
  cost: Long,
  canAfford: Boolean,
  onUpgrade: () -> Unit,
) {
  Surface(
    color = ElevatedCard,
    shape = RoundedCornerShape(18.dp),
    modifier =
      Modifier.fillMaxWidth().border(1.dp, ElectricCyan.copy(alpha = 0.25f), RoundedCornerShape(18.dp)),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.Bolt,
            contentDescription = null,
            tint = ElectricCyan,
            modifier = Modifier.size(20.dp),
          )
          Text(
            text = "${kind.title} • Lv.$level",
            style = MaterialTheme.typography.titleMedium,
            color = CloudWhite,
            fontWeight = FontWeight.Bold,
          )
        }
        Text(
          text = kind.description,
          style = MaterialTheme.typography.bodyMedium,
          color = MutedSteel,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          for (i in 1..RunnerCatalog.MAX_UPGRADE_LEVEL.toInt()) {
            Box(
              modifier =
                Modifier.height(6.dp)
                  .weight(1f)
                  .clip(RoundedCornerShape(3.dp))
                  .background(if (i <= level) ElectricCyan else DeepAsphalt)
            )
          }
        }
      }

      Spacer(modifier = Modifier.size(12.dp))

      Button(
        onClick = onUpgrade,
        enabled = !isMax && canAfford,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = if (isMax) EmeraldMint else SolarGold,
            contentColor = DeepAsphalt,
          ),
        modifier = Modifier.height(48.dp).testTag("upgrade_${kind.name.lowercase()}_button"),
      ) {
        Text(
          text = if (isMax) "MAX" else "$cost",
          style = MaterialTheme.typography.labelLarge,
        )
      }
    }
  }
}
