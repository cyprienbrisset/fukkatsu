# Communication Integration — Design Spec
_2026-09-05_

## Objectif

Intégrer Messenger, WhatsApp, Google Meet, Google Chat et Google Agenda directement dans l'interface Fukkatsu avec le design Sumi, sans GMS, sans root.

---

## 1. Contacts récents (Messenger + WhatsApp)

### Source de données
Le `MediaListenerService` (existant, `NotificationListenerService`) est étendu pour intercepter les notifications de `com.facebook.aloha.app.whatsapp` et `com.facebook.aloha.app.messenger` (packages Portal).

Pour chaque notification entrante :
- **Nom** : `notification.extras[EXTRA_TITLE]`
- **Avatar** : `notification.largeIcon` (Bitmap) → compressé en PNG en mémoire
- **Action de rappel** : première action de la notification contenant "Appel" ou "Call" ; sinon l'intent de contenu (ouvre la conversation)
- **Package** : identifie l'app source

### Stockage
`RecentContactsRepository` — singleton exposant un `StateFlow<List<RecentContact>>` mis à jour à chaque notif. Capacité max : 6 contacts, triés par `lastSeenMs` décroissant, dédupliqués par `(packageName, name)`.

Pas de persistance sur disque (liste en RAM) — les contacts se reconstituent à la prochaine utilisation.

```kotlin
data class RecentContact(
    val key: String,          // "$pkg:$name"
    val name: String,
    val avatar: Bitmap?,
    val packageName: String,
    val lastSeenMs: Long,
    val tapIntent: PendingIntent?,  // action rappel ou conversation
)
```

### UI — HomeScreen
**Landscape** : bande horizontale `RecentContactsStrip` dans le panel gauche, sous `AmbientBanner`. Affiche jusqu'à 4 contacts (les 6 sont en scroll horizontal).  
**Portrait** : même composable, entre `AmbientBanner` et le label "MES APPS".

Chaque contact : cercle avatar 52 dp (initiale si pas d'avatar) + nom tronqué 2 lignes + tap = lance `tapIntent`.

Si la liste est vide, le composable est invisible (`height(0)`), pas de placeholder.

---

## 2. Google Meet & Chat (WebView persistant)

### Architecture
`PersistentWebViewPool` — object singleton qui garde une instance `WebView` par clé (`"meet"`, `"chat"`). Les WebViews sont créées au premier accès et réutilisées ; leurs cookies/session WebStorage persistent via le stockage WebView standard d'Android.

Pas de `clearCookies` ni `clearData` — la session Google reste active entre les ouvertures.

### UI — GoogleScreen
L'onglet Google reçoit deux nouvelles tuiles au-dessus des shortcuts apps existants :

```
[ Meet  ▶ ]   [ Chat  ▶ ]
```

Tap → ouvre `GoogleWebSheet` : BottomSheet full-height (ou navigation vers un écran dédié) avec la WebView persistante chargée sur `https://meet.google.com` ou `https://chat.google.com`.

Un bouton de fermeture (HankoSeal "朱") permet de revenir à l'onglet Google sans détruire la WebView.

### Première connexion
La WebView charge le site normalement. Google détecte l'absence de compte et affiche sa page de connexion web standard. L'utilisateur se connecte une fois ; les cookies persistent.

---

## 3. Google Agenda (ICS)

### Configuration
L'utilisateur colle son URL ICS privée dans **Réglages → Agenda Google**. Stockée dans DataStore (`google_ics_url`).

URL ICS privée Google : `https://calendar.google.com/calendar/ical/EMAIL/private-TOKEN/basic.ics`

### Fetch & Parse
`IcsCalendarRepository` remplace `CalendarRepository` dès qu'une URL ICS est configurée.

- Fetch HTTP via OkHttp (déjà en dépendance) toutes les 30 min (ou au lancement)
- Parse les blocs `VEVENT` : `DTSTART`, `DTEND`, `SUMMARY`, `LOCATION`, `DESCRIPTION`, `URL` (pour Meet)
- Filtre : événements entre `now` et `now + 7 jours`
- Détecte les liens Meet dans `DESCRIPTION` ou `URL` (regex `meet.google.com`)

Résultat : `List<CalEvent>` — même type que l'existant, zéro changement côté ViewModel/UI.

### UI
La section "PROCHAINS ÉVÉNEMENTS" dans `GoogleScreen` est inchangée. Si l'URL ICS n'est pas configurée, on affiche :

> "Ajoute ton URL d'agenda Google dans les Réglages pour voir tes événements."

---

## 4. Réglages

`SettingsScreen` reçoit une nouvelle ligne **"Agenda Google"** → `IcsSettingsScreen` :
- Champ texte pour coller l'URL ICS
- Bouton "Vérifier" (fait un fetch test et affiche le nombre d'événements trouvés)
- Bouton "Effacer"

Route : `Routes.ICS_SETTINGS` dans `AppNav`.

---

## Fichiers à créer / modifier

| Fichier | Action |
|---|---|
| `system/MediaListenerService.kt` | Étendre pour capturer notifs Messenger/WhatsApp |
| `integration/RecentContactsRepository.kt` | Nouveau — StateFlow des contacts récents |
| `integration/IcsCalendarRepository.kt` | Nouveau — fetch + parse ICS |
| `ui/home/RecentContactsStrip.kt` | Nouveau — composable bande de contacts |
| `ui/home/HomeScreen.kt` | Insérer RecentContactsStrip |
| `ui/home/HomeViewModel.kt` | Exposer contacts récents |
| `ui/google/GoogleWebSheet.kt` | Nouveau — WebView persistante Meet/Chat |
| `ui/google/PersistentWebViewPool.kt` | Nouveau — cache WebView par clé |
| `ui/google/GoogleScreen.kt` | Tuiles Meet/Chat + ICS hint |
| `ui/google/GoogleViewModel.kt` | Source ICS + events |
| `ui/settings/IcsSettingsScreen.kt` | Nouveau — config URL ICS |
| `ui/settings/SettingsScreen.kt` | Ligne "Agenda Google" |
| `ui/AppNav.kt` | Route ICS_SETTINGS |
| `data/PreferencesRepository.kt` | Clé `google_ics_url` (DataStore) |

---

## Contraintes

- Pas de nouvelles dépendances (OkHttp, DataStore, Room déjà présents)
- Le `NotificationListenerService` est déjà déclaré et accordé via ADB — pas de popup permission
- Les WebViews Meet/Chat partagent le processus WebView d'Android → cookies partagés avec `WebAppActivity` si même domaine
- ICS : parser custom minimal (pas de librairie ical4j) — VEVENT only, timezone UTC+offset
