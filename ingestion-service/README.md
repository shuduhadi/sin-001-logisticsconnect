# IngestionServiceApp

## Overview

Parses and cleans `hubs-global.csv`, a messy legacy export of hubs, sorting
centers, and regional districts data, and is the first stop in the
LogisticsConnect pipeline. Independent Maven module, no parent pom.

Part of the LogisticsConnect project.

## Known data issues

`hubs-global.csv` is deliberately messy — cleaning it is the point of this
service. The following are handled:

- **Inconsistent casing** in IDs, names, and status/category values
  (`Active` / `active` / `ACTIVE`)
- **Padding** — leading/trailing spaces, and the occasional double space,
  inside fields
- **Duplicate records** for the same real-world entity, written with a
  different ID casing/format and/or slightly different field values
- **Missing / placeholder values** — blank fields, `N/A`, `n/a`, `TBD`,
  `unknown`, `-`, `NaN`
- **Inconsistent boolean/flag representations** (`Y`/`N`, `yes`/`no`,
  `1`/`0`, `true`/`FALSE`)
- **Naming/spelling variants** for the same thing (e.g. `KwaZulu-Natal` /
  `Kwa-Zulu Natal` / `KwaZulu Natal`)

`hubs-global.csv` as shipped has no date or other numeric columns, so date
and numeric normalization aren't exercised against this file. A
`normalizeDate` utility is included regardless (handles `YYYY-MM-DD`,
`MM/DD/YYYY`, `DD-MM-YYYY`, 1- and 2-digit months/days, and rejects
calendar-invalid dates), ready to wire in if a date field is added later.

### Worked example

Two raw rows from `hubs-global.csv`:

```
hub_id, Province ,sorting_center,active
H-502 ,gauteng,Pretoria North,0
H-505,Western Cape ,Cape Town  Port,TRUE
```

The cleaned shape for those same two rows:

| hub_id | province | sorting_center | active |
|---|---|---|---|
| H-502 | Gauteng | Pretoria North | false |
| H-505 | Western Cape | Cape Town Port | true |

That covers padding (`H-502 ` → `H-502`), casing (`gauteng` → `Gauteng`), a
collapsed double space (`Cape Town  Port` → `Cape Town Port`), and boolean
normalization (`0`/`TRUE` → `false`/`true`).

## Cleaning approach

Each field is normalized independently before any deduplication happens:

| Field | Normalization |
|---|---|
| `hub_id` | trim + collapse whitespace, uppercase |
| `province` | trim + collapse whitespace; known spelling/formatting variants mapped to one canonical form (see `PROVINCE_ALIASES` in `IngestionServiceApp`); unrecognized-but-non-blank values are title-cased rather than rejected; placeholders/blank → `null` |
| `sorting_center` | trim + collapse whitespace, title-cased; placeholders/blank → `""`, and the row is then dropped (a hub with no name isn't usable downstream) |
| `active` | `Y`/`yes`/`true`/`1` → `true`; `N`/`no`/`false`/`0` → `false` (all case-insensitive); anything else, including placeholders like `unknown` or `N/A` → `null` — deliberately **not** defaulted to `false`, since "unknown" and "inactive" are different states and collapsing them would misrepresent the data |
| date fields (not present in this CSV) | see [Date normalization](#date-normalization) below |

Rows missing a usable `hub_id` or `sorting_center` after cleaning are
dropped and logged to stderr with the row number, rather than silently
included with blank keys.

### Date normalization

`normalizeDate` accepts ISO (`YYYY-MM-DD`), slash (`MM/DD/YYYY`), and dash
(`DD-MM-YYYY`) formats, each with 1- or 2-digit month/day. Rather than
building a `java.time.format.DateTimeFormatter` per format, it extracts the
year/month/day digits with a capturing regex and constructs the date
directly via `LocalDate.of(year, month, day)`. That call throws on a
calendar-invalid date (e.g. `"2023-02-30"`), giving the same rejection
behavior without depending on `DateTimeFormatter`'s parsing-width rules for
variable-length numeric fields. Unrecognized shapes and placeholders both
return `null` rather than throwing, so one bad date can't fail the whole
ingestion run.

## Duplicate detection & resolution

The source data contains the same real-world hub listed multiple times
under different hub IDs — e.g. `H-500`, `H-504`, `H-510`, and `H-515` all
describe "Johannesburg Central" in "Gauteng", with conflicting `active`
values between them.

**Grouping key:** records are grouped by normalized `sorting_center`, not
`hub_id` or `province`. Hub IDs vary in casing/format across duplicates of
the same hub, so they can't be used to detect duplicates in the first
place. Province was ruled out too — one duplicate (`H-508`) has a blank
province while its sibling (`H-502`, same sorting center) has `Gauteng`, so
requiring province to match would have missed that pair. Sorting center is
the one field reliably shared by true duplicates in this dataset.

**Within each group:**

- **Canonical `hub_id`** — the member with the lowest trailing number wins
  (e.g. `H-500` over `H-504`/`H-510`/`H-515`). Arbitrary but deterministic,
  and reads naturally as "the original entry."
- **`province`** — taken from the canonical record if it has one;
  otherwise backfilled from the first non-null province among the other
  group members (handles the `H-508` case above).
- **`active`** — resolved by majority vote among non-placeholder values in
  the group. **Ties resolve to `true`.** Reasoning: wrongly marking a live
  hub inactive silently drops real capacity from downstream ETA
  calculations, whereas the reverse only costs an extra availability
  check — the tie-break favors the cheaper failure mode. If every member's
  value was a placeholder, the result is `null` (genuinely unknown) rather
  than a guessed default.
- **`mergedFrom`** — every other raw ID folded into the canonical record is
  kept in this list on the output record, so the merge is auditable rather
  than silent.

This collapses the 19 raw rows in `hubs-global.csv` down to 10 distinct
hubs.

## Project structure

```
ingestion-service/
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   ├── java/co/wethinkcode/logisticsconnect/
    │   │   ├── IngestionServiceApp.java   # bootstrap, cleaning, dedup, REST
    │   │   └── HubRecord.java             # cleaned hub record (REST response shape)
    │   └── resources/
    │       └── hubs-global.csv
    └── test/
        └── java/co/wethinkcode/logisticsconnect/
            └── IngestionServiceAppTest.java
```

## Build

```
mvn package
```

## Run

```
java -jar target/ingestion-service.jar
```

Listens on port 7050.

## API

| Method | Path | Returns |
|---|---|---|
| `GET` | `/health` | `OK` |
| `GET` | `/hubs` | JSON array of cleaned, deduplicated `HubRecord`s |

Example `/hubs` entry:

```json
{
  "hubId": "H-500",
  "province": "Gauteng",
  "sortingCenter": "Johannesburg Central",
  "active": true,
  "mergedFrom": ["H-504", "H-510", "H-515"]
}
```

## Test

```
mvn clean test
```

`IngestionServiceAppTest` covers field normalization
(`normalizeHubId`/`normalizeProvince`/`normalizeText`/`parseBoolean`/`normalizeDate`),
the `dedupe` logic in isolation, and an end-to-end check that loading and
cleaning the real `hubs-global.csv` produces the expected 10 hubs with the
expected merges. All 36 tests pass.

```
curl http://localhost:7050/health   # -> OK
curl http://localhost:7050/hubs     # -> cleaned JSON array
```
