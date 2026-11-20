<p align="center">
  <a href="https://github.com/ClxybyCloner/ClxyPro/blob/main/README.fa.md">🇮🇷 نسخه فارسی</a>
</p>

<p align="center">
  <img src="app/src/main/res/drawable/clxy_logo.png" alt="ClxyPro logo" width="96">
</p>

<p align="center">
  <a href="https://github.com/ClxybyCloner/ClxyPro/releases/latest"><img src="https://img.shields.io/github/v/release/ClxybyCloner/ClxyPro?label=latest&color=7c5cff" alt="Latest release"></a>
  <a href="https://github.com/ClxybyCloner/ClxyPro/releases"><img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 8.0+"></a>
  <img src="https://img.shields.io/badge/arch-arm64--v8a-4f8cff" alt="arm64-v8a">
  <img src="https://img.shields.io/badge/arch-armeabi--v7a-4f8cff" alt="armeabi-v7a">
  <a href="https://github.com/ClxybyCloner/ClxyPro/releases"><img src="https://img.shields.io/github/downloads/ClxybyCloner/ClxyPro/total?label=downloads" alt="Total downloads"></a>
</p>

<h1 align="center">ClxyPro — Android VPN Client</h1>

<p align="center">
  <img src="https://readme-typing-svg.demolab.com?font=Fira+Code&weight=600&size=24&duration=2800&pause=1200&color=7C5CFF&center=true&vCenter=true&width=700&lines=Fast.+Private.+Yours.;Two+engines.+One+tap.;Open+source%2C+right+here" alt="Fast. Private. Yours.">
</p>

**ClxyPro** is a fast, modern, multi-engine VPN client for Android, written in
Kotlin with Jetpack Compose. This repository is both the **source code** and
the **official release channel**: every release is built here by GitHub
Actions, and the in-app updater checks this exact repository — so if you're
running ClxyPro, you're always one tap away from the newest build.

---

## ⬇️ Download

**Latest: 1.5.15**, released November 20, 2026

| APK | Size | |
|---|---|---|
| arm64-v8a (most phones) | 12.8 MB | 📦 [Download](https://github.com/ClxybyCloner/ClxyPro/releases/download/v1.5.15/ClxyPro-1.5.15-arm64-v8a.apk) |
| armeabi-v7a (older 32-bit phones) | 12.0 MB | 📦 [Download](https://github.com/ClxybyCloner/ClxyPro/releases/download/v1.5.15/ClxyPro-1.5.15-armeabi-v7a.apk) |
| universal (runs everywhere) | 23.3 MB | 📦 [Download](https://github.com/ClxybyCloner/ClxyPro/releases/download/v1.5.15/ClxyPro-1.5.15-universal.apk) |

🗂️ [Browse all releases](https://github.com/ClxybyCloner/ClxyPro/releases)

Not sure which one fits your phone? Grab **universal** — it runs on everything.
Once installed, the app picks the right APK for your phone by itself when it
updates.

---

## ⚡ Why ClxyPro

- 🌍 **The open internet, in your pocket** — unblock the sites, apps and services restricted in your region. One tap and you're in.
- 🔀 **Two engines, pick your route** — **CLXY Hyper** rides on your CLXY account, **CLXY Quantum** runs a MASQUE tunnel. Switch any time.
- 🔒 **Your traffic stays yours** — a real encrypted VPN tunnel for the whole device, with per-app split tunneling to choose what goes through ClxyPro.
- 👆 **Quick Settings tile** — connect or disconnect straight from the notification shade, without opening the app.
- 👨‍👩‍👧 **One connection for everyone** — share the tunnel with nearby devices over your hotspot as SOCKS5 (`1080`) / HTTP (`8080`) proxies.
- 🔄 **In-app updates** — the app tells you the moment a new build is out, downloads the right APK for your phone, and asks you before installing. Nothing installs behind your back.
- 📳 **Quiet by design** — connecting vibrates briefly instead of playing a sound.
- 🪶 **Light on your phone** — ~13 MB, smooth on Android 8.0 and up, even older phones.

---

## 🛠️ How it works

ClxyPro pairs a Kotlin + Jetpack Compose interface with native engine binaries.
The UI talks to the engines over a local loopback API to manage settings and
read connection status, while the engines handle the network traffic. Both
engines tunnel over MASQUE (RFC 9298).

In VPN mode, Android's `VpnService` creates a virtual TUN interface and routes
the whole device through the tunnel. Proxy-only mode skips the device-wide VPN
and just exposes a local SOCKS5 proxy (`1080`) and HTTP proxy (`8080`) that any
app can use. Per-app rules choose which apps go through the tunnel and which
connect directly.

---

## Install in 60 seconds

1. **Download** the APK from the link above.
2. **Open it** — Android will ask you to allow *"Install unknown apps"* for your browser. Allow it once.
3. **Tap Install.** Done.

Then open the app: **Hyper** asks you to sign in with your CLXY account
(only the sign-in token is kept on your phone, never your password),
**Quantum** asks for a serial the first time you connect with it.

---

## ✅ Verify your download

Check a file against the published hashes for **1.5.15** (from the next
release on, every release also ships a `SHA256SUMS.txt` next to its APKs, so
`sha256sum -c SHA256SUMS.txt` verifies everything at once):

```text
f8880b61985e8bea96cd933f43da071471879be555864d1facd196504441de25  ClxyPro-1.5.15-arm64-v8a.apk
9ee33a2d3d67ad96647e8b93a8bf73e90d9fab556d081907a5ba55382f96099b  ClxyPro-1.5.15-armeabi-v7a.apk
a2418dec36f0f24fcebedbc80f8cfe21661463e9845cd2561e489e85c823cfa8  ClxyPro-1.5.15-universal.apk
```

If they match, the file is exactly what we published.

---

## 🔄 How updates work

ClxyPro checks this repo's **Releases** every time the app opens and every time
you connect. When a newer version is found, you get a notification and a banner
on the home screen — tap it, and the app downloads the APK that matches your
phone's architecture and opens the system installer. You always confirm the
install yourself.

---

## 📋 Current build specs

| | |
|---|---|
| Package | `com.clxypro.vpn` |
| Version / version code | `1.5.15` / `22` |
| Architecture | `arm64-v8a`, `armeabi-v7a`, `universal` |
| Minimum Android | 8.0 (API 26) |
| Target Android | 15 (API 35) |
| Size | ~13 MB (per-architecture APK) |

---

## ❓ FAQ

**Where is the source code?**
Right here — this repository is the public source, and every release is built
from it by GitHub Actions.

**I need an older version.**
Older builds stay available on the [Releases page](https://github.com/ClxybyCloner/ClxyPro/releases).

**Hyper or Quantum — which one do I use?**
Hyper signs in with your CLXY account and just works. Quantum needs a serial
the first time, then connects without an account. Try both and keep the one
that's faster on your network.

**The hotspot proxy asks for nothing — is that safe?**
The hotspot SOCKS5/HTTP proxies have no password by design, so **only turn
them on on a hotspot you trust**, and switch them off when you're done. Anyone
connected to your hotspot could use them.

**Does ClxyPro keep my password?**
No. Hyper keeps only the sign-in token; your password is never stored on the
phone.
