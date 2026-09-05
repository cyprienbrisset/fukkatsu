#!/usr/bin/env bash
#
# provision-portal.sh — installe et configure Fukkatsu / FukkaStore sur un Meta Portal.
#
# Ce que fait le script :
#   1. Construit l'APK debug si besoin (JBR d'Android Studio, aucun java requis sur le PATH).
#   2. Installe l'APK sur le Portal (adb).
#   3. Accorde les permissions qui ne peuvent l'être que via adb :
#        - accès « Ne pas déranger » (politique de notifications)
#        - administrateur d'appareil (extinction de l'écran)
#   4. Propose de désactiver le vérificateur de paquets (Play Protect).
#      >>> C'est un CHOIX laissé à l'utilisateur. <<<
#      Le Portal n'a pas de GMS fonctionnel : si le vérificateur reste ACTIF, il ne
#      peut jamais répondre et les installations depuis FukkaStore échouent
#      (« install verification failure »). Le désactiver résout le problème ;
#      c'est une atténuation de sécurité désactivée sur cet appareil kiosque perso.
#
# Usage :
#   scripts/provision-portal.sh [SERIAL]
#     SERIAL                 série adb du Portal (auto-détecté si un seul appareil)
#   Options :
#     --build                force la reconstruction de l'APK avant install
#     --disable-verifier     désactive le vérificateur sans poser la question
#     --keep-verifier        laisse le vérificateur actif sans poser la question
#     -h | --help            aide
#
# Variables d'env (surchargeables) : ADB, APK, JAVA_HOME
#
set -euo pipefail

# --- Résolution des chemins ---------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd -P)"

ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
APK="${APK:-$REPO_ROOT/app/build/outputs/apk/debug/app-debug.apk}"
JAVA_HOME="${JAVA_HOME:-/Users/cyprienbrisset/Applications/Android Studio.app/Contents/jbr/Contents/Home}"

PKG="com.cyprienbrisset.myportal"
ADMIN="$PKG/.system.MyDeviceAdminReceiver"

# --- Sortie lisible -----------------------------------------------------------
if [ -t 1 ]; then BOLD=$'\033[1m'; RED=$'\033[31m'; GRN=$'\033[32m'; YLW=$'\033[33m'; RST=$'\033[0m'
else BOLD=""; RED=""; GRN=""; YLW=""; RST=""; fi
info() { printf '%s➜%s %s\n' "$BOLD" "$RST" "$*"; }
ok()   { printf '%s✓%s %s\n' "$GRN" "$RST" "$*"; }
warn() { printf '%s!%s %s\n' "$YLW" "$RST" "$*"; }
die()  { printf '%s✗ %s%s\n' "$RED" "$*" "$RST" >&2; exit 1; }

# --- Arguments ----------------------------------------------------------------
SERIAL=""
FORCE_BUILD=0
VERIFIER_CHOICE=""   # "disable" | "keep" | "" (demander)
LAUNCHER_CHOICE=""   # "set" | "skip" | "" (demander)
while [ $# -gt 0 ]; do
  case "$1" in
    --build) FORCE_BUILD=1 ;;
    --disable-verifier) VERIFIER_CHOICE="disable" ;;
    --keep-verifier) VERIFIER_CHOICE="keep" ;;
    --set-launcher) LAUNCHER_CHOICE="set" ;;
    --no-launcher) LAUNCHER_CHOICE="skip" ;;
    -h|--help) sed -n '2,32p' "$0"; exit 0 ;;
    -*) die "Option inconnue : $1" ;;
    *) SERIAL="$1" ;;
  esac
  shift
done

[ -x "$ADB" ] || command -v "$ADB" >/dev/null 2>&1 || die "adb introuvable ($ADB). Définis ADB=..."

# --- Sélection de l'appareil --------------------------------------------------
if [ -z "$SERIAL" ]; then
  mapfile -t DEVICES < <("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}')
  case "${#DEVICES[@]}" in
    0) die "Aucun appareil adb connecté. Branche le Portal (débogage USB activé)." ;;
    1) SERIAL="${DEVICES[0]}" ;;
    *) die "Plusieurs appareils : ${DEVICES[*]}. Précise la série en argument." ;;
  esac
fi
A=("$ADB" -s "$SERIAL")
info "Appareil ciblé : ${BOLD}$SERIAL${RST}"
"${A[@]}" get-state >/dev/null 2>&1 || die "Appareil $SERIAL injoignable."

# --- 1. Build (si besoin) -----------------------------------------------------
if [ "$FORCE_BUILD" -eq 1 ] || [ ! -f "$APK" ]; then
  info "Construction de l'APK debug…"
  ( cd "$REPO_ROOT" && JAVA_HOME="$JAVA_HOME" ./gradlew :app:assembleDebug -q ) \
    || die "Échec du build Gradle."
  ok "APK construit."
fi
[ -f "$APK" ] || die "APK introuvable : $APK (utilise --build)."

