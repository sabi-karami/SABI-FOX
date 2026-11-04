package com.sabifox.vpn.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sabifox.vpn.data.AppLogger
import com.sabifox.vpn.data.ControlPlaneHttp
import com.sabifox.vpn.data.FxaAuthRepository
import com.sabifox.vpn.platform.CookieManager
import com.sabifox.vpn.platform.windows.SignInHelper
import com.sabifox.vpn.platform.windows.WindowsShell
import com.sabifox.vpn.resources.*
import com.sabifox.vpn.resources.Res
import com.sabifox.vpn.ui.components.SabiMark
import com.sabifox.vpn.ui.components.sabiAuroraBrush
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.json.JSONObject

private const val TAG = "BrowserLogin"

private const val FXA_CHANNEL_ID = "account_updates"
private const val CMD_FXA_STATUS = "fxaccounts:fxa_status"
private const val CMD_CAN_LINK_ACCOUNT = "fxaccounts:can_link_account"
private const val CMD_LOGIN = "fxaccounts:login"

private val SESSION_TOKEN_HEX = Regex("^[0-9a-f]{64}$")

/** Firefox for Windows, so the Mozilla account pages offer the desktop sign-in flow. */
private const val FIREFOX_DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:135.0) Gecko/20100101 Firefox/135.0"

private fun jsString(value: String): String = JSONObject.quote(value)

/**
 * The Android app exposes `sabiWebChannel` as a JavascriptInterface. In the WebView2 sign-in
 * window the same object forwards to `chrome.webview.postMessage`; the helper tags every
 * message with the real page origin, so the checks below are identical to Android's.
 */
private val SABI_CHANNEL_SHIM = """
(function() {
  if (window.sabiWebChannel) return;
  var post = function (method, args) {
    try { window.chrome.webview.postMessage(JSON.stringify({ method: method, args: args })); } catch (e) {}
  };
  window.sabiWebChannel = {
    onMessage: function (payload, originUrl) { post('onMessage', [String(payload), String(originUrl || window.location.href)]); },
    onSessionVerified: function () { post('onSessionVerified', []); },
    onOAuthResult: function (status, text) { post('onOAuthResult', [Number(status), String(text)]); },
    onOAuthError: function (error) { post('onOAuthError', [String(error)]); }
  };
})();
""".trimIndent()

