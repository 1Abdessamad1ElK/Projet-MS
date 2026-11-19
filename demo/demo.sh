#!/usr/bin/env bash
# =====================================================================
#  Scenario de demonstration (rejouable) - a lancer APRES `docker compose up --build`
#  Windows : Git Bash.   Usage : ./demo/demo.sh
#
#  1. login (JWT)            4. echec A : le vol n'a pas assez de places  -> compensation du train
#  2. recherche agregee      5. echec B : voiture en panne au CONFIRM     -> compensation train + vol
#  3. reservation nominale   6. rejeu avec la meme Idempotency-Key        -> aucun doublon
#                            7. notifications recues via RabbitMQ
# =====================================================================
GW="${GW:-http://localhost:8080}"
set -u

title() { printf '\n\033[1;36m== %s\033[0m\n' "$1"; }

title "1. Login"
TOKEN=$(curl -s -X POST "$GW/api/v1/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"demo123"}' | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
[ -n "$TOKEN" ] && echo "token obtenu (${#TOKEN} caracteres)" || { echo "login KO : la plateforme est-elle demarree ?"; exit 1; }
AUTH="Authorization: Bearer $TOKEN"

title "2. Recherche multimodale agregee (train CAS->RAB, vol RAB->PAR, voiture a Paris)"
curl -s "$GW/api/v1/search?origin=Casablanca&via=Rabat&destination=Paris&date=2026-11-15" -H "$AUTH"; echo

title "3. Reservation NOMINALE : train + vol + voiture  (attendu : 201 CONFIRMED)"
curl -s -w '\nHTTP %{http_code}\n' -X POST "$GW/api/v1/reservations" -H "$AUTH" -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: demo-nominal-1' -d '{
  "customerEmail": "voyageur@example.ma",
  "legs": [
    {"mode":"TRAIN",  "offerId":"RAIL-CAS-RAB-0730", "seats":1, "origin":"Casablanca","destination":"Rabat",  "date":"2026-11-15"},
    {"mode":"FLIGHT", "offerId":"AIR-RBA-ORY-1200",  "seats":1, "origin":"Rabat",     "destination":"Paris",  "date":"2026-11-15"},
    {"mode":"CAR",    "offerId":"CAR-PAR-001",       "seats":1, "origin":"Paris",     "destination":"Paris",  "date":"2026-11-15"}
  ]}'

title "4. ECHEC A - vol AIR-CMN-MAD-1800 (3 places) : on en demande 4 (attendu : 409 FAILED, train annule)"
curl -s -w '\nHTTP %{http_code}\n' -X POST "$GW/api/v1/reservations" -H "$AUTH" -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: demo-fail-hold-1' -d '{
  "customerEmail": "voyageur@example.ma",
  "legs": [
    {"mode":"TRAIN",  "offerId":"RAIL-CAS-RAB-0730", "seats":4, "origin":"Casablanca","destination":"Rabat",  "date":"2026-11-15"},
    {"mode":"FLIGHT", "offerId":"AIR-CMN-MAD-1800",  "seats":4, "origin":"Casablanca","destination":"Madrid", "date":"2026-11-15"},
    {"mode":"CAR",    "offerId":"CAR-MAD-001",       "seats":1, "origin":"Madrid",    "destination":"Madrid", "date":"2026-11-15"}
  ]}'

title "5. ECHEC B - voiture CAR-PAR-FAULTY : hold OK puis confirm refuse (attendu : 409 FAILED, train + vol + voiture annules)"
curl -s -w '\nHTTP %{http_code}\n' -X POST "$GW/api/v1/reservations" -H "$AUTH" -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: demo-fail-confirm-1' -d '{
  "customerEmail": "voyageur@example.ma",
  "legs": [
    {"mode":"TRAIN",  "offerId":"RAIL-CAS-RAB-0900", "seats":1, "origin":"Casablanca","destination":"Rabat",  "date":"2026-11-15"},
    {"mode":"FLIGHT", "offerId":"AIR-RBA-ORY-1200",  "seats":1, "origin":"Rabat",     "destination":"Paris",  "date":"2026-11-15"},
    {"mode":"CAR",    "offerId":"CAR-PAR-FAULTY",    "seats":1, "origin":"Paris",     "destination":"Paris",  "date":"2026-11-15"}
  ]}'

title "6. IDEMPOTENCE - on rejoue la requete du point 3 avec la meme cle (attendu : meme RES-xxxx, aucun nouvel appel)"
curl -s -w '\nHTTP %{http_code}\n' -X POST "$GW/api/v1/reservations" -H "$AUTH" -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: demo-nominal-1' -d '{
  "customerEmail": "voyageur@example.ma",
  "legs": [
    {"mode":"TRAIN",  "offerId":"RAIL-CAS-RAB-0730", "seats":1, "origin":"Casablanca","destination":"Rabat",  "date":"2026-11-15"},
    {"mode":"FLIGHT", "offerId":"AIR-RBA-ORY-1200",  "seats":1, "origin":"Rabat",     "destination":"Paris",  "date":"2026-11-15"},
    {"mode":"CAR",    "offerId":"CAR-PAR-001",       "seats":1, "origin":"Paris",     "destination":"Paris",  "date":"2026-11-15"}
  ]}' | head -c 400; echo " ..."

title "7. Notifications recues (evenements RabbitMQ)"
sleep 1
curl -s "$GW/api/v1/notifications" -H "$AUTH"; echo

echo
echo "Traces distribuees : http://localhost:16686  (service 'gateway' -> POST /api/v1/reservations)"
echo "RabbitMQ           : http://localhost:15672  (guest/guest)"
