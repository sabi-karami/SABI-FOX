package com.sabifox.vpn.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sabifox.vpn.data.TokenStore
import com.sabifox.vpn.data.formatBytes
import com.sabifox.vpn.data.model.ConnectionState
import com.sabifox.vpn.vpn.SabiFoxService
import java.text.DateFormat
import java.util.Date

@Composable
fun AccountsScreen(
    accounts: List<TokenStore.AccountSlot>,
    activeSlot: Int,
    busy: Boolean,
    message: String?,
    onBack: () -> Unit,
    onChoose: (Int) -> Unit,
    onIdentify: () -> Unit,
    onRefresh: () -> Unit,
) {
    val connection by SabiFoxService.state.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Account switcher • prototype") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
        )
    }) { inset ->
        Column(
            Modifier.fillMaxSize().padding(inset).verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            Text(
                "Up to ten accounts on this device. Each requires its own Mozilla sign-in and verification. " +
                    "Disconnect before switching, then reconnect manually. No password or email code is stored in the app.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (connection != ConnectionState.DISCONNECTED) {
                Text(
                    "Disconnect VPN before changing accounts.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            message?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
            }
            accounts.forEach { account ->
                Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Slot ${account.slot + 1}" + if (account.slot == activeSlot) " • ACTIVE" else "", style = MaterialTheme.typography.titleMedium)
                        Text(
                            account.email ?: if (account.signedIn) "Identity not yet checked" else "Add a Mozilla account",
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        val quota = account.remainingBytes?.let { formatBytes(it) } ?: "Unknown"
                        val max = account.maximumBytes?.let { " / ${formatBytes(it)}" } ?: ""
                        Text("Remaining: $quota$max", modifier = Modifier.padding(top = 8.dp))
                        val checked = account.quotaCheckedAt?.let {
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
                        } ?: "never"
                        Text("Last checked: $checked", style = MaterialTheme.typography.bodySmall)
                        if (!account.signedIn) Text("Not signed in", style = MaterialTheme.typography.bodySmall)
                        Row(modifier = Modifier.padding(top = 8.dp)) {
                            if (account.slot != activeSlot || !account.signedIn) {
                                Button(
                                    enabled = !busy && connection == ConnectionState.DISCONNECTED,
                                    onClick = { onChoose(account.slot) },
                                ) {
                                    Text(if (account.signedIn) "Select" else "Sign in")
                                }
                            }
                            if (account.slot == activeSlot && account.signedIn && account.email == null) {
                                OutlinedButton(enabled = !busy, onClick = onIdentify) { Text("Identify") }
                            }
                        }
                    }
                }
            }
            OutlinedButton(
                enabled = !busy && accounts.getOrNull(activeSlot)?.signedIn == true,
                onClick = onRefresh,
                modifier = Modifier.padding(top = 16.dp),
            ) { Text("Refresh active account balance") }
            Text(
                "Inactive balances are last-known values, not live. A session may need sign-in again. " +
                    "Transfer between phones is not available in this prototype.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
