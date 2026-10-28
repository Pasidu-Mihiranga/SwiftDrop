#!/usr/bin/env bash
# M2 Pricing and Zones: end-to-end demonstration.
#
# Proves, against a running deployment, the four behaviours Issue #15 asks for:
#   1. an anonymous fee estimate, with a breakdown that reconciles to the total
#   2. an admin rate change, versioned rather than edited
#   3. a quote already issued keeping its fee after that change
#   4. quote expiry answering 404, indistinguishable from an unknown id
#
# It also exercises GET /api/v1/pricing/quotes/{id} exactly the way parcel-service
# will, which is as far as this can go until M3 ships a booking endpoint.
#
# Works against either deployment:
#   ./scripts/demo-m2-pricing.sh                                  # kind, the defaults
#   DEMO_BASE_URL=http://localhost:8080/api/v1 DEMO_HOST_HEADER= \
#   DEMO_AUTH_URL=http://localhost:8180 DEMO_AUTH_HOST= \
#     ./scripts/demo-m2-pricing.sh                                # compose
#
# Credentials come from the git-ignored .env and are never printed.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

# .env is sourced first, because it defines AUTH_URL and GATEWAY_URL for compose and
# would otherwise overwrite this script's own endpoint settings.
[ -f .env ] || { echo "No .env found. Run 'pnpm env:init' first." >&2; exit 1; }
set -a; . ./.env; set +a

BASE_URL="${DEMO_BASE_URL:-http://127.0.0.1/api/v1}"
HOST_HEADER="${DEMO_HOST_HEADER-api.swiftdrop.local}"
KC_BASE_URL="${DEMO_AUTH_URL:-http://127.0.0.1}"
KC_HOST="${DEMO_AUTH_HOST-auth.swiftdrop.local}"

pass=0; fail=0
step()  { printf "\n\033[1m== %s\033[0m\n" "$1"; }
ok()    { printf "   PASS  %s\n" "$1"; pass=$((pass+1)); }
bad()   { printf "   FAIL  %s\n" "$1"; fail=$((fail+1)); }
check() { if [ "$1" = "$2" ]; then ok "$3 ($1)"; else bad "$3: expected $2, got $1"; fi; }

# curl against the API, with the Host header only when one is configured.
api() {
  if [ -n "${HOST_HEADER}" ]; then
    curl -s -m 30 -H "Host: ${HOST_HEADER}" "$@"
  else
    curl -s -m 30 "$@"
  fi
}

jq_get() { python3 -I -c "import sys,json;d=json.load(sys.stdin);print(eval('d'+sys.argv[1]))" "$1"; }

# ---------------------------------------------------------------- tokens
step "Acquire tokens from Keycloak"
get_token() {
  local user="$1"
  curl -s -m 30 -H "Host: ${KC_HOST}" \
    "${KC_BASE_URL}/realms/${KEYCLOAK_REALM:-swiftdrop}/protocol/openid-connect/token" \
    -d grant_type=password -d client_id=swiftdrop-test \
    -d "username=${user}" -d "password=${KC_SEED_USER_PASSWORD}" -d scope=openid \
    | python3 -I -c "import sys,json;print(json.load(sys.stdin).get('access_token',''))"
}
ADMIN_TOKEN="$(get_token admin@swiftdrop.lk)"
CUSTOMER_TOKEN="$(get_token customer@swiftdrop.lk)"
[ -n "${ADMIN_TOKEN}" ]    && ok "ADMIN token"    || { bad "ADMIN token"; exit 1; }
[ -n "${CUSTOMER_TOKEN}" ] && ok "CUSTOMER token" || { bad "CUSTOMER token"; exit 1; }

# ---------------------------------------------------------------- 1. estimate
step "1. Anonymous fee estimate (no token at all)"
api "${BASE_URL}/pricing/zones" -o /tmp/m2-zones.json -w "" 
ZONES="$(python3 -I -c "import json;print(len(json.load(open('/tmp/m2-zones.json'))))")"
check "${ZONES}" "9" "active zones listed anonymously"

api -X POST "${BASE_URL}/pricing/quotes" -H 'content-type: application/json' \
  -d '{"fromDistrict":"Colombo","toDistrict":"Kandy","weightKg":2,"sizeClass":"M","serviceLevel":"STANDARD"}' \
  -o /tmp/m2-q1.json
python3 -I - <<'PY'
import json
d = json.load(open('/tmp/m2-q1.json'))
lines = d['breakdown']
total = round(sum(l['amount'] for l in lines), 2)
print(f"   quote {d['quoteId']}")
print(f"   {d['fromDistrict']} ({d['fromZone']}) -> {d['toDistrict']} ({d['toZone']})  {d['serviceLevel']}")
for l in lines:
    print(f"     {l['rule']:<8} {l['amount']:>10.2f}")
