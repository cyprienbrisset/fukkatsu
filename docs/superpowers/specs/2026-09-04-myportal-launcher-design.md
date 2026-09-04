# MyPortal — Launcher pour Meta Portal+ (2ᵉ génération)

- **Date** : 2026-09-04
- **Statut** : Design validé, prêt pour planification
- **Cible** : Meta Portal+ 2ᵉ génération (écran paysage tactile, Android, mode développeur + sideload activés)
- **Module** : `app` unique — package `com.cyprienbrisset.myportal`, minSdk 28, targetSdk 37

## 1. Objectif

Remplacer l'écran d'accueil du Meta Portal+ par un launcher personnel, moderne et lisible à distance, qui permet de lancer indistinctement :

- des applications **sideloadées / installées** (Netflix, Jellyfin, audiobooks…), lancées par leur package ;
- des **web apps en plein écran** (WebView type kiosque, session persistante).

L'appareil ne possède **pas d'application horloge** : MyPortal embarque donc sa propre horloge et son propre réveil, qui doit sonner réellement à l'heure programmée.

## 2. Périmètre

### Inclus
1. **Launcher HOME** — MyPortal devient l'écran d'accueil par défaut.
2. **Bannière d'ambiance** — grande horloge, date, salutation, météo (Open-Meteo, ville fixée), indication de la prochaine alarme.
3. **Grille de tuiles** tactile simple (défile si nécessaire), deux types de tuiles :
   - `APP` : lance une app installée par son package.
   - `WEB` : ouvre une URL en WebView plein écran immersif, cookies/localStorage persistants.
4. **Écran de réglages** — gérer les tuiles (ajouter/retirer/réordonner), régler la ville météo, gérer les alarmes.
5. **Horloge / Réveil intégré** — alarmes multiples, répétition par jour de semaine, sonnerie réelle plein écran, snooze / arrêt.

