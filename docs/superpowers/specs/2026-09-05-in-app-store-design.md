# Fukkatsu — Store intégré (API Play, sans GMS)

- **Date** : 2026-09-05
- **Statut** : Design validé, prêt pour planification
- **Base** : Fukkatsu (ex-MyPortal) sur `main`

## Intention

Offrir, **dans l'écran d'ajout de tuile**, un **store applicatif** aux capacités proches du Play Store (rechercher, voir, installer les **vraies apps signées par leurs éditeurs**), **sans Google Play Services**, sur le Meta Portal (Android 10). Moteur : la bibliothèque open-source **gplayapi** (celle d'Aurora Store) + **authentification anonyme** via le dispenser Aurora. Installation via **`PackageInstaller`** (gère les *split APK*). Le tout **désactivé par défaut**, activable dans les Réglages derrière un **popup de risques**.

## Cadre & honnêteté

- L'**API Play est non officielle** : elle peut changer/être bloquée sans préavis. C'est hors des conditions de Google. Fonction opt-in, réservée à un usage personnel sur son propre appareil, avec avertissement explicite.
- L'**auth anonyme dépend de l'infra Aurora** (serveur de distribution de comptes jetables) : si ce service tombe, l'auth échoue → le store affiche une erreur claire.
- Périmètre **MVP** : recherche par nom → résultats → fiche minimale → installer. Pas de tendances/catégories/avis/mises à jour automatiques au début.

## Approche « spike d'abord »

**Phase 1 = spike go/no-go**, isolé et vérifié tôt : ajouter `gplayapi`, obtenir l'`AuthData` anonyme, exécuter **une recherche renvoyant des résultats** (émulateur/Portal, réseau requis). Décision :
- ✅ succès → poursuivre (installation + UI).
- ❌ la lib ne compile pas / l'auth ou la recherche est cassée → **pivot** : abandonner le store intégré et livrer à la place l'**installation d'Aurora Store** (APK fourni par l'utilisateur). On n'aura pas investi le gros de l'effort.

Les endpoints exacts du dispenser et la version/surface d'API de gplayapi seront **épinglés pendant le spike** (ils évoluent) ; la spec fixe l'architecture, pas les constantes réseau.

## Architecture (paquet `store/`)

- **`PlayStoreClient`** — encapsule gplayapi ; expose des types Fukkatsu (pas les modèles gplayapi hors du paquet) :
  - `suspend fun ensureAuth(): Boolean` — récupère/renouvelle l'`AuthData` anonyme (dispenser Aurora) ; met en cache l'`AuthData`.
  - `suspend fun search(query: String): List<StoreApp>` — `StoreApp(packageName, title, developer, iconUrl, versionCode)`.
  - `suspend fun files(packageName: String, versionCode: Int): List<ApkFile>` — `ApkFile(name, url, size)` (base + splits), via `PurchaseHelper`.
  - Toute erreur → exception encapsulée `StoreException` (auth, réseau, indisponible) pour un message UI clair.
- **`ApkDownloader`** — télécharge les `ApkFile` (OkHttp) dans le cache, avec progression (`Flow<Int>` %).
- **`ApkInstaller`** — `PackageInstaller` : crée une session `MODE_FULL_INSTALL`, écrit **base + tous les splits**, `commit()` avec un `IntentSender` (PendingIntent → petit `BroadcastReceiver` de statut). Déclenche le **prompt système** d'installation. Gère l'autorisation « installer des applis inconnues » : si `packageManager.canRequestPackageInstalls()` est faux, rediriger vers `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` (explication d'abord).
- **`StoreViewModel`** — orchestration : `search(query)`, `install(app)` ; expose `StateFlow` d'état (`Idle/Loading/Results(list)/Error(msg)`) et un état d'installation par package (`downloading %/installing/installed/error`).
- **`SettingsRepository`** (existant, DataStore) — ajouter `storeEnabled: Flow<Boolean>` + `setStoreEnabled`.

## UI

- **Réglages** : nouvelle ligne « Store d'applications » avec un interrupteur. À l'activation → **`AlertDialog`/feuille de risques** (texte : API Play non officielle, installe des binaires tiers, peut cesser de fonctionner, hors conditions Google ; « J'ai compris, activer »). Refus = reste désactivé.
- **Ajout de tuile** (`TileEditScreen`) : quand `storeEnabled`, le `SegmentedChoice` gagne une 3ᵉ option **« Store »** (à côté d'Application / Web). Section Store :
  - Grand champ de **recherche** (tactile) → lance `search`.
  - **Résultats** en cartes Sumi (icône via Coil, titre, éditeur) + bouton **Installer**.
  - À l'installation : progression (%) puis prompt système ; en cas de succès l'app est installée → l'utilisateur peut l'ajouter en tuile depuis l'onglet Application (ou on l'ajoute automatiquement — hors MVP).
- Style Sumi cohérent (cartes `SumiSurface`, accent `Shu`, pas de dropdown).

## Permissions & manifest
- `android.permission.REQUEST_INSTALL_PACKAGES` (installer des APK).
- `INTERNET` (déjà présent).
- Petit `BroadcastReceiver` interne pour le retour de statut de `PackageInstaller` (non exporté).

## Dépendances
- `gplayapi` via **JitPack** (`maven { url "https://jitpack.io" }` dans `settings.gradle.kts` `dependencyResolutionManagement`), coordonnée + version épinglées au spike. OkHttp/Coil déjà présents.

## Tests
- **JVM (pur)** : parsing de la réponse du dispenser (email/token) en fonction pure ; mapping modèle gplayapi → `StoreApp` (si isolable) ; tri/normalisation de la requête.
- **Compilation** : `assembleDebug` à chaque tâche ; le spike valide en plus la résolution JitPack.
- **Émulateur/Portal (réseau)** : auth anonyme OK, recherche renvoie des résultats, installation d'une petite app gratuite bout-en-bout (prompt système → app installée → lançable).

## Risques
- **gplayapi non officiel** : surface d'API et endpoints changent ; épingler une version connue et isoler derrière `PlayStoreClient` pour limiter l'impact.
- **Dispenser Aurora** : dépendance externe ; échec géré par message clair + pivot Aurora Store possible.
- **Split APK / PackageInstaller** : sessions multi-fichiers délicates ; tester tôt avec une app à splits.
- **Autorisation « sources inconnues »** : à demander/rediriger proprement (API 26+).
- **Toolchain preview** (AGP 9.4 / Kotlin 2.2) vs gplayapi (protobuf, deps transitives) : risque de conflit résolu au spike (bump/exclusions si besoin).
- **Compat sans GMS** : les apps installées qui *exigent* GMS ne fonctionneront pas pleinement — comportement attendu, hors de notre contrôle.