print(f"     {'TOTAL':<8} {d['fee']['amount']:>10.2f} {d['fee']['currency']}")
print(f"   issued {d['issuedAt']}  expires {d['expiresAt']}")
open('/tmp/m2-q1-id','w').write(d['quoteId'])
open('/tmp/m2-q1-fee','w').write(str(d['fee']['amount']))
assert total == d['fee']['amount'], f"breakdown {total} != fee {d['fee']['amount']}"
PY
ok "breakdown lines reconcile exactly with the total"

step "1b. Distance matters: the same parcel on a longer lane"
NEAR="$(cat /tmp/m2-q1-fee)"
api -X POST "${BASE_URL}/pricing/quotes" -H 'content-type: application/json' \
  -d '{"fromDistrict":"Colombo","toDistrict":"Jaffna","weightKg":2,"sizeClass":"M","serviceLevel":"STANDARD"}' \
  -o /tmp/m2-q2.json
FAR="$(jq_get "['fee']['amount']" < /tmp/m2-q2.json)"
printf "   Colombo->Kandy %s   Colombo->Jaffna %s\n" "${NEAR}" "${FAR}"
python3 -I -c "import sys;sys.exit(0 if float('${FAR}')>float('${NEAR}') else 1)" \
  && ok "a lane crossing more province borders costs more" \
  || bad "border bands are not in force"

# ---------------------------------------------------------------- 2. admin rate change
step "2. Admin rate change: the card in force is closed, a new one is added"
api "${BASE_URL}/admin/pricing/rate-cards?fromZone=WP&toZone=CP" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" -o /tmp/m2-cards.json

# The admin list uses the project's shared pagination envelope, { content, page }, and
# includes closed cards as well as the one in force.
#
# The change date is derived from the existing history rather than being hardcoded to
# tomorrow, so this demo can be run repeatedly: each run closes whatever card is open and
# versions a new one after it. That accumulation is the designed behaviour, because a rate
# card is never edited or deleted in place.
read -r CARD_ID OLD_BASE CHANGE_DATE <<<"$(python3 -I -c "
import datetime, json
d = json.load(open('/tmp/m2-cards.json'))
cards = d['content'] if isinstance(d, dict) else d
if not cards:
    raise SystemExit('no WP->CP rate card exists; is the seed migration applied?')
live = [c for c in cards if c.get('effectiveTo') in (None, '')]
if not live:
    raise SystemExit('no WP->CP card is currently in force')
latest = max(datetime.date.fromisoformat(c['effectiveFrom']) for c in cards)
change = max(latest, datetime.date.today()) + datetime.timedelta(days=1)
print(live[0]['id'], live[0]['baseFee'], change.isoformat())
")"
printf "   WP->CP card in force: %s baseFee=%s\n" "${CARD_ID}" "${OLD_BASE}"
printf "   the change takes effect %s\n" "${CHANGE_DATE}"

# Effective periods are half-open, so a card starting the day another ends leaves neither
# a gap nor an overlap.
CODE="$(api -o /tmp/m2-close.json -w '%{http_code}' -X PUT \
  "${BASE_URL}/admin/pricing/rate-cards/${CARD_ID}/closure" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" -H 'content-type: application/json' \
  -d "{\"effectiveTo\":\"${CHANGE_DATE}\"}")"
check "${CODE}" "200" "closing the card in force"

NEW_BASE="$(python3 -I -c "print(float('${OLD_BASE}')+200)")"
CODE="$(api -o /tmp/m2-new.json -w '%{http_code}' -X POST "${BASE_URL}/admin/pricing/rate-cards" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" -H 'content-type: application/json' \
  -d "{\"fromZone\":\"WP\",\"toZone\":\"CP\",\"baseFee\":${NEW_BASE},\"perKgFee\":78,\"expressMultiplier\":1.5,\"codFeePct\":0.015,\"effectiveFrom\":\"${CHANGE_DATE}\"}")"
check "${CODE}" "201" "adding the replacement card from ${CHANGE_DATE}"
printf "   baseFee %s -> %s. The old row is closed, not deleted.\n" "${OLD_BASE}" "${NEW_BASE}"

step "2b. Overlapping periods for the same lane are rejected"
CODE="$(api -o /tmp/m2-dup.json -w '%{http_code}' -X POST "${BASE_URL}/admin/pricing/rate-cards" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" -H 'content-type: application/json' \
  -d "{\"fromZone\":\"WP\",\"toZone\":\"CP\",\"baseFee\":999,\"perKgFee\":78,\"expressMultiplier\":1.5,\"codFeePct\":0.015,\"effectiveFrom\":\"${CHANGE_DATE}\"}")"
check "${CODE}" "409" "a second card starting the same day is refused"
printf "   code=%s\n" "$(jq_get "['code']" < /tmp/m2-dup.json 2>/dev/null || echo '-')"

