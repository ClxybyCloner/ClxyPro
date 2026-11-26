<p align="center">
  <a href="README_fa.md"><img src="https://img.shields.io/badge/🇮🇷_Persian-7c5cff?style=for-the-badge" alt="نسخه فارسی"></a>
</p>

<p align="center">
  <img src="assets/banner.png" alt="CLXY — Fast. Private. Yours." width="100%">
</p>

<p align="center">
  <a href="https://github.com/ClxybyCloner/ClxyPro/releases/latest"><img src="https://img.shields.io/github/v/release/ClxybyCloner/ClxyPro?label=latest&color=7c5cff" alt="Latest release"></a>
  <a href="https://github.com/ClxybyCloner/ClxyPro/releases"><img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 8.0+"></a>
  <img src="https://img.shields.io/badge/arch-arm64--v8a-4f8cff" alt="arm64-v8a">
  <a href="https://github.com/ClxybyCloner/ClxyPro/releases"><img src="https://img.shields.io/github/downloads/ClxybyCloner/ClxyPro/total?label=downloads" alt="Total downloads"></a>
</p>

<h1 align="center">ClxyPro — Hyper &amp; Quantum for Android</h1>

<p align="center">
  <img src="https://readme-typing-svg.demolab.com?font=Fira+Code&weight=600&size=24&duration=2800&pause=1200&color=7C5CFF&center=true&vCenter=true&width=700&lines=Fast.+Private.+Yours.;Official+ClxyPro+Android+releases;One+tap+away+from+the+newest+build" alt="Fast. Private. Yours.">
</p>

This is the **official download & update channel** for the ClxyPro Android app.
Every release is published here — and the in-app updater checks this exact
repository, so if you're running ClxyPro, you're always one tap away from the
newest build.

<p align="center">
  <img src="assets/wave.svg" alt="" width="99%">
</p>

## ⬇️ Download

**Latest: 1.5.29**, released November 26, 2026

