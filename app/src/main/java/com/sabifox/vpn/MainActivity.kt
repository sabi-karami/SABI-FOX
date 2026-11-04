package com.sabifox.vpn

import android.Manifest
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.sabifox.vpn.ui.navigation.SabiNavGraph
import com.sabifox.vpn.ui.theme.SabiFoxTheme
import com.sabifox.vpn.ui.theme.rememberThemeController
import com.sabifox.vpn.update.UpdatePrompt
import com.sabifox.vpn.vpn.SabiFoxService

class MainActivity : ComponentActivity() {

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                requestNotificationPermissionIfNeeded()
                SabiFoxService.start(this)
            } else {

                Toast.makeText(
                        this,
                        getString(R.string.vpn_permission_denied_help),
                        Toast.LENGTH_LONG,
                    )
                    .show()
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private fun requestConnect() {
        if ((application as SabiFoxApp).settingsStore.proxyOnlyMode) {
            requestNotificationPermissionIfNeeded()
            SabiFoxService.start(this)
            return
        }

        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            requestNotificationPermissionIfNeeded()
            SabiFoxService.start(this)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val alreadyGranted =
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (!alreadyGranted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onStart() {
        super.onStart()
        (application as SabiFoxApp).appUpdater.check(automatic = true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as SabiFoxApp
        setContent {
            val themeController = rememberThemeController(app.settingsStore)
            SabiFoxTheme(themeMode = themeController.mode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    UpdatePrompt(app.appUpdater)
                    SabiNavGraph(
                        navController = navController,
                        container = app,
                        themeController = themeController,
                        onRequestConnect = { requestConnect() },
                        onDisconnect = { SabiFoxService.stop(this) },
                    )
                }
            }
        }
    }
}
