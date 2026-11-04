<div align="center">

<img src="docs/logo.svg" alt="SABI-FOX" width="132"/>

# SABI-FOX

**Private · Swift · Yours**

![Android](https://img.shields.io/badge/Android_8%2B-070B14?style=for-the-badge&logo=android&logoColor=3DF5C2)
![Kotlin](https://img.shields.io/badge/Kotlin-070B14?style=for-the-badge&logo=kotlin&logoColor=A78BFA)
![Compose](https://img.shields.io/badge/Jetpack_Compose-070B14?style=for-the-badge&logo=jetpackcompose&logoColor=3DF5C2)
![License](https://img.shields.io/badge/License-MIT-070B14?style=for-the-badge)

*A distinctive, privacy-focused Android client for Mozilla's VPN network — with the
"Nocturne Aurora" design: midnight surfaces, neon-mint accents and an animated aurora connect orb.*

</div>

## ✨ Features

- **Firefox Account sign-in** through the official Mozilla page
- **System-wide tunnel** or **proxy-only mode**
- **Split tunneling**, **DNS-over-HTTPS**, local-network bypass
- **HTTP/2 & HTTP/3 (MASQUE)** transport, TLS fragmentation, uTLS fingerprints
- **Upstream proxy chaining**, custom edge override, TCP Fast Open, custom MTU
- **Live diagnostics**: exit IP, latency, session data, quota
- **Quick Settings tile & home-screen widget**
- **Signed in-app updates** from this repository's releases

## 🚀 Getting started

1. Download the APK for your device from **[Releases](https://github.com/sabi-karami/SABI-FOX/releases/latest)**
   (`arm64-v8a` for most modern phones, `armeabi-v7a` for older ones, or `universal`).
2. Install, open **SABI-FOX** and tap **Sign in with Firefox**.
3. Pick a location and tap the orb.

> [!NOTE]
> Access, quota and availability are controlled by Mozilla. SABI-FOX connects only to
> official endpoints and respects account quotas.

## 🛠 Build from source

Requirements: JDK 17, Android SDK 35 (Build-Tools 35.0.0), Gradle 8.10.2.

```bash
git clone https://github.com/sabi-karami/SABI-FOX.git && cd SABI-FOX
mkdir -p app/libs
# fetch the pinned native core listed in .github/workflows/android-ci.yml (LIBBOX_URL) and verify:
curl -L -o app/libs/libbox.aar "$LIBBOX_URL"
echo "f1b59f4fab207b4c19f8ea3d145bc4dd0e834a530081d0dc045f4b795d6eac0b  app/libs/libbox.aar" | sha256sum -c
gradle :app:assembleDebug
```

Pushing a `v*.*.*` tag builds, signs and publishes a release automatically.

## 📄 License

MIT — see [LICENSE](LICENSE). Bundled components keep their own licenses —
see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
