package com.example.ui.auth

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.ui.theme.CloudWhite
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.DeepAsphalt
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HyperOrange
import com.example.ui.theme.MidnightSlate
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.SolarGold

@Composable
fun SignInScreen(
  onAuthSuccess: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  var isCheckingAuto by remember { mutableStateOf(true) }
  var isSigningIn by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) {
    attemptAutoSignIn(
      context = context,
      credentialManager = credentialManager,
      onAuthSuccess = {
        isCheckingAuto = false
        onAuthSuccess()
      },
      onUnauthenticated = {
        isCheckingAuto = false
      },
      scope = scope,
    )
  }

  Box(modifier = modifier.fillMaxSize().background(DeepAsphalt)) {
    Image(
      painter = painterResource(id = R.drawable.img_dash_hero),
      contentDescription = "Dash Runners urban subway sprint artwork",
      modifier = Modifier.fillMaxSize(),
      contentScale = ContentScale.Crop,
    )

    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(
            Brush.verticalGradient(
              colors =
                listOf(
                  DeepAsphalt.copy(alpha = 0.55f),
                  DeepAsphalt.copy(alpha = 0.35f),
                  DeepAsphalt.copy(alpha = 0.88f),
                  DeepAsphalt.copy(alpha = 0.98f),
                )
            )
          )
    )

    Column(
      modifier =
        Modifier.fillMaxSize()
          .statusBarsPadding()
          .navigationBarsPadding()
          .padding(horizontal = 24.dp, vertical = 20.dp)
          .widthIn(max = 560.dp)
          .align(Alignment.Center),
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      // Top arcade emblem badge
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Surface(
          color = ElectricCyan.copy(alpha = 0.16f),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.border(1.dp, ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.DirectionsRun,
              contentDescription = null,
              tint = ElectricCyan,
              modifier = Modifier.size(18.dp),
            )
            Text(
              text = "3D SUBWAY SPRINT",
              style = MaterialTheme.typography.labelLarge,
              color = ElectricCyan,
            )
          }
        }
      }

      // Bottom title, highlights, and Google Sign-In CTA
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Text(
          text = "DASH\nRUNNERS",
          style = MaterialTheme.typography.displayLarge,
          color = CloudWhite,
        )
        Text(
          text = "Leap across express trains, ride neon hoverboards, and outrun the metro guard across an endless 3D skyline.",
          style = MaterialTheme.typography.bodyLarge,
          color = CloudWhite.copy(alpha = 0.88f),
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          FeatureChip(
            icon = Icons.Rounded.Bolt,
            label = "3-Lane 3D",
            accent = ElectricCyan,
            modifier = Modifier.weight(1f),
          )
          FeatureChip(
            icon = Icons.Rounded.Shield,
            label = "Hoverboards",
            accent = HyperOrange,
            modifier = Modifier.weight(1f),
          )
          FeatureChip(
            icon = Icons.Rounded.DirectionsRun,
            label = "Cloud Sync",
            accent = SolarGold,
            modifier = Modifier.weight(1f),
          )
        }

        if (errorMessage != null) {
          Surface(
            color = CrimsonAlert.copy(alpha = 0.18f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, CrimsonAlert.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
          ) {
            Text(
              text = errorMessage!!,
              color = CloudWhite,
              style = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.padding(12.dp),
            )
          }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Button(
          onClick = {
            errorMessage = null
            isSigningIn = true
            onGoogleSignInClicked(
              context = context,
              credentialManager = credentialManager,
              onAuthSuccess = {
                isSigningIn = false
                onAuthSuccess()
              },
              onAuthError = { msg ->
                isSigningIn = false
                errorMessage = msg
              },
              scope = scope,
              onAuthCancelled = {
                isSigningIn = false
              },
            )
          },
          enabled = !isSigningIn && !isCheckingAuto,
          shape = RoundedCornerShape(18.dp),
          colors =
            ButtonDefaults.buttonColors(
              containerColor = ElectricCyan,
              contentColor = DeepAsphalt,
              disabledContainerColor = MidnightSlate,
              disabledContentColor = MutedSteel,
            ),
          modifier =
            Modifier.fillMaxWidth()
              .height(58.dp)
              .clip(RoundedCornerShape(18.dp))
              .testTag("google_sign_in_button"),
        ) {
          if (isSigningIn || isCheckingAuto) {
            CircularProgressIndicator(
              modifier = Modifier.size(22.dp),
              color = if (isCheckingAuto) ElectricCyan else DeepAsphalt,
              strokeWidth = 2.5.dp,
            )
          } else {
            Text(
              text = "SIGN IN WITH GOOGLE",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
            )
          }
        }

        Text(
          text = "Your high scores, gold coins, runners, and hoverboards sync securely to your Google account.",
          style = MaterialTheme.typography.labelMedium,
          color = MutedSteel,
        )
      }
    }
  }
}

@Composable
private fun FeatureChip(
  icon: ImageVector,
  label: String,
  accent: Color,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = MidnightSlate.copy(alpha = 0.85f),
    shape = RoundedCornerShape(14.dp),
    modifier = modifier.border(1.dp, accent.copy(alpha = 0.38f), RoundedCornerShape(14.dp)),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = accent,
        modifier = Modifier.size(16.dp),
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = CloudWhite,
        maxLines = 1,
      )
    }
  }
}
