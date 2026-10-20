#!/usr/bin/env bash
# Runs once, on the first start of an empty Postgres volume.
# Database-per-service: every service gets its own database AND its own login role,
# so no service can read another service's tables.
#
# The body runs in a subshell so the script behaves the same whether the Postgres
# entrypoint executes it or sources it (Windows checkouts may lose the +x bit).
(
  set -euo pipefail

  : "${SWIFTDROP_DB_PASSWORD:?SWIFTDROP_DB_PASSWORD must be set}"
  : "${KEYCLOAK_DB_PASSWORD:?KEYCLOAK_DB_PASSWORD must be set}"

  create_db() {
    local db="$1" role="$2" password="$3"
    echo "Creating database '${db}' owned by '${role}'"
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
         -v role="$role" -v password="$password" -v db="$db" <<'EOSQL'
CREATE ROLE :"role" LOGIN PASSWORD :'password';
CREATE DATABASE :"db" OWNER :"role";
REVOKE ALL ON DATABASE :"db" FROM PUBLIC;
EOSQL
  }

  # database:role
  for entry in \
    "users_db:user_svc" \
    "merchants_db:merchant_svc" \
    "pricing_db:pricing_svc" \
    "parcels_db:parcel_svc" \
    "dispatch_db:dispatch_svc" \
    "reporting_db:reporting_svc"; do
    create_db "${entry%%:*}" "${entry##*:}" "$SWIFTDROP_DB_PASSWORD"
  done

  create_db "keycloak" "keycloak" "$KEYCLOAK_DB_PASSWORD"
  echo "All SwiftDrop databases created."
)
