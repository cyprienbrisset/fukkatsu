# Fukkatsu 復活

**Fukkatsu** is a custom Android home launcher built for the Meta Portal 2nd Gen (Android 10, no Google Mobile Services). It replaces the stock Portal UI with a minimalist Japanese-aesthetic interface designed for always-on ambient display use.

> 復活 (*fukkatsu*) — revival, resurrection.

---

## Features

### Home Screen
- **Customizable tile grid** — app shortcuts arranged in a medallion-style grid
- **Spring press animations** — tiles bounce with a medium-bouncy spring on tap
- **Ambient clock banner** — large Mincho-font time, date, weather and next alarm at a glance
- **Recent contacts strip** — last 6 Messenger/WhatsApp contacts (via NotificationListener), tap to open conversation
- **Now Playing bar** — media controls with seek bar, album art, tap to open player app
- **Volume slider** — quick system media volume control

### Google Integration tab
- **Meet & Chat WebView** — persistent Google Meet and Chat sessions in a full-screen sheet
- **App shortcuts** — long-press shortcuts for installed Google apps (Agenda, Meet, Chat…)
- **Calendar events** — upcoming events via native Calendar permission or a personal ICS URL
  - Configurable ICS URL (Settings → Agenda Google) for GMS-less setups
  - One-tap "Rejoindre" button on events with a Meet link

### FukkaStore (in-app store)
- Browse and install apps from Google Play without GMS using [gplayapi](https://github.com/whyorean/GPlayApi)
- Google account sign-in via AC2DM OAuth2 exchange
- Curated home (top free apps), search, category browsing

### System controls
- **DND mode** — do-not-disturb with configurable duration
- **Screen lock** — device admin lock
- **Alarm system** — Room-backed alarms with volume ramp and custom ringtone picker

### Day / Night theme
| Mode | Hours | Palette |
|------|-------|---------|
| **Washi** (day) | 07:00 – 19:59 | Parchment `#F2EDE3`, Ink `#14161C` |
| **Sumi** (night) | 20:00 – 06:59 | Deep black `#0D0E12`, Kinari `#ECE7DD` |

Theme switches automatically every minute based on the device clock.

---

## Device

Designed and tested on the **Meta Portal 2nd Gen** (codename *aloha*):
- Android 10 (API 29), 1280×800 landscape
- No GMS / no Google Play
- Meta's Aloha OS overlay
- Package verifier must be disabled for sideloaded APKs to install

---

## Setup

### Prerequisites
- Android Studio (Hedgehog or later) — JBR used as `JAVA_HOME`
- ADB (`~/Library/Android/sdk/platform-tools/adb`)
- Portal with USB Debugging enabled

### Build & deploy

```bash
# First time: provision the Portal (build → install → permissions → launcher)
bash scripts/provision-portal.sh 2221B01C9C02NQ --disable-verifier --set-launcher

# Subsequent updates (Portal already connected)
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The provision script handles:
1. Gradle build (uses Android Studio JBR, no system `java` needed)
2. APK install via ADB
3. DND and Device Admin permissions
4. Setting Fukkatsu as the default home launcher
5. Disabling the package verifier (required for FukkaStore installs)

### FukkaStore auth

Open the Store tab, tap **Se connecter**, enter your Google account credentials. The app exchanges an OAuth token for an AAS token via AC2DM, then uses gplayapi to query the Play catalog. No credentials are stored beyond the AAS token in DataStore.

### Google Calendar (ICS)

Go to Settings → **Agenda Google**, paste your Google Calendar ICS URL (from calendar.google.com → share → get shareable link). The app fetches and parses VEVENT entries without any external library.

---

## Architecture

```
com.cyprienbrisset.myportal
├── alarm/          Alarm scheduling, foreground service, volume ramp
├── data/           Room database (tiles, alarms), DataStore (settings), weather
├── integration/    Calendar (native + ICS), Google app shortcuts, recent contacts
├── launch/         Intent resolution for tile taps
├── media/          Now Playing via MediaController
├── store/          FukkaStore: auth, APK download/install, gplayapi wrappers
├── system/         DND, screen lock, NotificationListenerService, device admin
├── ui/
│   ├── alarms/     Alarm list + editor screens
│   ├── google/     Google tab (GoogleScreen, WebView sheet, PersistentWebViewPool)
│   ├── home/       HomeShell, HomeScreen, AmbientBanner, MedallionGrid, NowPlayingBar
│   ├── settings/   Settings, ICS config, weather config, tile editor
│   ├── store/      StoreScreen
│   ├── sumi/       Design-system components (Medallion, HankoSeal, SectionLabel…)
│   └── theme/      Color palettes (Sumi/Washi), typography, MaterialTheme wrappers
└── web/            WebAppActivity for in-app web views
```

**Stack:** Kotlin, Jetpack Compose, Navigation Compose, Room, DataStore, ViewModel, OkHttp, gplayapi.

---

## Design system

The UI is inspired by Japanese stationery and ink aesthetics:

| Token | Value | Usage |
|-------|-------|-------|
| `Sumi` | `#0D0E12` | Night background |
| `Kinari` | `#ECE7DD` | Night primary text |
| `SumiMuted` | `#9A9488` | Night secondary text |
| `Washi` | `#F2EDE3` | Day background |
| `Ink` | `#14161C` | Day primary text |
| `Shu` | `#C1272D` | Accent (vermilion red) |

Typography uses **Noto Serif JP** (Mincho weight family) throughout.

---

## License

MIT — see [LICENSE](LICENSE).
