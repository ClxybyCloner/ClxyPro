# ClxyPro Android VPN Client

<p align="center">
  <img src="app/src/main/res/drawable/clxy_logo.png" width="96" alt="ClxyPro Logo" />
</p>

**ClxyPro** is a modern, high-performance, multi-engine VPN and proxy client for Android.

## Features
- **Multi-Engine Core**: Built-in support for Psiphon, Tor, OpenRung, Lantern, SSTP, and Firefox VPN protocols.
- **Modern Cyber-Neon UI**: Sleek glassmorphism interface with real-time ping indicator, animated dotted world map, dynamic status alerts, and one-tap connect.
- **Split Tunneling**: Choose which apps bypass or route through the encrypted tunnel.
- **LAN Sharing**: Share encrypted SOCKS5 and HTTP proxy connections across your local Wi-Fi network.
- **Automatic Multi-ABI APK Builds**: Native releases packaged for ARM32 (`armeabi-v7a`), ARM64 (`arm64-v8a`), and universal APKs.

## Architecture
- **Language**: Kotlin + Jetpack Compose
- **Native Bridges**: C/C++ & Go multicore engines via userspace `c2tun` (hev-socks5-tunnel)
- **Minimum SDK**: 26 (Android 8.0 Oreo)
- **Target SDK**: 35 (Android 15)

## Building
```bash
# Verify cores manifest & ABI alignment
./tools/build-apks.sh --check

# Assemble Release APKs
./tools/build-apks.sh
```
APKs will be placed in `dist/`.
