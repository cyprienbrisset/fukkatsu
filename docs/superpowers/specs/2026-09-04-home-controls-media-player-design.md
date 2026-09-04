# MyPortal — Contrôles d'accueil + Player média

- **Date** : 2026-09-04
- **Statut** : Design validé, prêt pour planification
- **Base** : MyPortal v3 (Sumi) sur `main`

## Intention

Ajouter à l'accueil : (A) un **cluster de 3 boutons-sceaux** en haut à droite — Ne pas déranger, Éteindre l'écran, Réglages — et (B) un **mini-player média** sous l'horloge montrant la lecture en cours avec commandes précédent / play-pause / suivant. Style Sumi (sceau vermillon + icône). Chaque capacité système est gardée par une **permission accordée une fois** par l'utilisateur, avec redirection vers l'écran système si manquante.

## A. Cluster de contrôles (haut-droite)

Composant réutilisable **`SealIconButton`** (dans `ui/sumi/`) : carré arrondi **vermillon (Shu)** + icône (`OnShu`), même look que `HankoSeal` mais avec une `ImageVector` au lieu d'un caractère. Optionnellement un état « actif » (bordure/teinte) pour les bascules.

Trois boutons en `Row` (espacés), alignés `TopEnd`, sous la barre système (`statusBarsPadding`) :

1. **Ne pas déranger** (`SealIconButton`, icône cloche / cloche-barrée selon l'état)
   - Bascule via `NotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_NONE ↔ ALL)`.
   - Permission : **`ACCESS_NOTIFICATION_POLICY`**. Si `notificationManager.isNotificationPolicyAccessGranted == false`, ouvrir `Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS` (petit texte d'explication d'abord).
   - L'icône reflète l'état DND courant (observé).

2. **Éteindre l'écran** (`SealIconButton`, icône power/écran)
   - Action : `DevicePolicyManager.lockNow()` — verrouille et éteint l'écran (équivalent du contrôle Portal).
   - Permission : **Administrateur d'appareil** avec politique `force-lock`. Nécessite un `DeviceAdminReceiver` déclaré + activation via `DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN`. Si non actif au 1ᵉʳ appui → lancer l'ajout d'admin (avec explication), sinon `lockNow()`.

3. **Réglages** (`SealIconButton`, icône roue crantée)
   - Restaure le **fond rouge** (sceau vermillon) demandé, avec l'icône gear.
   - `onClick = onOpenSettings` (navigation existante).

Comportement permission générique : un helper vérifie la permission ; si absente, affiche une brève explication puis ouvre l'écran système ; au retour, l'action est de nouveau tentable.

## B. Mini-player média (sous l'horloge)

Placé dans la **colonne héro**, sous la ligne date/météo (et sous la prochaine alarme). **Masqué s'il n'y a aucune session média active.**

- **Source** : `MediaSessionManager.getActiveSessions(component)` où `component` est le `NotificationListenerService` de MyPortal. Requiert l'accès aux notifications.
- **Affichage** : pochette (petite, ~56 dp, via l'`artwork`/`albumArt` des métadonnées, sinon rien), **titre** + **artiste** (Gothic), le tout sobre.
- **Commandes** (3 `SealIconButton` ou icônes discrètes) : **précédent** (`skipToPrevious`), **play/pause** (`play`/`pause` selon `PlaybackState`), **suivant** (`skipToNext`) — envoyées via `MediaController.getTransportControls()`.
- **Live** : observer le `MediaController.Callback` (métadonnées + état) ; se met à jour à chaque changement. Si plusieurs sessions actives, prendre la première/la plus récente.
- Permission : **accès aux notifications** (`NotificationListenerService` + `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`). Si non accordé, le player affiche un petit bouton « Activer le player » qui ouvre l'écran système.

### Architecture player
- **`MediaListenerService : NotificationListenerService`** — déclaré au manifest avec `BIND_NOTIFICATION_LISTENER_SERVICE` ; sert de composant autorisé pour `getActiveSessions`. Pas de logique lourde ; sa seule présence + l'autorisation débloquent l'accès aux sessions.
- **`NowPlayingController`** (classe non-Compose testable là où c'est du pur calcul) — encapsule `MediaSessionManager`, expose un `StateFlow<NowPlaying?>` (`NowPlaying(title, artist, art, isPlaying)`) et des actions `next()/prev()/toggle()`. Gère l'enregistrement/désenregistrement des callbacks selon le cycle de vie.
- **`NowPlayingBar`** (Compose) — l'UI dans la colonne héro, alimentée par le controller via le `HomeViewModel`.
- `HomeViewModel` expose `nowPlaying: StateFlow<NowPlaying?>` et `dndEnabled: StateFlow<Boolean>`.

## Permissions & manifest (récap)
- `android.permission.ACCESS_NOTIFICATION_POLICY` (DND).
- `NotificationListenerService` + `<service>` avec `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` et l'intent-filter `android.service.notification.NotificationListenerService` (player).
- `DeviceAdminReceiver` + `<receiver>` avec `BIND_DEVICE_ADMIN`, méta `device_admin` déclarant `force-lock` (éteindre l'écran).
- Toutes **accordées manuellement** par l'utilisateur via les écrans système ; l'app ne les exige pas au démarrage.

## Portée / non-portée
- Inclus : les 3 boutons, le player now-playing, les flux de permission, le composant `SealIconButton`.
- Exclu : file d'attente/mix, réglage du volume, choix de la session si plusieurs (on prend la 1ʳᵉ), notifications personnalisées.

## Tests
- **JVM (pur)** : mapping `PlaybackState` → `isPlaying` ; sélection de la session active (première non-nulle) ; état d'icône DND selon `interruptionFilter`. Fonctions pures extraites du controller.
- **Compilation** : `assembleDebug` par tâche.
- **On-device (Portal)** : accorder les 3 permissions ; lancer une lecture (Spotify) → le player s'affiche et contrôle ; DND bascule ; bouton éteindre-écran verrouille l'appareil.

## Risques
- **Éteindre l'écran = Device Admin** : permission forte ; si l'utilisateur la refuse, le bouton reste inactif (redirige vers l'activation). `lockNow` verrouille (peut demander déverrouillage ensuite selon la config Portal) — à valider sur l'appareil.
- **Sessions média sans Play Services** : sur le Portal, `MediaSessionManager` est standard AOSP, indépendant de GMS ; Spotify publie une MediaSession → devrait marcher.
- **Pochette** : certaines apps ne fournissent pas l'artwork → prévoir un placeholder sobre (pas de crash).
- **NotificationListener** : le service tourne en tâche de fond ; garder son `onNotificationPosted` vide pour ne rien consommer d'autre que l'accès aux sessions.
