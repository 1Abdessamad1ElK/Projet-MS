# 00 — START HERE

The whole platform is generated. Your job now: **run it, fix what breaks, understand it, defend it.**

## 0. Add this overlay to the repo (once, one of you)

Unzip into the **root of the GitHub Classroom repo** (next to `docker-compose.yml`). It only *adds* files.

```bash
git add .
git commit -m "Platform: bridges, microservices, saga, gateway, compose, demo"
git push
```
The other person: `git pull`.

## 1. Start everything (needs Docker running)

```bash
docker compose up --build
```
First build: 10-20 min (Maven downloads + OpenTelemetry agent download). Wait until the logs calm down.

Health check: `curl http://localhost:8080/health` → `{"status":"UP"}` and `./verify.sh` (legacy systems).

## 2. Run the demo

```bash
./demo/demo.sh        # Git Bash on Windows
```
Or in IntelliJ: open `demo/scenario.http` and run the requests top to bottom.

It shows: login → aggregated search → **nominal booking (201)** → **failure A: flight full → train hold cancelled (409)** → **failure B: car fails at confirm → train + flight + car all cancelled (409)** → **idempotent replay** → notifications.

Then open **Jaeger** http://localhost:16686 → service `gateway` → look at a `POST /api/v1/reservations` trace:
you should see the gateway → reservations → rail-bridge (`rpc.system=corba`) / air-bridge (`rpc.system=soap`).

## 3. If something breaks (it was written without being able to run the stack — expect small fixes)

| Symptom | Where to look |
|---|---|
| `docker compose up` fails on one build | read the Maven error for that service; `docker compose build <service>` to retry only it |
| `rail-bridge` can't reach CORBA | `docker compose logs rail-bridge`; check `ior/RailBooking.ior` exists; `CORBA_ADVERTISED_HOST` must be `rail` (default) |
| `air-bridge` 503 | `docker compose logs air-bridge`; WSDL ok at http://localhost:8081/ws/air.wsdl ? |
| reservation returns 409 `Validation/pricing failed` | the offer/origin/destination/date in your request don't match (data is for **2026-11-15**) |
| notifications empty | RabbitMQ not healthy yet; `docker compose logs notification-service` |
| Port already in use | change the left side of the port mapping in `docker-compose.override.yml` |

Rebuild one service after a fix: `docker compose up --build -d <service>` (e.g. `reservations-service`).

## 4. Run the unit tests (no Docker needed)

```bash
cd reservations-service && mvn test     # saga: nominal, compensation, idempotency, window...  (JDK 17)
cd pricing-service      && mvn test     # pricing rules                                          (JDK 17)
```

## 5. Lot 0 clients (optional now, useful to understand CORBA/SOAP by hand)

`lot0/air-client` (`java AirClient.java`, JDK 17) and `lot0/rail-client` (Maven, JDK 8).
For `rail-client` run the platform with `CORBA_ADVERTISED_HOST=localhost docker compose up --build`
(then `rail-bridge` in Docker can't reach the train: use it only for the Lot 0 test, then restart normally).

## 6. Who does what now (2 people, alternating)

| | **A — legacy side** | **B — platform side** |
|---|---|---|
| Own & be able to explain | `rail-bridge`, `air-bridge`, `reservations-service` (saga) | `carshare`, `pricing`, `notification`, `gateway`, compose, tracing |
| Report | saga + CORBA/SOAP sections, ADR 001/003/004/005/007/008 | architecture, observability, limits, README, ADR 002/010/011/012 |

Both: read `docs/05-architecture-et-adr.md` end to end. Swap explanations once before the defense.

## 7. Rules

- Never edit `rail-operator-corba/` or `air-partner-soap/`.
- `main` always builds and starts; branch + PR for changes.
- Update `HANDOFF.md` at the end of each session.
- Final: `git tag -a v1.0-rendu -m "Version rendue" && git push origin v1.0-rendu`, then **test a fresh clone** with `docker compose up --build`.

## Useful URLs

Gateway :8080 · rail-bridge :8101 · air-bridge :8102 · carshare :8103 · pricing :8104 · reservations :8105 ·
notifications :8106 · RabbitMQ UI :15672 (guest/guest) · Jaeger :16686 · Air WSDL :8081/ws/air.wsdl
