# Two-account prototype — NOT A PRODUCTION RELEASE

This experimental branch adds two independent on-device account slots. The `debug` build uses
`com.sabifox.vpn.prototype`, installs alongside the stable app, and disables background update
checks/installations. It has a temporary debug signing key and cannot update the stable app.
There are no embedded passwords or tokens. Profile email is read from the Mozilla profile API.
The suggested addresses are labels only; check the actual account after signing in.

## Test procedure

1. Install the debug APK on a test device. Sign in to the first Mozilla account through the
   existing in-app Mozilla login and complete the email code yourself.
2. Open Settings > Accounts (two-account prototype). Check account identity and refresh balance.
3. Disconnect the VPN. Choose slot 2 and sign in to a second account using Mozilla and its code.
4. Reopen Accounts. Check identities, switch between slots when disconnected, reconnect manually,
   and refresh balance. Inactive balances are last-known and include a timestamp.
5. Test app restart, both accounts, cancellation, expired sessions, offline conditions, and VPN
   after switching. Do not depend on this prototype for privacy-critical use.

**Not implemented:** phone-to-phone transfer, automatic login / OTP capture, simultaneous balance
refresh for both accounts, forced email autofill. Android autofill depends on the login WebView and
provider. Sessions and quota are encrypted under Android Keystore. Removing the app removes them.
VPN/identity behavior and migration are untested on a device. No guarantee of service compatibility.
The inherited login WebView bridge has an outstanding origin-validation risk and is not redesigned
here. The debug signer and package differ from release builds, and the production updater will
reject them. This prototype must not be tagged as a release.