private val WEBCHANNEL_HOOK_JS = """
(function() {
  if (window.__sabiChannelHooked) return;
  window.__sabiChannelHooked = true;
  var forward = function (detail) {
    try {
      var host = window.location.hostname;
      if (host !== 'accounts.firefox.com' && !host.endsWith('.firefox.com')) return;
      var payload = typeof detail === 'string' ? detail : JSON.stringify(detail);
      sabiWebChannel.onMessage(payload, window.location.href);
    } catch (e) {}
  };
  window.addEventListener('WebChannelMessageToChrome', function (e) { forward(e.detail); });
  document.addEventListener('WebChannelMessageToChrome', function (e) { forward(e.detail); });
  window.__sabiReply = function (channelId, messageId, command, dataJson) {
    try {
      var parsedData = typeof dataJson === 'string' ? JSON.parse(dataJson) : dataJson;
      var envelope = {
        id: channelId,
        message: { messageId: messageId, command: command, data: parsedData }
      };
      var eventObj = new CustomEvent('WebChannelMessageToContent', { detail: envelope });
      window.dispatchEvent(eventObj);
      document.dispatchEvent(eventObj);
      var eventStr = new CustomEvent('WebChannelMessageToContent', { detail: JSON.stringify(envelope) });
      window.dispatchEvent(eventStr);
      document.dispatchEvent(eventStr);
    } catch (e) {}
  };
  var getUrlString = function(arg) {
    if (!arg) return '';
    if (typeof arg === 'string') return arg;
    if (typeof arg.url === 'string') return arg.url;
    if (typeof arg.href === 'string') return arg.href;
    try { return arg.toString(); } catch (e) { return ''; }
  };
  var checkVerifyUrl = function(url) {
    var u = getUrlString(url).toLowerCase();
    return u.indexOf('/verify') !== -1 || u.indexOf('/totp') !== -1 || u.indexOf('/session/verify') !== -1 ||
           u.indexOf('enter_code') !== -1 || u.indexOf('confirm_signin') !== -1 || u.indexOf('confirm_email') !== -1;
  };
  var checkSuccessUrl = function(url) {
    var u = getUrlString(url).toLowerCase();
    return u.indexOf('confirmed') !== -1 || u.indexOf('connect_another_device') !== -1 ||
           u.indexOf('/pair') !== -1 || u.indexOf('/settings') !== -1 || u.indexOf('/complete') !== -1 ||
           u.indexOf('/verified') !== -1 || u.indexOf('success') !== -1;
  };
  if (window.fetch) {
    var origFetch = window.fetch;
    window.fetch = function() {
      var reqUrl = (arguments[0] && arguments[0].url) || arguments[0] || '';
      return origFetch.apply(this, arguments).then(function(res) {
        try {
          if (res && res.ok && checkVerifyUrl(reqUrl)) {
            sabiWebChannel.onSessionVerified();
          }
        } catch (e) {}
        return res;
      });
    };
  }
  if (window.XMLHttpRequest) {
    var origOpen = XMLHttpRequest.prototype.open;
    var origSend = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.open = function(method, url) {
      this.__sabiReqUrl = url;
      return origOpen.apply(this, arguments);
    };
    XMLHttpRequest.prototype.send = function() {
      this.addEventListener('load', function() {
        try {
          if (this.status >= 200 && this.status < 300 && checkVerifyUrl(this.__sabiReqUrl)) {
            sabiWebChannel.onSessionVerified();
          }
        } catch (e) {}
      });
      return origSend.apply(this, arguments);
    };
  }
  var notifyNav = function() {
    try {
      if (checkSuccessUrl(window.location.href)) {
        sabiWebChannel.onSessionVerified();
      }
    } catch (e) {}
  };
  var origPushState = history.pushState;
  if (origPushState) {
    history.pushState = function() {
      var ret = origPushState.apply(this, arguments);
      notifyNav();
      return ret;
    };
  }
  var origReplaceState = history.replaceState;
  if (origReplaceState) {
    history.replaceState = function() {
      var ret = origReplaceState.apply(this, arguments);
      notifyNav();
      return ret;
    };
  }
  window.addEventListener('popstate', notifyNav);
  window.addEventListener('hashchange', notifyNav);
})();
""".trimIndent()

private fun isFirefoxHost(url: String?): Boolean {
    val host = runCatching { java.net.URI(url ?: return false).host?.lowercase() }.getOrNull() ?: return false
    return host == "accounts.firefox.com" || host.endsWith(".firefox.com")
}

