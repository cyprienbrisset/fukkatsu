# MyPortal v3 — 墨 Sumi design system (refonte UX/UI)

- **Date** : 2026-09-04
- **Statut** : Design validé (maquettes), prêt pour planification
- **Base** : MyPortal v2 (sur `main`)

## Intention

Refonte complète de l'UX/UI dans un langage **japonais « Sumi »** (encre & vermillon) : pas seulement les couleurs, mais la **composition, l'iconographie, les états, l'ergonomie tactile et la typographie**. Cible : Portal+ 1ʳᵉ/2ᵉ gén (grand écran tactile, vu de loin). La logique métier v2 (launcher, WebView, météo, réveil/service) ne change pas — c'est la **couche présentation** qui est repensée.

## Principes

- **Ma (間)** — vide maîtrisé, respiration, asymétrie assumée.
- **Un seul accent** — vermillon *shu* 朱, discipliné (focus, primaire, filets, chevrons).
- **Tactile d'abord** — cibles ≥ 56 dp, manipulation directe, lisibilité à distance.
- **Zéro liste déroulante / combo / spinner** — tout choix = boutons segmentés, pastilles, grilles à taper, steppers.
- **Motifs** — sceau *hanko*, filets vermillon, kanji en filigrane discret, anneau *ensō* pour le primaire/focus.

## Jetons de design

### Couleurs (`ui/theme/Color.kt`)
| Rôle | Nom | Hex |
|---|---|---|
| Fond | Sumi 墨 | `#0D0E12` |
| Fond 2 | Ink2 | `#15171C` |
| Surface (médaillon, carte) | Surface | `#191C23` |
| Filet / bordure | Line | `#242832` |
| Texte principal | Kinari 生成 | `#ECE7DD` |
| Texte secondaire | Muted | `#9A9488` |
| Accent | Shu 朱 | `#C1272D` |
| Accent (texte sur shu) | OnShu | `#F6EEE0` |

`MyPortalTheme` passe en `darkColorScheme` mappé sur ces jetons (primary=Shu, background=Sumi, surface=Surface, onBackground/onSurface=Kinari, outline=Line). Fond peint via `Surface` (déjà en place).

### Typographie (`ui/theme/Type.kt`, `res/font/`)
- **Mincho (serif)** — horloge, titres, boutons primaires. Police **Shippori Mincho** (JP+Latin), bundlée en `res/font/shippori_mincho_regular.ttf` + `_medium`.
- **Gothic (sans)** — labels, listes, corps. Police **Zen Kaku Gothic New** (JP+Latin), bundlée en `res/font/zen_kaku_gothic_new_regular.ttf` + `_medium`.
- Fallback système Noto CJK pour tout glyphe manquant.
- Échelle : horloge 88–96 sp (paysage) / 72 sp (portrait), titres 20–24 sp, labels 11 sp interlettrage `.26em` majuscules, corps 15–17 sp.

### Formes & espacements
- Rayons : médaillon = cercle ; cartes = 14–18 dp ; sceau hanko = 8–10 dp.
- Grille d'espacement : 8/12/16/24/32/44 dp. Marges d'écran généreuses (≥ 24 dp).

## Composants (nouveaux, réutilisables — `ui/sumi/`)

