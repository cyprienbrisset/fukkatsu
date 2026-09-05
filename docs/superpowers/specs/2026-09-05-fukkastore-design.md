# FukkaStore — store d'applications intégré à Fukkatsu

- **Date** : 2026-09-05
- **Statut** : Design validé, spike d'auth GO (login Google → aasToken → recherche fonctionne)
- **Base** : Fukkatsu sur `main`, spike sur `feat/fukkastore-spike`

## Intention

Un **store d'apps intégré** à Fukkatsu, avec les capacités du Play Store (rechercher, voir, installer les vraies apps signées éditeur), **sans Google Play Services**, en réutilisant le moteur d'**Aurora Store** (GPLv3) : le flux d'auth qui marche (login Google perso via EmbeddedSetup + échange **AC2DM** avec les params GMS → aasToken → gplayapi) + notre **UI Sumi**. Nom : **FukkaStore**.

## Acquis (spike GO)
- gplayapi **`com.gitlab.AuroraOSS:gplayapi:3.2.6`** (JitPack).
- **Auth login Google** : WebView `accounts.google.com/EmbeddedSetup` → capture cookie `oauth_token` + email (scrape JS) → **AC2DM** (`android.clients.google.com/auth` avec `callerPkg=com.google.android.gms`, `callerSig`, `google_play_services_version=19629032`, `sdk_version=28`, `service=ac2dm`, `add_account=1`, `get_accountid=1`, `ACCESS_TOKEN=1`, `droidguard_results=null`) → `response["Token"]` = **aasToken** → `AuthHelper.build(email, aasToken, properties, locale)` → `AuthData`.
- **Recherche** : `SearchHelper(authData).searchResults(query).appList` (App: `packageName`, `displayName`, `developerName`, `iconArtwork`, `versionCode`).
- Fichiers dans `store/` : `FukkaAuth.kt` (exchange/build/search), `FukkaLoginActivity.kt` (WebView), asset `fukka_device.properties`.

## Licence
Aurora est **GPLv3** ; réutiliser son flux/params rend la partie store **GPLv3**. Acceptable en usage perso. On garde les en-têtes/attribution GPL sur le code dérivé.

## Périmètre v1
- **Login** (une fois) : écran de login Google Sumi ; **AuthData persistée** (DataStore, chiffrée si simple) → pas de re-login à chaque ouverture. Bouton « Se déconnecter ».
- **Recherche + résultats** : champ de recherche → cartes Sumi (icône Coil, titre, éditeur) → **Installer**.
- **Installation** : `PurchaseHelper` → liste des fichiers (base + splits) → téléchargement (progression) → `PackageInstaller` (session multi-APK) → prompt système. Gère « sources inconnues » (redirection si besoin).
- **Entrée** : une section **« Store »** — depuis les Réglages (ligne « FukkaStore ») et/ou un 3ᵉ segment dans l'ajout de tuile. Après install, l'app est lançable et ajoutable en tuile.
- Hors v1 : catégories/tendances, avis/captures, mises à jour automatiques, comptes multiples, anonyme.

## Architecture (paquet `store/` + `ui/store/`)
- **`FukkaAuth`** (existe, du spike) — `exchangeAasToken`, `buildAuthDataFromAas`, `searchTitles` → étendre : `search(query): List<StoreApp>` (map complet), `files(pkg, versionCode): List<ApkFile>` (via `PurchaseHelper`+`AppDetailsHelper`).
- **`FukkaAccount`** — persiste `email` + `aasToken` (DataStore) ; reconstruit `AuthData` au besoin (via `buildAuthDataFromAas`, mise en cache mémoire) ; `isLoggedIn`, `logout`.
- **`FukkaLoginActivity`** (existe) — restylée Sumi ; au succès, persiste le compte via `FukkaAccount` et revient au store.
- **`ApkDownloader`** — OkHttp → cache, progression.
- **`ApkInstaller`** + **`InstallResultReceiver`** — `PackageInstaller` base+splits + prompt + « sources inconnues ».
- **`StoreViewModel`** — état (Idle/Loading/Results/Error), progression d'install par package, `search`, `install`, `isLoggedIn`.
- **`StoreScreen`/`StoreSection`** (Compose Sumi) — login gate → recherche → résultats → install.

## Permissions & manifest
- `REQUEST_INSTALL_PACKAGES`, `INTERNET`.
- `InstallResultReceiver` (non exporté), `FukkaLoginActivity` (non exporté).

## Tests
- **JVM (pur)** : parse réponse AC2DM (key=value) ; parse cookie string ; mapping App→StoreApp (isolé si possible).
- **Compilation** : `assembleDebug` par tâche.
- **On-device (Portal, réseau)** : login persiste ; recherche ; install d'une petite app gratuite bout-en-bout ; réouverture sans re-login.

## Risques
- **API Play non officielle** + params GMS figés (callerSig, play_services_version) : peuvent devoir être mis à jour si Google change. Isolés dans `FukkaAuth`.
- **Verrouillage de compte Google** (client non officiel) — l'utilisateur en est informé ; compte dédié recommandé.
- **Split APK / PackageInstaller** : sessions multi-fichiers — tester tôt.
- **Persistance du token** : l'aasToken est sensible ; le stocker en DataStore local (appareil perso). Reconstruire AuthData si expiré (re-login).
- **GPLv3** : garder l'attribution.