### Exclu (hors périmètre pour cette version)
- Lancement d'une app/playlist au réveil (l'alarme **sonne uniquement**).
- Catégories / onglets de tuiles (grille unique).
- Reprise de contenu (« continuer où j'en étais ») — inaccessible depuis l'extérieur des apps.
- Géolocalisation automatique (pas de GPS fiable) — ville saisie manuellement.
- Bannière hero interactive de type carrousel.

## 3. Décisions techniques

| Sujet | Décision |
|---|---|
| Toolkit UI | **Jetpack Compose** + Material 3 (remplace le scaffold AppCompat/XML) |
| Architecture | MVVM — écrans Compose + ViewModels + couche données |
| Persistance | **Room** (tuiles, alarmes) + **DataStore** (préférences : ville, format d'heure) |
| Rôle de l'app | **Launcher HOME** (`intent-filter` CATEGORY_HOME/DEFAULT) |
| Web apps | `WebAppActivity` avec WebView plein écran immersif, `domStorageEnabled`, cookies persistants |
| Lancement d'app | `PackageManager.getLaunchIntentForPackage(package)` |
| Météo | Open-Meteo (gratuit, sans clé) via OkHttp + kotlinx-serialization, cache local |
| Réveil | `AlarmManager.setAlarmClock()` (exact, exempté Doze, sans permission spéciale) |

## 4. Architecture des composants

### Activités
- **`MainActivity`** — héberge la navigation Compose (Navigation-Compose). Déclarée comme HOME. Écrans : `HomeScreen`, `SettingsScreen`, `TileEditScreen`, `AlarmsScreen`.
- **`WebAppActivity`** — hôte WebView plein écran immersif pour les tuiles `WEB`. Reçoit l'URL en extra. Persistance de session (CookieManager, DOM storage). Gestion retour / fermeture.
- **`AlarmRingActivity`** — écran d'alarme plein écran affiché par-dessus la veille (`USE_FULL_SCREEN_INTENT`, `setShowWhenLocked`/`setTurnScreenOn`). Sonnerie en boucle avec montée de volume, boutons **Snooze** et **Arrêter**.

### Écrans Compose
- **`HomeScreen`** — bannière d'ambiance en haut (`AmbientBanner`) + grille de tuiles (`TileGrid`) en dessous. Appui sur une tuile → lancement app ou `WebAppActivity`.
- **`SettingsScreen`** — accès à la gestion des tuiles, à la ville météo, au format d'heure, à la liste des alarmes.
- **`TileEditScreen`** — ajouter une tuile : choisir « app installée » (sélecteur listant les apps via `<queries>`) ou « web » (URL + libellé + icône) ; réordonner ; supprimer.
- **`AlarmsScreen`** — liste des alarmes, création/édition (heure, jours de répétition, label, activation).

### Couche données & services
- **`TileRepository`** (Room) — CRUD tuiles, ordre.
- **`AlarmRepository`** (Room) — CRUD alarmes.
- **`SettingsRepository`** (DataStore) — ville météo, format 12/24 h.
- **`WeatherRepository`** — appel Open-Meteo, parsing, cache, rafraîchissement périodique.
- **`AlarmScheduler`** — programme/annule les alarmes via `AlarmManager.setAlarmClock()` ; calcule la prochaine occurrence pour les alarmes répétées.
- **`AlarmReceiver`** (`BroadcastReceiver`) — reçoit le déclenchement, lance `AlarmRingActivity`, reprogramme l'occurrence suivante.
- **`BootReceiver`** (`RECEIVE_BOOT_COMPLETED`) — reprogramme toutes les alarmes actives après un redémarrage.

## 5. Modèle de données

```
TileEntity     : id, type (APP|WEB), label, packageName?, url?, iconRef, position
AlarmEntity    : id, hour, minute, repeatDays (bitmask lun..dim), label, enabled, ringtoneUri?
Settings (DataStore) : weatherCity, weatherLatLon (résolu), use24hFormat
```

## 6. Flux principaux

- **Lancer une app** : appui tuile `APP` → `getLaunchIntentForPackage` → `startActivity`. Si package absent, message d'erreur.
- **Ouvrir une web app** : appui tuile `WEB` → `WebAppActivity` avec l'URL → WebView immersif, session conservée entre ouvertures.
- **Programmer une alarme** : création dans `AlarmsScreen` → `AlarmScheduler.schedule()` → `setAlarmClock()` sur la prochaine occurrence.
- **Sonnerie** : `setAlarmClock` déclenche `AlarmReceiver` → `AlarmRingActivity` plein écran + sonnerie → **Arrêter** (fin ; si répétée, reprogramme le lendemain concerné) ou **Snooze** (reprogramme +N min).
- **Météo** : au démarrage et périodiquement, `WeatherRepository` interroge Open-Meteo pour la ville configurée ; la bannière affiche la donnée en cache si hors ligne.

## 7. Permissions & manifest

- `INTERNET` — météo + WebView.
- `USE_FULL_SCREEN_INTENT` — écran d'alarme par-dessus la veille.
- `RECEIVE_BOOT_COMPLETED` — reprogrammation des alarmes après reboot.
- `POST_NOTIFICATIONS` (API 33+) — notification d'alarme de secours.
- Bloc `<queries>` — lister les apps installées pour l'ajout de tuiles.
- `MainActivity` : `intent-filter` avec `CATEGORY_HOME` + `CATEGORY_DEFAULT`.

## 8. Dépendances à ajouter

Compose BOM, Material 3, `activity-compose`, `lifecycle-viewmodel-compose`, `navigation-compose`, Room (`runtime`, `ktx`, compilateur via **KSP**), OkHttp, kotlinx-serialization, DataStore Preferences, Coil (icônes/favicons). Retrait des dépendances AppCompat/Material XML du scaffold non nécessaires.

## 9. Découpage & unités de test

- **Grille & lancement** : `TileRepository` (tests CRUD/ordre), logique de résolution du `launchIntent`.
- **Réveil** : `AlarmScheduler.nextTriggerTime()` (calcul de la prochaine occurrence selon jours de répétition) — testable unitairement, cœur de fiabilité.
- **Météo** : parsing de la réponse Open-Meteo, comportement de cache/hors-ligne.
- **WebView** : configuration de session (test d'intégration léger).

## 10. Risques & points de vigilance

- **Définir MyPortal par défaut** : sur appareil verrouillé, le sélecteur de launcher par défaut peut être absent — prévoir un fallback (relancer via le sélecteur système, doc de mise en place ADB).
- **Fiabilité de l'alarme** : `setAlarmClock()` est le mécanisme le plus robuste contre le Doze ; à valider sur l'appareil réel.
- **WebView & DRM** : certains services (ex. Netflix web) refusent la lecture en WebView (Widevine) — Netflix sera privilégié en tuile `APP`. Les web apps ciblent surtout Jellyfin et services sans DRM strict.
- **targetSdk 37** (préversion) : vérifier la disponibilité des API au build.
```