/** Same protocol logic as the Android WebChannelBridge, talking to the WebView2 helper. */
internal class WebChannelBridge(
    private val helper: SignInHelper,
    private val authRepository: FxaAuthRepository,
    private val onTokens: (String, String?, Long) -> Unit,
    private val onSessionToken: (String) -> Unit,
) {
    @Volatile
    var pendingSessionToken: String? = null
        private set
    @Volatile
    private var exchangeInProgress = false

    fun dispatch(source: String, data: String) {
        if (!isFirefoxHost(source)) return
        val json = runCatching { JSONObject(data) }.getOrNull() ?: return
        val args = json.optJSONArray("args")
        when (json.optString("method")) {
            "onMessage" -> onMessage(args?.optString(0).orEmpty(), source)
            "onSessionVerified" -> onSessionVerified()
            "onOAuthResult" -> onOAuthResult(args?.optInt(0) ?: 0, args?.optString(1).orEmpty())
            "onOAuthError" -> onOAuthError(args?.optString(0).orEmpty())
        }
    }

    fun onSessionVerified() {
        val token = pendingSessionToken ?: return
        exchangeInProgress = false
        notifySessionToken(token)
    }

    private fun onOAuthResult(status: Int, responseText: String) {
        val token = pendingSessionToken
        if (status in 200..299) {
            val parsed = runCatching {
                val json = JSONObject(responseText)
                val accessToken = json.getString("access_token")
                val refreshToken = json.optString("refresh_token").ifBlank { null }
                val expiresIn = json.optLong("expires_in", 0L)
                Triple(accessToken, refreshToken, expiresIn)
            }.getOrNull()
            if (parsed != null) {
                onTokens(parsed.first, parsed.second, parsed.third)
                return
            }
        }
        val isUnconfirmed = status == 400 &&
            (responseText.contains("104") ||
                responseText.contains("Unconfirmed session", ignoreCase = true) ||
                responseText.contains("unverified", ignoreCase = true))
        if (isUnconfirmed) {
            exchangeInProgress = false
            return
        }
        if (token != null) fallbackSession(token)
    }

    private fun onOAuthError(error: String) {
        AppLogger.w(TAG, "in-browser oauth exchange failed: $error")
        pendingSessionToken?.let { fallbackSession(it) }
    }

    private fun fallbackSession(token: String) {
        exchangeInProgress = false
        onSessionToken(token)
    }

    private fun onMessage(payload: String, originUrl: String) {
        if (!isFirefoxHost(originUrl)) return
        val message = runCatching { JSONObject(payload).optJSONObject("message") ?: JSONObject() }.getOrNull() ?: return
        val command = message.optString("command")
        val data = message.optJSONObject("data") ?: JSONObject()
        val messageId = message.optString("messageId")

        when (command) {
            CMD_FXA_STATUS -> reply(messageId, CMD_FXA_STATUS, """{"capabilities":{},"signedInUser":null}""")
            CMD_CAN_LINK_ACCOUNT -> reply(messageId, CMD_CAN_LINK_ACCOUNT, """{"ok":true}""")
            CMD_LOGIN, "fxaccounts:oauth_login" -> {
                val token = data.optString("sessionToken", "").trim().ifBlank { data.optString("token", "").trim() }
                if (SESSION_TOKEN_HEX.matches(token)) {
                    pendingSessionToken = token
                    val hasVerificationReason = data.has("verificationReason") && data.optString("verificationReason").isNotBlank()
                    val isVerified = if (data.has("verified")) data.optBoolean("verified", true) else !hasVerificationReason
                    if (isVerified && !hasVerificationReason) notifySessionToken(token)
                }
                if (messageId.isNotBlank()) reply(messageId, command, """{"ok":true}""")
            }
            else -> {
                if (command.contains("verified", ignoreCase = true)) {
                    val token = data.optString("sessionToken", "").trim()
                        .ifBlank { data.optString("token", "").trim() }
                        .ifBlank { pendingSessionToken ?: "" }
                    if (SESSION_TOKEN_HEX.matches(token)) {
                        pendingSessionToken = token
                        exchangeInProgress = false
                        notifySessionToken(token)
                    }
                }
                if (messageId.isNotBlank()) reply(messageId, command, """{"ok":true}""")
            }
        }
    }

    fun notifySessionToken(token: String) {
        if (!SESSION_TOKEN_HEX.matches(token)) return
        if (exchangeInProgress) return
        exchangeInProgress = true
        pendingSessionToken = token
        runCatching { ControlPlaneHttp.cookieJar.syncFromWebView() }
        val hawkInfo = runCatching { authRepository.buildOAuthHawkHeader(token) }.getOrNull()
        if (hawkInfo == null || !helper.isRunning) {
            fallbackSession(token)
            return
        }
        val (hawkHeader, bodyJson) = hawkInfo
        val script = """
            (function() {
              fetch('https://api.accounts.firefox.com/v1/oauth/token', {
                method: 'POST',
                headers: {
                  'Authorization': ${jsString(hawkHeader)},
                  'Content-Type': 'application/json'
                },
                body: ${jsString(bodyJson)}
              }).then(function(res) {
                return res.text().then(function(text) {
                  sabiWebChannel.onOAuthResult(res.status, text);
                });
              }).catch(function(err) {
                sabiWebChannel.onOAuthError(err ? (err.message || ('' + err)) : 'fetch_failed');
              });
            })();
        """.trimIndent()
        if (!helper.evaluate(script)) fallbackSession(token)
    }

    private fun reply(messageId: String, command: String, dataJson: String) {
        val script = "window.__sabiReply && window.__sabiReply(" +
            "${jsString(FXA_CHANNEL_ID)}, ${jsString(messageId)}, ${jsString(command)}, ${jsString(dataJson)});"
        if (!helper.evaluate(script)) AppLogger.w(TAG, "could not answer webchannel command $command")
    }
}

private fun isSuccessUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val u = url.lowercase()
    return u.contains("confirmed") ||
        u.contains("/connect_another_device") ||
        u.contains("/pair") ||
        u.contains("/settings") ||
        u.contains("/complete") ||
        u.contains("/verified") ||
        u.contains("success")
}

private sealed interface HelperStatus {
    data object Starting : HelperStatus
    data object Open : HelperStatus
    data object Closed : HelperStatus
    data class Failed(val message: String, val runtimeMissing: Boolean) : HelperStatus
}