step "3. The quote issued in step 1 still has its original fee"
QID="$(cat /tmp/m2-q1-id)"
api "${BASE_URL}/pricing/quotes/${QID}" -H "Authorization: Bearer ${CUSTOMER_TOKEN}" -o /tmp/m2-look.json
LOOKED_UP="$(jq_get "['fee']['amount']" < /tmp/m2-look.json)"
check "${LOOKED_UP}" "$(cat /tmp/m2-q1-fee)" "the stored snapshot is returned verbatim"
printf "   Redis holds the computed snapshot, so lookup never recalculates.\n"

step "3b. This is exactly the call parcel-service will make"
printf "   GET %s/pricing/quotes/{quoteId} with a bearer token\n" "${BASE_URL}"
python3 -I - <<'PY'
import json
d = json.load(open('/tmp/m2-look.json'))
need = ["quoteId","fee","breakdown","fromZone","toZone","fromDistrict","toDistrict",
        "weightKg","sizeClass","serviceLevel","codAmount","issuedAt","expiresAt"]
missing = [k for k in need if k not in d]
print("   fields parcel-service can validate:", ", ".join(need))
assert not missing, f"missing {missing}"
PY
ok "every field in the agreed M3 contract is present"
printf "   NOTE: parcel-service has no create-parcel endpoint yet, so the consuming\n"
printf "         side of this handoff cannot be demonstrated. See Issue #15 Phase 3.\n"

# ---------------------------------------------------------------- 4. authorization and expiry
step "4. Authorization"
CODE="$(api -o /dev/null -w '%{http_code}' "${BASE_URL}/pricing/quotes/${QID}")"
check "${CODE}" "401" "lookup without a token"
CODE="$(api -o /tmp/m2-403.json -w '%{http_code}' "${BASE_URL}/admin/pricing/rate-cards" \
  -H "Authorization: Bearer ${CUSTOMER_TOKEN}")"
check "${CODE}" "403" "admin rate-cards with a CUSTOMER token"
CODE="$(api -o /tmp/m2-404.json -w '%{http_code}' \
  "${BASE_URL}/pricing/quotes/00000000-0000-0000-0000-000000000000" \
  -H "Authorization: Bearer ${CUSTOMER_TOKEN}")"
check "${CODE}" "404" "an unknown quote id"
printf "   code=%s\n" "$(jq_get "['code']" < /tmp/m2-404.json 2>/dev/null || echo '-')"

step "4b. Quote expiry"
printf "   A quote lives for PRICING_QUOTE_TTL (PT15M by default). An expired id answers\n"
printf "   404 QUOTE_NOT_FOUND, identical to an unknown id, so expiry is not\n"
printf "   distinguishable from a guess.\n"
if [ "${DEMO_WAIT_FOR_EXPIRY:-0}" = "1" ]; then
  printf "   DEMO_WAIT_FOR_EXPIRY=1: waiting for the real TTL to elapse.\n"
  while :; do
    CODE="$(api -o /dev/null -w '%{http_code}' "${BASE_URL}/pricing/quotes/${QID}" \
      -H "Authorization: Bearer ${CUSTOMER_TOKEN}")"
    [ "${CODE}" = "404" ] && break
    printf "   still %s, sleeping 60s\n" "${CODE}"
    sleep 60
  done
  check "${CODE}" "404" "the quote expired on its own"
else
  printf "   Skipped here to keep the demo short. Two ways to see it for real:\n"
  printf "     DEMO_WAIT_FOR_EXPIRY=1 %s   (waits out the TTL)\n" "$0"
  printf "     helm upgrade ... --set pricing-service.config.PRICING_QUOTE_TTL=PT1M\n"
  printf "   The boundary itself is asserted exactly, against a controlled clock, in\n"
  printf "   QuoteExpiryIT rather than by waiting.\n"
fi

# ---------------------------------------------------------------- restore
step "The rate-card history after this run"
api "${BASE_URL}/admin/pricing/rate-cards?fromZone=WP&toZone=CP" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" -o /tmp/m2-history.json
python3 -I - <<'PY'
import json
d = json.load(open("/tmp/m2-history.json"))
for c in sorted(d["content"], key=lambda c: c["effectiveFrom"]):
    until = c["effectiveTo"] or "open"
    print(f"   {c['effectiveFrom']} .. {until:<12} baseFee {c['baseFee']:>8.2f}")
PY
printf "   Versioning means nothing is overwritten, so every past price stays auditable.\n"
printf "   To put the seeded prices back, recreate the volume:\n"
printf "     kubectl -n swiftdrop delete pvc data-postgres-0; kubectl -n swiftdrop delete pod postgres-0\n"

printf "\n\033[1m== Result: %s passed, %s failed\033[0m\n" "${pass}" "${fail}"
[ "${fail}" -eq 0 ] || exit 1
