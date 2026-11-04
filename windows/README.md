# SABI-FOX for Windows

The Windows app is the Android app's own code running on the desktop: the same screens,
settings, Guardian / Mozilla account logic and sing-box configuration, compiled with
Compose Multiplatform. Only the platform layer is new.

| Android | Windows |
| --- | --- |
| `VpnService` + libbox | `sabifox-core.exe` (same pinned MASQUE sing-box fork) with a Wintun adapter |
| Proxy-only mode | Proxy-only mode, optionally set as the Windows system proxy |
| Per-app split tunneling | Per-program split tunneling (installed programs, running programs or any `.exe`) |
| Quick Settings tile / widget | Notification-area (tray) icon with connect / disconnect |
| Android Keystore | Windows DPAPI (keys bound to your Windows account) |
| In-app WebView sign-in | WebView2 (Microsoft Edge) sign-in window |
| Self-update from GitHub releases | Same, verified with SHA-256 **and** an Ed25519 signature |
| English | English and Persian (right-to-left, Vazirmatn font) |

## Download

Every `v*.*.*` release contains, next to the APKs:

- `SABI-FOX-Windows-Setup-x64.exe` – installer (per user, no administrator rights needed to install)
- `SABI-FOX-Windows-Portable-x64.zip` – portable build; settings stay in `data\` next to `SABI-FOX.exe`
- `SHA256SUMS-windows.txt` and `SHA256SUMS-windows.txt.sig`

Windows 10 1809 or newer (64-bit) is required.

## Administrator rights

The system-wide VPN creates a network adapter, which Windows only allows for administrators.
SABI-FOX asks to restart itself as administrator when you connect in VPN mode. Proxy-only mode
never needs administrator rights. *Settings → Windows → Always run as administrator* makes this
permanent; *Start with Windows* then uses a scheduled task so it still starts at sign-in.

## Layout

```
windows/
  src/main/kotlin/      shared Android sources (ported by tools) + desktop platform layer
  src/main/composeResources/  strings (values, values-fa), fonts, icons
  core/main.go          sabifox-core: stdin/stdout wrapper around the sing-box fork
  signin/               SabiFoxSignIn.exe: WebView2 sign-in window (C#, .NET Framework 4.8)
  packaging/            icon and Inno Setup script
```

The configuration and the proxy credential are passed to the engine over a pipe and never
written to disk. If SABI-FOX exits or crashes, the pipe closes and the engine shuts down,
removing its routes and the system proxy; a stale system proxy is also repaired at the next start.

## Building

CI (`.github/workflows/windows-ci.yml`) builds everything on `windows-latest`. Locally:

```bash
# 1. engine (Go, from Vauth/sing-box at the commit in windows-ci.yml)
cp windows/core/main.go <sing-box>/cmd/sabifox-core/
GOOS=windows CGO_ENABLED=0 go build -tags with_quic,with_utls,with_clash_api,with_wireguard,badlinkname,tfogo_checklinkname0 \
  -ldflags "-checklinkname=0 -s -w" -o windows/native/windows/sabifox-core.exe ./cmd/sabifox-core
# 2. sign-in window
dotnet publish windows/signin/SabiFoxSignIn.csproj -c Release -o windows/native/windows/signin
# 3. app (JDK 21)
gradle -p windows run              # or createDistributable
```
