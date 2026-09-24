# DelayStageServiceApp

## Overview

Tracks the Transit Delay Stage (0-8, e.g. weather shutdowns) per hub for
the LogisticsConnect pipeline.

Part of the [LogisticsConnect](../README.md) project. Independent Maven
module, no parent pom.

## Architecture

Standalone service — per the integration contract, `delay-stage-service`
does **not** call `hub-service` or `ingestion-service`. It accepts any
`hubId` string and tracks a stage against it, with no cross-service check
that the hub actually exists. `transit-service` is the caller that ties
this data to a real hub.

State is held in memory only (`DelayStageStore`, backed by a
`ConcurrentHashMap`) — nothing is persisted, so all recorded stages are
lost on restart. Hub IDs are normalized (trimmed, uppercased) on both
read and write, so `h-501` and `H-501` refer to the same entry.

### Stage 3 (MQ) — not yet wired in

`POST /delay-stage/{hubId}` is also where the stage-3 publish to the
`package-status-topic` ActiveMQ topic will eventually happen (see
`co.wethinkcode.logisticsconnect.mq.MqConfig`), once `transit-service`
subscribes to stage changes instead of calling `GET /delay-stage/{hubId}`
directly. The `activemq-client` dependency is already present in
`pom.xml` and the exact insertion point is marked with a `// MQ TODO`
comment in `DelayStageServiceApp`, but no publish call is made yet at
this stage.

## Project structure

```
delay-stage-service/
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   └── java/co/wethinkcode/logisticsconnect/
    │       ├── DelayStageServiceApp.java   # bootstrap, endpoints, validation
    │       ├── DelayStageStore.java        # in-memory stage state
    │       └── mq/
    │           └── MqConfig.java           # broker URL + topic name (stage 3, unused so far)
    └── test/
        └── java/co/wethinkcode/logisticsconnect/
            ├── DelayStageServiceAppTest.java
            └── DelayStageStoreTest.java
```

## Build

```
mvn package
```

## Run

```
java -jar target/delay-stage-service.jar
```

Listens on port `7052`. No other services need to be running — this one
has no outbound dependencies.

## API

| Method | Path | Body | Returns |
|---|---|---|---|
| `GET` | `/health` | — | `OK` |
| `GET` | `/delay-stage/{hubId}` | — | `200` with `{hubId, stage}`, `404` if no stage has been recorded for that hub yet |
| `POST` | `/delay-stage/{hubId}` | `{"stage": <0-8>}` | `200` with `{hubId, stage}` on success, `400` on malformed JSON, missing `stage`, a non-integer value, or a value outside `0`–`8` |

Hub ID lookup is case-insensitive and trims whitespace, same as
`hub-service`.

Example:

```
curl -X POST http://localhost:7052/delay-stage/H-501 \
  -H "Content-Type: application/json" \
  -d '{"stage":3}'
# -> {"hubId":"H-501","stage":3}

curl http://localhost:7052/delay-stage/H-501
# -> {"hubId":"H-501","stage":3}

curl http://localhost:7052/delay-stage/H-999
# -> 404 {"error":"No delay stage recorded for hub H-999"}

curl -X POST http://localhost:7052/delay-stage/H-501 \
  -H "Content-Type: application/json" \
  -d '{"stage":99}'
# -> 400 {"error":"Field 'stage' must be between 0 and 8 (got 99)"}
```

## Test

```
mvn clean test
```

No external dependencies needed to run the suite — this service owns its
own state, so tests run entirely in-process:

- `DelayStageStoreTest` — pure in-memory state (set/get, case-insensitive
  and trimmed hubId lookup, overwrite behavior, independent tracking per
  hub)
- `DelayStageServiceAppTest` — `parseStage` (pure request-body validation:
  valid payload, boundary values `0`/`8`, missing field, non-integer,
  out-of-range, malformed JSON) and the full `GET`/`POST`
  `/delay-stage/{hubId}` endpoint end-to-end against a real Javalin
  instance (`200`/`404`/`400` cases, case-insensitive hubId)
