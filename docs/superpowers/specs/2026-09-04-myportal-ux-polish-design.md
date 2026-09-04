# MyPortal v2 — UX/UI polish for Portal+ (gen 1 & 2)

- **Date** : 2026-09-04
- **Statut** : Design validé, prêt pour planification
- **Base** : MyPortal v1 (launcher Compose déjà mergé dans `main`)

## Objectif

Élever la qualité UX/UI de MyPortal, optimisée pour **Portal+ 1ʳᵉ gén** (15,6", 1920×1080 16:9, pivotant paysage/portrait) **et 2ᵉ gén** (14", 2160×1440 3:2, paysage). Quatre chantiers : icônes de tuiles, accueil « bannière + rangée » responsive, réveil robuste (compatible Android récent), sonnerie personnalisable.

Contraintes transverses : lecture à distance (« pièce »), cibles tactiles larges (≥ ~88 dp), typographie généreuse, responsive **paysage + portrait**.

## Workstream A — Icônes des tuiles

- **Tuiles APP** : charger l'icône réelle via `PackageManager.getApplicationIcon(packageName)` (Drawable → `ImageBitmap`/painter Compose), mise en cache mémoire. Fallback monogramme si l'app est absente.
- **Tuiles WEB** : charger un favicon via **Coil** (`AsyncImage`) depuis un service favicon par domaine, taille ~128 px. Fallback **monogramme coloré** (1ʳᵉ lettre du libellé, couleur dérivée du libellé) si échec/hors-ligne.
- **Icône perso** : si `TileEntity.iconRef` est renseigné (URI), il prime. Champ déjà présent ; l'édition d'icône perso reste optionnelle (hors périmètre immédiat).
- Composant `TileIcon(tile)` réutilisable (grille d'accueil + écran de gestion).

## Workstream B — Accueil « bannière + rangée » responsive

Disposition validée en maquette :
- **Bannière d'ambiance** en haut : grande horloge + date + salutation à gauche ; météo (temp + description) et prochaine alarme à droite ; engrenage Réglages en coin.
- **Zone tuiles** sous un libellé « Mes apps » : tuiles à icône + nom, grandes, avec une tuile « + Ajouter ».
- **Responsive** via `WindowSizeClass` + `BoxWithConstraints` :
  - **Paysage** (gen 2 3:2 et gen 1 16:9) : bannière large en ligne ; tuiles en **grille dense adaptative** (largeur mini de tuile → nb de colonnes ; défile si nécessaire).
  - **Portrait** (gen 1 pivoté) : bannière **empilée et centrée** (horloge très grande) ; tuiles en **grille 2 colonnes**.
- `MainActivity` **ne verrouille plus** l'orientation (retrait de toute contrainte ; l'app suit la rotation de l'appareil). Le contenu déjà posé sur `Surface` sombre (fond peint).
- Cibles ≥ ~88 dp, espacements 16–24 dp, typo clock ~56–72 sp selon orientation.

## Workstream C — Réveil robuste (fiabilité de la sonnerie)

Remplace le lancement direct d'Activity par le pattern robuste des apps d'horloge :
- **`AlarmForegroundService`** : démarré par `AlarmReceiver` au déclenchement. Joue la sonnerie en boucle (stream ALARM) avec **montée progressive du volume**, tient un `WakeLock` partiel. C'est lui qui garantit le son, écran allumé ou éteint. S'arrête sur action Arrêter/Snooze.
- **Notification full-screen-intent** : canal dédié `alarm` (importance haute, `CATEGORY_ALARM`), notification `ongoing` avec `setFullScreenIntent(pendingIntent → AlarmRingActivity, true)`. Le système ouvre l'écran plein écran (veille) ou une heads-up (écran utilisé).
- **`AlarmRingActivity`** : UI plein écran (par-dessus la veille) ; boutons **Arrêter** / **Snooze** envoient une commande au service (via `Intent`/action) plutôt que de gérer le son directement.
- **Permissions** : `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (34+), `POST_NOTIFICATIONS` (33+, demande runtime), `USE_FULL_SCREEN_INTENT` (déjà déclaré ; vérif `canUseFullScreenIntent()` sur 34+). Auto-accordées sur Portal (Android ~9/10), mais code défensif.
- La logique testée `nextTriggerTime` et la reprogrammation (repeat/one-shot, boot) restent inchangées.

## Workstream D — Sonnerie perso + snooze réglable + montée de volume

- **Migration Room v3** : ajout à `AlarmEntity` de `ringtoneUri: String?` (null = sonnerie d'alarme par défaut) et `snoozeMinutes: Int = 10`.
- **Écran d'édition d'alarme** enrichi : sélection de la sonnerie via `RingtoneManager.ACTION_RINGTONE_PICKER` (aperçu du nom), choix de la durée de snooze (5 / 10 / 15 min).
- **Montée de volume** : gérée par `AlarmForegroundService` (montée du volume STREAM_ALARM sur ~30 s jusqu'au niveau système).
- Le snooze utilise `snoozeMinutes` de l'alarme (au lieu du 10 min codé en dur).

## Architecture & fichiers (incréments)

- Nouveau : `ui/home/TileIcon.kt`, `ui/home/HomeLayout.kt` (aiguillage responsive), `alarm/AlarmForegroundService.kt`, `alarm/AlarmNotifications.kt` (canal + notification).
- Modifiés : `ui/home/HomeScreen.kt`, `ui/home/AmbientBanner.kt`, `ui/home/TileGrid.kt` (icônes + responsive), `MainActivity.kt` (orientation), `data/alarm/AlarmEntity.kt` (+DAO/DB v3), `alarm/AlarmReceiver.kt` (démarre le service + notif), `alarm/AlarmRingActivity.kt` (pilote le service), `alarm/AlarmScheduler.kt` (snooze paramétré), `ui/alarms/AlarmsScreen.kt` + `AlarmsViewModel.kt` (sonnerie + snooze), manifest (service + permissions).

## Découpage (pour le plan)

- **Phase 1 — Accueil (A + B)** : `TileIcon` (app icon + favicon Coil + fallback), grille à icônes, layout responsive paysage/portrait, orientation déverrouillée.
- **Phase 2 — Réveil robuste (C)** : DB v3 (champs alarme), `AlarmNotifications`, `AlarmForegroundService` (son + volume ramp + wakelock), rebranchement `AlarmReceiver`/`AlarmRingActivity`, permissions/manifest.
- **Phase 3 — Personnalisation (D)** : sélecteur de sonnerie + snooze réglable dans l'édition d'alarme, câblage `snoozeMinutes`/`ringtoneUri` dans le service et le scheduler.

## Tests

- **Logique** (JVM) : couleur/monogramme dérivé du libellé (fonction pure) ; sélection du layout selon `(largeur, orientation)` (fonction pure) ; calcul de la montée de volume (pas/temps) en fonction pure.
- **Compilation** : `assembleDebug` à chaque tâche.
- **On-device / émulateur** : rendu responsive (rotation), icônes réelles, et surtout **l'alarme sonne** via le service (écran éteint et allumé) — vérifié sur l'émulateur `MyPortal_Emu` puis sur le Portal.

## Risques

- **Favicons hors-ligne** : Jellyfin en réseau local peut ne pas exposer de favicon joignable → le fallback monogramme doit être impeccable.
- **Foreground service sous Android récent** : type `mediaPlayback` requis (34+) ; sur Portal (ancien Android) non requis mais le code doit gérer les deux.
- **Rotation** : recréation d'activité — l'état (navigation, WebView) doit survivre ; `WebAppActivity` gère déjà `configChanges` + save/restore.
