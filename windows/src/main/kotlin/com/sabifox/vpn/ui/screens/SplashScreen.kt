package com.sabifox.vpn.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sabifox.vpn.data.FxaAuthRepository
import com.sabifox.vpn.data.SessionStatus
import com.sabifox.vpn.ui.components.SabiMark
import com.sabifox.vpn.ui.components.sabiAuroraBrush
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

private const val SESSION_RESTORE_TIMEOUT_MS = 8_000L

@Composable
fun SplashScreen(
    authRepository: FxaAuthRepository,
    onSignedIn: () -> Unit,
    onNeedsLogin: () -> Unit,
) {
    LaunchedEffect(Unit) {
        delay(500)
        val status =
            withTimeoutOrNull(SESSION_RESTORE_TIMEOUT_MS) { authRepository.restoreSession() }
                ?: SessionStatus.UNREACHABLE

        when (status) {
            SessionStatus.ACTIVE -> onSignedIn()
            SessionStatus.UNREACHABLE -> onSignedIn()
            SessionStatus.NEEDS_LOGIN -> onNeedsLogin()
        }
    }

    Box(
        Modifier.fillMaxSize().background(sabiAuroraBrush()),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SabiMark(size = 120.dp)
            Spacer(Modifier.height(20.dp))
            Text(
                "SABI-FOX",
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 6.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "PRIVATE \u00B7 SWIFT \u00B7 YOURS",
                fontSize = 11.sp,
                letterSpacing = 3.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp),
            )
            CircularProgressIndicator(
                modifier = Modifier.padding(top = 32.dp).size(26.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
