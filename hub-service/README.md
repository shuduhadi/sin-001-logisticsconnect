# HubServiceApp

## Overview

Serves provinces and sorting centers (place-name source of truth) for the
LogisticsConnect pipeline. Calls ingestion-service synchronously on each
request to resolve the current cleaned hub data.

Part of the [LogisticsConnect](../README.md) project. Independent Maven
module, no parent pom.

## Architecture

`hub-service` does **not** cache hub data at startup. On every
`GET /hubs/{hubId}` request, it calls `GET {ingestion-service}/hubs`
synchronously, then looks up the requested ID in the response.

This is a deliberate choice for stage 2: the point of this stage is to
demonstrate real synchronous REST-to-REST calls between services — the
kind of direct coupling that stage 3 later replaces with the MQ topic for
the delay-stage → transit link. Caching at startup (as `ingestion-service`
does for its own static CSV data) would hide that call chain and risk
serving stale data if `ingestion-service`'s CSV changes between requests.

The tradeoff: every request to `hub-service` costs a network round-trip to
`ingestion-service`, and if `ingestion-service` is down, `hub-service`
can't serve *any* hub lookups even for data it successfully fetched a
moment ago. No retry/backoff or circuit breaker is implemented — a single
failed call surfaces as a `502` to the caller (see [API](#api) below).

## Project structure

```
hub-service/
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   └── java/co/wethinkcode/logisticsconnect/
    │       ├── HubServiceApp.java     # bootstrap, endpoint, hub lookup
    │       ├── IngestionClient.java   # calls ingestion-service's GET /hubs
    │       └── HubRecord.java         # local copy of the hub JSON shape
    └── test/
        └── java/co/wethinkcode/logisticsconnect/
            ├── HubServiceAppTest.java
            └── IngestionClientTest.java
```

`HubRecord` is hub-service's own copy of the record shape returned by
ingestion-service's `GET /hubs` — the two are independent Maven modules
with no shared library, so this mirrors that JSON contract rather than
reusing ingestion-service's class directly.

## Build

```
mvn package
```

## Run

Requires `ingestion-service` to be running first (or at least reachable)
for `/hubs/{hubId}` to return anything other than `502`:

```
# terminal 1
cd ../ingestion-service && java -jar target/ingestion-service.jar

# terminal 2
java -jar target/hub-service.jar
```

Listens on port `7051`. Calls `ingestion-service` at
`http://localhost:7050` by default (see `HubServiceApp.DEFAULT_INGESTION_SERVICE_URL`).

## API

| Method | Path | Returns |
|---|---|---|
| `GET` | `/health` | `OK` |
| `GET` | `/hubs/{hubId}` | `200` with hub details, `404` if not found, `502` if ingestion-service is unreachable or errors |

Hub ID lookup is case-insensitive (`h-500` matches `H-500`) and trims
whitespace.

Example `200` response for `GET /hubs/H-500`:

```json
{
  "hubId": "H-500",
  "province": "Gauteng",
  "sortingCenter": "Johannesburg Central",
  "active": true,
  "mergedFrom": ["H-504", "H-510", "H-515"]
}
```

Example `404` response for an unknown ID:

```json
{
  "error": "No hub found with id H-999"
}
```

Example `502` response when ingestion-service is unreachable:

```json
{
  "error": "ingestion-service is unavailable: ..."
}
```

## Test

```
mvn clean test
```

No live `ingestion-service` is needed to run the suite — tests stand up a
local `com.sun.net.httpserver.HttpServer` stub in place of
ingestion-service, so both success and failure paths (200, non-200 status,
unreachable host) are covered without any external dependency:

- `IngestionClientTest` — `parseHubs` (pure JSON parsing against a canned
  payload) and `fetchHubs` (real HTTP call against the stub: success,
  error status, unreachable host)
- `HubServiceAppTest` — `findHubById` (pure lookup logic) and the full
  `GET /hubs/{hubId}` endpoint end-to-end against a real Javalin instance
  wired to the stub (`200`/`404`/`502` cases)

```
curl http://localhost:7051/health          # -> OK
curl http://localhost:7051/hubs/H-500      # -> hub JSON (needs ingestion-service running)
curl http://localhost:7051/hubs/H-999      # -> 404
```