- 📦 [Download the ARM64 APK](https://github.com/ClxybyCloner/ClxyPro/releases/download/v1.5.29/ClxyPro-1.5.29-arm64-v8a.apk)
- [View the SHA-256 checksum](https://github.com/ClxybyCloner/ClxyPro/releases/download/v1.5.29/SHA256SUMS.txt)
- 🗂️ [Browse all releases](https://github.com/ClxybyCloner/ClxyPro/releases)

The source code lives in this repository.

---

## ⚡ Why CLXY

- 🌍 **The open internet, in your pocket** — unblock the sites, apps and services restricted in your region. One tap and you're in.
- 🚀 **Built for speed** — multi-layer routing on a Go-powered core with HTTP/2 and HTTP/3 (QUIC) stays fast and stable, even on flaky mobile data.
- 🔒 **Your traffic stays yours** — a real encrypted VPN tunnel for the whole device, with per-app control over what goes through CLXY.
- 👨‍👩‍👧 **One subscription for everyone** — share your connection with nearby devices over Wi-Fi or hotspot (SOCKS5 `1081` / HTTP `8080`).
- 👆 **No setup headaches** — open the app, tap connect, done.
- 🔄 **Always up to date** — the app notifies you the moment a new build is out; nothing installs without your approval.
- 🪶 **Light on your phone** — just ~13 MB, smooth on Android 8.0 and up, even older phones.

---

## Advanced VPN Routing

CLXY routes traffic through a multi-layered path — not a single VPN hop:

This architecture combines Xray technology with the Mozilla VPN infrastructure to establish a multi-layered network communication path. Its primary goal is to improve traffic management flexibility, isolate network routes, and provide more precise control over inbound and outbound traffic.

Features:

* Multi-Layer Routing: Establishes a chained communication path between the VPN ingress and egress layers.
* Mozilla VPN Integration: Leverages WireGuard infrastructure to manage outbound traffic routing.
* Smart Traffic Routing: Directs traffic based on predefined routing policies.
* Dedicated Tunnel Management: Provides independent control over the outbound tunnel and its connection status.
* IP Leak Protection: Prevents exposure of the original server IP in the event of a tunnel failure.
* Fault Handling & Recovery: Incorporates mechanisms for connection monitoring and automatic route recovery.

Network Architecture:

VLESS → Xray Core → Linux Routing → Mozilla VPN → Internet

This architecture is designed to provide a flexible, scalable, and manageable networking infrastructure.

---

## 📱 Android edition — Hyper & Quantum

- **Two engines, one app** — **CLXY Hyper**: the Mozilla VPN egress layer of CLXY's multi-layer routing path, over HTTP/3 MASQUE with automatic exit routing (Germany first, then the fastest working route) and a live server list with pings. **CLXY Quantum**: direct anycast mesh with a serial gate, a server list of 16 relays with real TCP pings, and pin-a-relay.
- **Connects in ~2 seconds** — session and OAuth tokens are cached on first sign-in, so reconnecting skips the slow auth round-trips.
- **In-app updater** — download and install new builds inside the app; nothing installs without your approval.
- **Made for Android** — Quick Settings tile to connect from anywhere, per-app split tunneling, and local SOCKS5 (`127.0.0.1:1080`) and HTTP (`127.0.0.1:8080`) proxies for other apps.
- **Five languages** — English, Persian, Russian, Spanish and Chinese.

---

## CLXY core architecture

CLXY pairs a native Android interface with two Go networking cores, one per engine. The cores run as native binaries spawned by the app — the client-side end of CLXY's multi-layer routing path. The UI manages them through process control and reads connection status from their output, while the cores handle network traffic.

In VPN mode, Android's `VpnService` creates a virtual TUN interface. A user-space TCP/IP stack processes packets and forwards TCP connections to the core's SOCKS5 proxy. The built-in DNS resolver maps domain names to temporary IP addresses so routing rules can still match domains. These rules choose whether to use the proxy, connect directly, or block a connection. App selection controls which apps use the VPN.

The cores connect to the gateway over HTTP/2 CONNECT (TLS) or HTTP/3 CONNECT (QUIC). From there, traffic follows CLXY's multi-layer path: VLESS → Xray Core → Linux Routing → Mozilla VPN → Internet. They also expose a local SOCKS5 proxy (`127.0.0.1:1080`) and HTTP proxy (`127.0.0.1:8080`) for other apps on the device. The Android VPN path handles TCP and DNS; other UDP traffic is not tunneled.

---

## Install in 60 seconds

1. **Download** the APK from the link above.
2. **Open it** — Android will ask you to allow *"Install unknown apps"* for your browser. Allow it once.
3. **Tap Install.** Done.

A subscription is required to use CLXY.

---

## ✅ Verify your download

On a computer, calculate the APK's SHA-256:

```bash
sha256sum ClxyPro-1.5.29-arm64-v8a.apk
```

Compare the output with this expected hash:

```text
578a24e60d019308f8e72cc04da532c8127c7e0a7bda51c470fd9321339ce880  ClxyPro-1.5.29-arm64-v8a.apk
```

If they match, the file is exactly what we published.

---

## 🔄 How updates work

CLXY periodically checks this repo's **Releases** for a newer version. When one
is found, you get a notification inside the app — tap it to download, then
confirm the install yourself. The app never installs anything behind your back.

---

## 📋 Current build specs

| | |
|---|---|
| Package | `com.clxypro.vpn` |
| Version / version code | `1.5.29` / `36` |
| Architecture | `arm64-v8a` |
| Minimum Android | 8.0 (API 26) |
| Size | 13.5 MB |

---

## ❓ FAQ

**Where is the source code?**
The source code is in this repository.

**I need an older version.**
Older builds stay available on the [Releases page](https://github.com/ClxybyCloner/ClxyPro/releases).

**Sharing my connection — is that safe?**
The LAN SOCKS5 (`1081`) and HTTP (`8080`) proxies have no password by design, so **only turn them on on a network you trust**, and switch them off when you're done. Anyone on your local network could use them.

---

<p align="center">
  <img src="assets/wave.svg" alt="" width="100%">
</p>

## 📜 Terms

The source code is in this repository. Official signed releases and checksums are published here.