- **`Medallion`** — disque encre (Surface) + filet fin, icône au centre (icône d'app en couleur adoucie ou glyphe), **anneau vermillon** au focus/press. Taille paramétrable (64–78 dp). Libellé Gothic dessous. Remplace les cartes rectangulaires.
- **`HankoSeal`** — carré vermillon arrondi avec un caractère (朱/＋/鈴…), tappable. Sert d'accès Réglages et d'en-tête.
- **`VermilionRule`** — filet dégradé (horizontal ou vertical) vermillon→transparent.
- **`SectionLabel`** — label bilingue « カナ / TEXTE » 11 sp interlettrage large, terminé par une hairline.
- **`WatermarkKanji`** — grand kanji en filigrane (opacité ~5 %) débordant, décoratif.
- **`SumiPrimaryButton`** — pilule/ensō vermillon, texte Mincho (ex. « 保存 Enregistrer », « 止 Arrêter »).
- **`SegmentedChoice`** — 2+ gros boutons côte à côte, sélection pleine vermillon (remplace tout toggle/dropdown de type).
- **`ChoiceChip` (Sumi)** — pastille tactile (jours, snooze, tones) état actif vermillon.
- **`Stepper`** — colonne ▲ / valeur Mincho / ▼ pour l'heure (remplace le time picker à molette/dropdown).

## Écrans repensés

### Accueil (`HomeScreen`, `AmbientBanner`, `TileGrid`→`MedallionGrid`)
- **Paysage** : composition asymétrique — colonne *héro* (≈38 %) à gauche : marque interlettrée, horloge Mincho, date FR + jour en kanji vermillon, ligne météo + prochaine alarme ; **filet vermillon vertical** ; zone apps à droite avec `SectionLabel` « アプリ / MES APPS » puis **grille de médaillons** (+ médaillon « ＋ Ajouter » en pointillés). `WatermarkKanji` 墨.
- **Portrait** : héro centré empilé (horloge 72 sp), puis grille de médaillons 2–3 colonnes.
- **Réglages** : `HankoSeal` 朱 en haut à droite (fin du bouton mal placé).
- Focus/press médaillon → anneau *ensō* vermillon (navigation à distance/tactile).

### Réglages (`SettingsScreen`)
- En-tête `HankoSeal` + « 設定 · Réglages » (Mincho).
- Lignes **grandes et tappables** (≥ 64 dp) : libellé kanji + FR à gauche, valeur/`›` vermillon à droite. Aucune liste déroulante.

### Ajouter/gérer une tuile (`TileEditScreen`)
- `SegmentedChoice` **Application | Web** (gros boutons).
- **App** : grille de médaillons des apps installées — **taper pour ajouter** (pas de liste).
- **Web** : grand champ Nom + grand champ URL (clavier tactile), bouton `SumiPrimaryButton`.
- Gestion : médaillons existants avec actions déplacer/supprimer en **grandes icônes tactiles** (pas de menu).

### Ville météo (`WeatherSettingsScreen`)
- Grand champ de recherche + **grosses cartes de villes** (résultats) à taper. Pas de dropdown.

### Alarmes (`AlarmsScreen`, éditeur)
- Liste : grandes lignes (heure Mincho, répétition, snooze) + interrupteur tactile + suppression.
- Éditeur (dans une feuille plein écran, pas un `AlertDialog` étriqué) :
  - **Heure** = `Stepper` ▲/▼ (heures/minutes), chiffres Mincho ~68 sp.
  - **Répéter** = 7 gros ronds jour (L M M J V S D), actif vermillon.
  - **Snooze** = 3 pastilles 5/10/15.
  - **Sonnerie** = **cartes tap-pour-écouter** listant les sonneries d'alarme système (`RingtoneManager`), bouton ▶ d'aperçu, sélection = filet vermillon. **Remplace le sélecteur système** (plus de radiolist système ni de dropdown).
  - `SumiPrimaryButton` « 保存 Enregistrer ».

### Écran de réveil (`AlarmRingActivity`)
- Fond Sumi, filigrane 鈴, kanji 目覚まし vermillon, grand horaire Mincho, **bouton ensō « 止 Arrêter »** + pastille « Snooze N ». Pilote le service (inchangé).

### WebView (`WebAppActivity`)
- Inchangé (plein écran immersif).

## Portée technique

- **Presque exclusivement couche UI** : `ui/theme/*`, nouveau paquet `ui/sumi/*` (composants), refonte des composables d'écran. La sonnerie/service/alarme/DAO/DB **ne changent pas**, sauf l'écran de sélection de sonnerie qui interroge `RingtoneManager` (aucune migration DB — `ringtoneUri` existe déjà).
- Ajout de 4 fichiers de police dans `res/font/` + téléchargement des TTF (Shippori Mincho, Zen Kaku Gothic New) depuis Google Fonts.
- Retrait de l'`AlertDialog` d'alarme au profit d'un écran/feuille plein écran (meilleure ergonomie tactile).

## Tests

- **JVM (pure)** : mapping jour→kanji (fonction pure) ; formatage de la date bilingue ; logique du `Stepper` (incrément/wrap heures 0-23, minutes 0-59) — testables sans Android.
- **Compilation** : `assembleDebug` à chaque tâche.
- **Visuel (émulateur + Portal)** : rendu Sumi paysage/portrait, médaillons + focus, Réglages, éditeur d'alarme tactile, écran de réveil.

## Risques

- **Taille des polices JP** (quelques Mo) — acceptable en sideload ; sous-ensemble possible plus tard.
- **Densité des kanji** — rester discret (filigranes ~5 %, labels bilingues fins) pour ne pas surcharger.
- **Écran de réglages/alarme en plein écran** — vérifier la navigation retour (bouton système Portal).
- **Cohérence** : tous les écrans doivent passer par les composants `ui/sumi/*` — pas de Material brut résiduel.
