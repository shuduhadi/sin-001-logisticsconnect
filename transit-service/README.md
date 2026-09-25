# TransitServiceApp

## Overview

Calculates estimated arrival windows based on hub and delay stage. This
is the service that ties the whole synchronous chain together for stage
2: it calls both `hub-service` and `delay-stage-service`, then combines
their responses into an ETA.

Part of the [LogisticsConnect](../README.md) project. Independent Maven
module, no parent pom.

MQ: this service subscribes to the ActiveMQ topic `package-status-topic`
— see [`../common/`](../common). Broker URL and topic name come from the
common `co.wethinkcode.logisticsconnect.mq.MqConfig` class alongside it in
this module. **Not wired in yet at this stage** — `delay-stage-service` is
still called directly over HTTP; the subscription replaces that call in
stage 3.

## Architecture

On every `GET /transit/{hubId}` request:

1. Calls `GET {hub-service}/hubs/{hubId}` — if the hub doesn't exist,
   returns `404` immediately.
2. Calls `GET {delay-stage-service}/delay-stage/{hubId}` — if no stage has
   ever been recorded for this hub (`404`), **defaults to stage 0** rather
   than failing the request. A hub with no delay history isn't an error
   condition; it just means "no known delay."
3. Combines both into an ETA via `EtaCalculator`.

Like `hub-service`, there's no caching — both calls happen fresh on every
request, for the same reason: this stage is meant to demonstrate the
direct REST coupling that stage 3 partially replaces with MQ.

### ETA formula

No distance, routing, or coordinate data exists anywhere in this
pipeline — `hub-service` only knows province and sorting center, not
physical location. So the formula is deliberately simple rather than
physically accurate:

```
earliest = now + BASE_TRANSIT_HOURS + (delayStage × HOURS_PER_DELAY_STAGE)
latest   = earliest + WINDOW_BUFFER_HOURS
```

| Constant | Value |
|---|---|
| `BASE_TRANSIT_HOURS` | 24 |
| `HOURS_PER_DELAY_STAGE` | 6 |
| `WINDOW_BUFFER_HOURS` | 4 |

All three are named constants in `EtaCalculator`, chosen to be easy to
reason about and easy to change — not derived from any real transit data.
With more time, the natural next step would be a province-to-province (or
hub-to-hub) base-time table, so the ETA actually varies by where the hub
is rather than only by its delay stage.

`now` is supplied via an injected `java.time.Clock` rather than read
directly, so the calculation is deterministic and testable with a fixed
clock.

## Project structure

```
transit-service/
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   └── java/co/wethinkcode/logisticsconnect/
    │       ├── TransitServiceApp.java   # bootstrap, endpoint, orchestration
    │       ├── HubClient.java           # calls hub-service's GET /hubs/{hubId}
    │       ├── DelayStageClient.java    # calls delay-stage-service's GET /delay-stage/{hubId}
    │       ├── EtaCalculator.java       # the ETA formula
    │       ├── EtaWindow.java           # earliest/latest result record
    │       ├── HubRecord.java           # local copy of the hub JSON shape
    │       └── mq/
    │           └── MqConfig.java        # broker URL + topic name (stage 3, unused so far)
    └── test/
        └── java/co/wethinkcode/logisticsconnect/
            ├── HubClientTest.java
            ├── DelayStageClientTest.java
            ├── EtaCalculatorTest.java
            └── TransitServiceAppTest.java
```

## Build

```
mvn package
```

## Run

Requires `hub-service` and `delay-stage-service` both running (which in
turn means `ingestion-service` running too, for `hub-service` to have
anything to serve):

```
# terminal 1
cd ../ingestion-service && java -jar target/ingestion-service.jar

# terminal 2
cd ../hub-service && java -jar target/hub-service.jar

# terminal 3
cd ../delay-stage-service && java -jar target/delay-stage-service.jar

# terminal 4
java -jar target/transit-service.jar
```

Listens on port `7053`. Calls `hub-service` at `http://localhost:7051`
and `delay-stage-service` at `http://localhost:7052` by default.

## API

| Method | Path | Returns |
|---|---|---|
| `GET` | `/health` | `OK` |
| `GET` | `/transit/{hubId}` | `200` with hub + ETA details, `404` if the hub doesn't exist, `502` if either upstream service is unreachable or errors |

Example `200` response for `GET /transit/H-500` (assuming delay stage 3
was previously `POST`ed to `delay-stage-service`):

```json
{
  "hubId": "H-500",
  "sortingCenter": "Johannesburg Central",
  "province": "Gauteng",
  "delayStage": 3,
  "estimatedArrival": {
    "earliest": "2026-09-25T18:00:00Z",
    "latest": "2026-09-25T22:00:00Z"
  }
}
```

If no delay stage has ever been recorded for the hub, `delayStage` comes
back as `0` rather than the request failing.

## Test

```
mvn clean test
```

No live `hub-service` or `delay-stage-service` is needed to run the
suite — tests stand up local `com.sun.net.httpserver.HttpServer` stubs in
place of both, and `EtaCalculator` is tested against a fixed `Clock` for
deterministic timestamps:

- `HubClientTest` — `parseHub` (pure) and `fetchHub` (against a
  hub-service stub: success, `404` → empty, error status, unreachable)
- `DelayStageClientTest` — same shape as above, against a
  delay-stage-service stub
- `EtaCalculatorTest` — the formula itself: zero delay, per-stage hour
  increment, the max stage (8), and window width, all asserted against
  exact `Instant` values
- `TransitServiceAppTest` — the full `GET /transit/{hubId}` endpoint
  end-to-end against real Javalin, wired to **two** stubs at once (one
  per upstream service): both found, missing delay stage defaulting to
  0, unknown hub, and either upstream being unreachable

```
curl http://localhost:7053/health         # -> OK
curl http://localhost:7053/transit/H-500  # -> ETA JSON (needs hub-service + delay-stage-service running)
curl http://localhost:7053/transit/H-999  # -> 404
```