/**
 * Desktop sign-in. The Mozilla account pages open in a separate WebView2 (Microsoft Edge)
 * window, the same engine Windows uses for its own sign-in dialogs; this screen shows the
 * progress and lets the user reopen or cancel that window.
 */
@Composable
fun BrowserLoginScreen(
    startUrl: String,
    busy: Boolean,
    authRepository: FxaAuthRepository,
    errorMessage: String? = null,
    onTokensCaptured: (String, String?, Long) -> Unit,
    onSessionCaptured: (String) -> Unit,
    onCanceled: () -> Unit,
) {
    val currentOnTokens by rememberUpdatedState(onTokensCaptured)
    val currentOnSession by rememberUpdatedState(onSessionCaptured)
    val currentOnCanceled by rememberUpdatedState(onCanceled)
    val currentBusy by rememberUpdatedState(busy)
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<HelperStatus>(HelperStatus.Starting) }
    var attempt by remember { mutableIntStateOf(0) }
    val windowTitle = stringResource(Res.string.login_title)

    DisposableEffect(startUrl, attempt) {
        status = HelperStatus.Starting
        val helper = SignInHelper()
        val bridge =
            WebChannelBridge(
                helper,
                authRepository,
                onTokens = { a, r, e -> scope.launch(Dispatchers.Main) { if (!currentBusy) currentOnTokens(a, r, e) } },
                onSessionToken = { t -> scope.launch(Dispatchers.Main) { if (!currentBusy) currentOnSession(t) } },
            )
        helper.start(
            url = startUrl,
            userAgent = FIREFOX_DESKTOP_UA,
            script = SABI_CHANNEL_SHIM + "\n" + WEBCHANNEL_HOOK_JS,
            title = windowTitle,
            cookies = CookieManager.getInstance().entries(),
            listener =
                object : SignInHelper.Listener {
                    override fun onReady() {
                        scope.launch(Dispatchers.Main) { status = HelperStatus.Open }
                    }

                    override fun onBridge(source: String, data: String) = bridge.dispatch(source, data)

                    override fun onUrl(url: String) {
                        if (isSuccessUrl(url)) bridge.onSessionVerified()
                    }

                    override fun onLoaded(url: String) {
                        helper.evaluate(WEBCHANNEL_HOOK_JS)
                        if (isSuccessUrl(url)) bridge.onSessionVerified()
                    }

                    override fun onCookies(url: String, cookies: List<SignInHelper.Cookie>) {
                        val manager = CookieManager.getInstance()
                        cookies.forEach { manager.setCookie(url, "${it.name}=${it.value}; Domain=${it.domain}") }
                        runCatching { ControlPlaneHttp.cookieJar.syncFromWebView() }
                    }

                    override fun onClosed() {
                        scope.launch(Dispatchers.Main) {
                            status = HelperStatus.Closed
                            if (!currentBusy) currentOnCanceled()
                        }
                    }

                    override fun onFailed(message: String, runtimeMissing: Boolean) {
                        AppLogger.w(TAG, "sign-in window failed: $message")
                        scope.launch(Dispatchers.Main) { status = HelperStatus.Failed(message, runtimeMissing) }
                    }
                },
        )
        onDispose { helper.close() }
    }

    Box(
        Modifier.fillMaxSize().background(sabiAuroraBrush()),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            SabiMark(size = 88.dp)
            Spacer(Modifier.height(20.dp))
            Text(stringResource(Res.string.login_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                when (val s = status) {
                    is HelperStatus.Failed ->
                        if (s.runtimeMissing) stringResource(Res.string.desktop_signin_webview_missing) else s.message
                    else -> stringResource(Res.string.desktop_signin_window_help)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            if (busy || status == HelperStatus.Starting) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (errorMessage != null) {
                Spacer(Modifier.height(12.dp))
                Text(errorMessage, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(20.dp))
            val failed = status as? HelperStatus.Failed
            if (failed?.runtimeMissing == true) {
                Button(onClick = { WindowsShell.openUrl("https://go.microsoft.com/fwlink/p/?LinkId=2124703") }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.desktop_signin_get_webview))
                }
                Spacer(Modifier.height(8.dp))
            }
            if (status == HelperStatus.Closed || failed != null) {
                Button(onClick = { attempt++ }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.desktop_signin_reopen))
                }
                Spacer(Modifier.height(8.dp))
            }
            OutlinedButton(onClick = { currentOnCanceled() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.cancel))
            }
        }
    }
}
