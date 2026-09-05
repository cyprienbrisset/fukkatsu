<div align="center">

# 復活 Fukkatsu

**A custom Android home launcher for the Meta Portal 2nd Gen**

*Japanese ink aesthetics · Always-on ambient display · No GMS required*

---

![Android](https://img.shields.io/badge/Android-10%20%28API%2029%29-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202024-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-C1272D?style=for-the-badge)
![Platform](https://img.shields.io/badge/Device-Meta%20Portal%202nd%20Gen-0866FF?style=for-the-badge&logo=meta&logoColor=white)

</div>

---

## What is Fukkatsu?

**復活** (*fukkatsu*, "revival") replaces the stock Meta Portal UI with a launcher built around Japanese stationery aesthetics — deep Sumi ink at night, warm Washi parchment by day. Designed as an always-on ambient display for a home hub, it runs fully offline with no Google Mobile Services dependency.

---

## Features

### 🏠 Home Screen

| Feature | Description |
|---|---|
| **Tile grid** | Customizable app shortcuts in a medallion-style grid |
| **Spring press** | Tiles bounce with a medium-bouncy spring on tap (0.86 scale) |
| **Ambient clock** | Full-screen Mincho-font time, date, weather and next alarm |
| **Recent contacts** | Last 6 Messenger/WhatsApp contacts via `NotificationListenerService` |
| **Now Playing bar** | Media controls, seek bar, album art — tap to open the player |
| **Volume slider** | Quick system media volume adjustment |

### 🌐 Google Tab

| Feature | Description |
|---|---|
| **Meet & Chat** | Persistent WebView sessions in a full-screen sheet — no re-login |
| **App shortcuts** | Long-press shortcuts for installed Google apps |
| **Calendar events** | Upcoming events via READ_CALENDAR permission **or** a personal ICS URL |
| **Join Meet** | One-tap "Rejoindre" button on events containing a Meet link |

### 🛒 FukkaStore

| Feature | Description |
|---|---|
| **Google Play catalog** | Browse and install apps without GMS using [gplayapi](https://github.com/whyorean/GPlayApi) |
| **Auth** | Google account sign-in via AC2DM OAuth2 exchange, AAS token stored in DataStore |
| **Curated home** | Top free compatible apps, category chips, and full search |

### ⚙️ System

| Feature | Description |
|---|---|
| **Alarms** | Room-backed alarm scheduler with volume ramp and custom ringtone |
| **DND** | Do-not-disturb toggle with configurable duration |
| **Screen lock** | Device Admin screen-off |
| **Weather** | Open-Meteo weather via configurable coordinates |

---

## Day / Night theming

Fukkatsu switches color schemes automatically based on the device clock (checked every minute):

<table>
<tr>
<th align="center">🌙 Sumi — Night (20:00 – 06:59)</th>
<th align="center">☀️ Washi — Day (07:00 – 19:59)</th>
</tr>
<tr>
<td>

| Token | Color | Hex |
|---|---|---|
| Background | ![#0D0E12](https://img.shields.io/badge/-%230D0E12-0D0E12) | `#0D0E12` |
| Primary text | ![#ECE7DD](https://img.shields.io/badge/-%23ECE7DD-ECE7DD) | `#ECE7DD` |
| Muted text | ![#9A9488](https://img.shields.io/badge/-%239A9488-9A9488) | `#9A9488` |
| Surface | ![#191C23](https://img.shields.io/badge/-%23191C23-191C23) | `#191C23` |

</td>
<td>

| Token | Color | Hex |
|---|---|---|
| Background | ![#F2EDE3](https://img.shields.io/badge/-%23F2EDE3-F2EDE3) | `#F2EDE3` |
| Primary text | ![#14161C](https://img.shields.io/badge/-%2314161C-14161C) | `#14161C` |
| Muted text | ![#6E6B61](https://img.shields.io/badge/-%236E6B61-6E6B61) | `#6E6B61` |
| Surface | ![#E6E1D6](https://img.shields.io/badge/-%23E6E1D6-E6E1D6) | `#E6E1D6` |

</td>
</tr>
</table>

> **Accent — invariant across modes:** ![Shu](https://img.shields.io/badge/Shu%20vermilion-%23C1272D-C1272D) `#C1272D`

Typography uses **Noto Serif JP** (Mincho family) throughout.

---

## Architecture

```
com.cyprienbrisset.myportal
├── 🔔 alarm/          Scheduling, foreground service, volume ramp, boot receiver
├── 💾 data/           Room (tiles, alarms) · DataStore (settings) · Weather models
├── 🔗 integration/    Calendar (native + ICS) · Google app shortcuts · Recent contacts
├── 🚀 launch/         Intent resolution for tile taps
├── 🎵 media/          Now Playing via MediaController
├── 🛒 store/          FukkaStore: AC2DM auth · APK download/install · gplayapi wrappers
├── ⚙️  system/         DND · Screen lock · NotificationListenerService · Device admin
└── 🖌️  ui/
    ├── alarms/         Alarm list + editor
    ├── google/         Google tab · WebView sheet · PersistentWebViewPool
    ├── home/           HomeShell · AmbientBanner · MedallionGrid · NowPlayingBar
    ├── settings/       Settings · ICS config · Weather · Tile editor
    ├── store/          StoreScreen
    ├── sumi/           Design system (Medallion · HankoSeal · SectionLabel…)
    └── theme/          Color palettes · Typography · MaterialTheme wrappers
```

**Stack**

![Room](https://img.shields.io/badge/Room-Database-4285F4?style=flat-square&logo=android)
![DataStore](https://img.shields.io/badge/DataStore-Preferences-4285F4?style=flat-square&logo=android)
![OkHttp](https://img.shields.io/badge/OkHttp-HTTP%20client-48BB78?style=flat-square)
![gplayapi](https://img.shields.io/badge/gplayapi-Play%20catalog-EA4335?style=flat-square)
![Navigation Compose](https://img.shields.io/badge/Navigation-Compose-7F52FF?style=flat-square&logo=jetpackcompose)

---

## Device

> Designed and tested exclusively on the **Meta Portal 2nd Gen** (codename *aloha*).

| Property | Value |
|---|---|
| OS | Android 10 (API 29) |
| Resolution | 1280 × 800 landscape |
| GMS | ❌ None |
| ADB serial (example) | `2221B01C9C02NQ` |

Because the Portal has no functional GMS, the package verifier must be disabled for sideloaded APKs — the provision script handles this automatically.

---

## Setup

### Prerequisites

- **Android Studio** (Hedgehog or later) — its bundled JBR is used as `JAVA_HOME`
- **ADB** at `~/Library/Android/sdk/platform-tools/adb`
- Portal with **USB Debugging** enabled

### First-time provisioning

```bash
# Builds APK, installs, grants DND + Device Admin, sets as launcher,
# disables package verifier so FukkaStore can install apps.
bash scripts/provision-portal.sh 2221B01C9C02NQ --disable-verifier --set-launcher
```

### Incremental update

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug
adb -s 2221B01C9C02NQ install -r app/build/outputs/apk/debug/app-debug.apk
```

### FukkaStore sign-in

Open the **Store** tab → **Se connecter** → enter your Google account credentials. The app exchanges an OAuth token for an AAS token via AC2DM and uses gplayapi to query the Play catalog. Only the AAS token is persisted (DataStore), no password is stored.

### Google Calendar (ICS)

Go to **Settings → Agenda Google**, paste your Google Calendar ICS URL (from calendar.google.com → share → get shareable link). Events are fetched and parsed without any third-party library.

---

## License

```
MIT License — Copyright (c) 2024-2026 Cyprien Brisset
```

See [LICENSE](LICENSE) for the full text.
