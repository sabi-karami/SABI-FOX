# Ten-account prototype — not a production release

This branch supports up to 10 separate Mozilla account slots on one device. Each requires an
individual Mozilla login and any verification code requested by Mozilla. Only the **active**
account balance is refreshed; inactive balances are last-known values with timestamps. Switch
only after disconnecting the VPN, and reconnect manually. Session credentials are stored in an
Android Keystore-backed encrypted vault. Email addresses, passwords and codes are not embedded.
Account identity is retrieved from Mozilla's profile API, and duplicates are rejected across slots.

## Isolation and limitations

The debug APK uses `com.sabifox.vpn.prototype` and installs alongside the stable app, not as an
update. It has a temporary build signing key, differs from the production signer, and disables
background updates. Its login WebView inherits an unresolved origin-validation security risk.
This prototype is not suitable for privacy-sensitive use. Account transfer between phones,
automatic login, automatic retrieval of email codes and automatic inactive-account refresh are
not implemented. OAuth token refresh, VPN switching, balance, session migration and login have
not been validated on an Android device. Mozilla may require sign-in or verification again.

## Manual checks on a test device

1. Install the debug APK separately; sign in with your account and email code.
2. Settings > Accounts: disconnect, add an account in a second slot and verify its displayed identity.
3. Switch among at least three accounts (including slots 1, 2 and 10), reconnect manually and
   compare balances with the service. Verify offline and expired-session behavior, restart, app
   removal and duplicate accounts.
4. Document tests and errors without including passwords, verification codes, or tokens.

No production release should be published before a device test and security review.
