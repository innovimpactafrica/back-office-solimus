#!/usr/bin/env bash
# Lancement local de l'application Spring Boot sur la MÊME base que la prod.
# Usage : ./run-local.sh
set -euo pipefail
cd "$(dirname "$0")"

# 1. Charger le .env sans interpréter le shell (les valeurs sont prises telles quelles :
#    pas d'expansion de $VAR, pas de suppression des guillemets imbriqués dans le JSON Firebase)
while IFS= read -r line || [ -n "$line" ]; do
  line="${line%$'\r'}"
  case "$line" in
    ''|'#'*) continue ;;
  esac
  case "$line" in
    *=*) ;;
    *) continue ;;
  esac
  key="${line%%=*}"
  case "$key" in
    *[!A-Za-z0-9_]*) continue ;;
  esac
  val="${line#*=}"
  case "$val" in
    \'*\') val="${val#\'}"; val="${val%\'}" ;;
    \"*\") val="${val#\"}"; val="${val%\"}" ;;
  esac
  export "$key=$val"
done < ./.env

# 2. Vérification rapide : on doit bien voir la base en ligne
if [ "${DB_NAME:-}" != "solimus_db" ]; then
  echo "ERREUR : DB_NAME='${DB_NAME:-}' (attendu solimus_db) — .env non chargé ?" >&2
  exit 1
fi
echo "Base cible : ${DB_HOST}:${DB_PORT}/${DB_NAME} (profil local)"

# 3. Lancer l'app (le log Hikari DEBUG affiche "jdbcUrl" au démarrage)
if command -v mvn >/dev/null 2>&1; then
  exec mvn clean spring-boot:run
else
  exec ./mvnw clean spring-boot:run
fi