# --- 2. Install ---------------------------------------------------------------
info "Installation de l'APK sur le Portal…"
"${A[@]}" install -r -d "$APK" >/dev/null || die "Échec de l'installation de l'APK."
ok "Fukkatsu installé."

# --- 3. Permissions adb -------------------------------------------------------
info "Attribution de l'accès « Ne pas déranger »…"
if "${A[@]}" shell cmd notification allow_dnd "$PKG" >/dev/null 2>&1; then
  ok "Accès DND accordé."
else
  warn "Impossible d'accorder le DND automatiquement (à activer dans les réglages si besoin)."
fi

info "Attribution du rôle administrateur d'appareil (extinction écran)…"
if "${A[@]}" shell dpm set-active-admin "$ADMIN" >/dev/null 2>&1; then
  ok "Administrateur d'appareil actif."
else
  warn "Impossible d'activer l'admin d'appareil automatiquement."
fi

info "Attribution de WRITE_SECURE_SETTINGS (bouton vérificateur dans Réglages)…"
if "${A[@]}" shell pm grant "$PKG" android.permission.WRITE_SECURE_SETTINGS >/dev/null 2>&1; then
  ok "WRITE_SECURE_SETTINGS accordé."
else
  warn "Impossible d'accorder WRITE_SECURE_SETTINGS."
fi

# --- Launcher principal (CHOIX utilisateur) -----------------------------------
echo
printf '%s————— Launcher principal —————%s\n' "$BOLD" "$RST"
echo "Fukkatsu peut devenir l'écran d'accueil (launcher) du Portal : il s'ouvre"
echo "au démarrage et au bouton accueil, à la place du launcher d'origine."
echo
if [ -z "$LAUNCHER_CHOICE" ]; then
  if [ -t 0 ]; then
    read -r -p "Définir Fukkatsu comme launcher principal ? [O/n] " ans
    case "$ans" in
      ""|[Oo]|[Oo][Uu][Ii]|[Yy]|[Yy][Ee][Ss]) LAUNCHER_CHOICE="set" ;;
      *) LAUNCHER_CHOICE="skip" ;;
    esac
  else
    warn "Non interactif : launcher inchangé (utilise --set-launcher)."
    LAUNCHER_CHOICE="skip"
  fi
fi

if [ "$LAUNCHER_CHOICE" = "set" ]; then
  if "${A[@]}" shell cmd package set-home-activity "$PKG/.MainActivity" >/dev/null 2>&1; then
    ok "Fukkatsu défini comme launcher principal."
  else
    warn "Échec via set-home-activity. Ouvre les Réglages Android → Applis par défaut →"
    echo "  Application d'accueil, puis choisis Fukkatsu."
  fi
else
  info "Launcher d'origine conservé."
  echo "  Pour le définir plus tard : scripts/provision-portal.sh $SERIAL --set-launcher"
fi

# --- 4. Vérificateur de paquets (CHOIX utilisateur) ---------------------------
CUR_VERIFIER="$("${A[@]}" shell settings get global package_verifier_enable 2>/dev/null | tr -d '\r')"
echo
printf '%s————— Vérificateur de paquets (Play Protect) —————%s\n' "$BOLD" "$RST"
echo "État actuel : package_verifier_enable = ${CUR_VERIFIER:-inconnu}"
echo
printf '%sImportant :%s ce Portal n'\''a pas de GMS fonctionnel. Si le vérificateur reste\n' "$YLW" "$RST"
echo "ACTIF, il ne peut jamais valider une installation → les installs depuis"
printf '%sFukkaStore échoueront%s (« install verification failure »).\n' "$RED" "$RST"
echo "Le désactiver corrige le problème (réglage persistant, réversible)."
echo

if [ -z "$VERIFIER_CHOICE" ]; then
  if [ -t 0 ]; then
    read -r -p "Désactiver le vérificateur pour que FukkaStore fonctionne ? [O/n] " ans
    case "$ans" in
      ""|[Oo]|[Oo][Uu][Ii]|[Yy]|[Yy][Ee][Ss]) VERIFIER_CHOICE="disable" ;;
      *) VERIFIER_CHOICE="keep" ;;
    esac
  else
    warn "Non interactif : vérificateur laissé tel quel (utilise --disable-verifier)."
    VERIFIER_CHOICE="keep"
  fi
fi

if [ "$VERIFIER_CHOICE" = "disable" ]; then
  "${A[@]}" shell settings put global package_verifier_enable 0
  "${A[@]}" shell settings put global verifier_verify_adb_installs 0
  "${A[@]}" shell settings put global package_verifier_user_consent -1
  ok "Vérificateur désactivé — les installs FukkaStore fonctionneront."
else
  warn "Vérificateur laissé ACTIF — les installs depuis FukkaStore risquent d'échouer."
  echo "  Pour le désactiver plus tard : scripts/provision-portal.sh $SERIAL --disable-verifier"
fi

echo
ok "Provisioning terminé."
