# 04 — Modèle canonique (v0)

Le **modèle canonique** est le langage commun de NOTRE plateforme. Les bridges traduisent
le legacy vers ce modèle ; la saga et le gateway ne parlent que lui.
Contrat REST complet : `contracts/bridge-api.openapi.yaml`.

## Objets

| Objet | Champs |
|---|---|
| **Offer** | `offerId`, `mode` (TRAIN/FLIGHT/CAR), `origin`, `destination`, `departure`, `arrival`, `priceMinor`, `currency` (=MAD), `seatsAvailable` |
| **Hold** | `holdId`, `offerId`, `mode` |
| **Booking** | `bookingRef`, `holdId`, `mode` |

Opérations communes (identiques pour les 3 modes) : `search`, `hold`, `confirm`, `cancel`.

## Table de traduction

| Canonique | Rail (CORBA `Trip`) | Air (SOAP `Flight`) |
|---|---|---|
| `offerId` | `tripId` | `flightId` |
| `mode` | `TRAIN` (constante) | `FLIGHT` (constante) |
| `origin`, `destination` | identiques | identiques |
| `departure`, `arrival` | identiques (ISO-8601) | identiques (ISO-8601) |
| `priceMinor` | `priceMinor` | `priceMinor` |
| `currency` | `MAD` (constante) | `MAD` (constante) |
| `seatsAvailable` | `seatsAvailable` | `seatsAvailable` |

| Opération canonique | Rail (CORBA) | Air (SOAP) | Car-share |
|---|---|---|---|
| `search` | `search(o, d, date)` | `searchFlights` | natif |
| `hold` | `hold(tripId, seats)` | `holdFlight` | natif |
| `confirm` | `confirm(holdId)` | `confirmFlight` | natif |
| `cancel` | `cancel(ref)` | `cancelFlight` | natif |
| erreur de dispo | exception `NotAvailable` | SOAP Fault | — |
| → côté canonique | HTTP **409** `NOT_AVAILABLE` | HTTP **409** `NOT_AVAILABLE` | HTTP **409** |

## Décisions déjà prises (à justifier dans un ADR)

1. **Montants en centimes (`priceMinor`)** : pas de flottants pour l'argent.
2. **Dates en ISO-8601 sans fuseau** : le legacy n'en fournit pas ; on ne l'invente pas.
3. **`offerId` = id legacy** : simple pour la v0. Limite à citer : fuite légère du legacy dans l'identifiant.
4. **`cancel` idempotent, toujours 204** : miroir de la sémantique legacy.
5. **Erreurs métier → 409** : le legacy lève une exception, le REST renvoie un code HTTP + `Problem`.

## À décider ensemble (et noter dans un ADR)

- Format de l'offre multimodale agrégée (un `Trip` = liste d'offres + prix total ?).
- Orchestration (saga pilote) : choix déjà proposé, à confirmer.
