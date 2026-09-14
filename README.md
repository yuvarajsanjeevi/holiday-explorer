# Holiday Explorer

Holiday Explorer is a Spring Boot service that provides insights on public holiday data using the
open API from [date.nager.at](https://date.nager.at/Api).

It answers three core queries:

| # | Question | Endpoint |
| --- | --- | --- |
| 1 | Given a country, what were the last 3 celebrated holidays? | `GET /api/v1/countries/{countryCode}/holidays/last` |
| 2 | Given a year and list of countries, how many public holidays fall on weekdays for each country? (sorted descending) | `GET /api/v1/holidays/weekday-counts` |
| 3 | Given a year and two countries, which dates do both celebrate? (deduplicated, with local names) | `GET /api/v1/holidays/shared` |

## Tech Stack

* **Java 21**
* **Spring Boot 4.1.1** (Spring Framework 7, Jackson 3)
* **Redis** (Data caching via Spring Cache)
---

## Why I Built It This Way

The main goal was to keep the business logic simple while still handling the practical problems that
show up in a real service:

* upstream failures
* retries
* caching
* concurrent requests
* deterministic results
* duplicate data
* regional holidays
* testability
* API contract consistency

The service layer depends on interfaces, the upstream client owns HTTP concerns, and the algorithms
themselves operate on simple domain objects. That makes the code reasonably easy to test and change
without pulling the whole application into every test.
---

## Design & Implementation Decisions

The upstream API payload has several edge cases that required explicit handling decisions:

* **Definition of "Last Celebrated":** Holidays must occur strictly prior to "today". A holiday
  occurring on the current calendar day is not considered already celebrated.
* **Timezone Basis:** "Today" is determined using the server's timezone. To avoid ambiguity near
  midnight, the API includes an `asOf` date in the response.
* **Country-Specific Weekends:** Weekend days vary globally (e.g., Egypt rests Friday and Saturday).
  Non-standard weekend schedules are handled in `WeekendCalendar`, defaulting to Saturday/Sunday for
  all other countries.
* **Holiday Types & Scope:**
  * Query 2 strictly filters for `Public` holidays.
  * Queries 1 and 3 count all celebrated days (including observances or school holidays, like Good
    Friday).
  * Bank-only days (like Denmark's *Banklukkedag* or US Columbus Day) are excluded from public
    holiday counts.
* **Regional Holidays:** To prevent federal countries from skewing counts (e.g., Switzerland listing
  cantonal variations of the same holiday), Query 2 counts only nationwide holidays. Duplicate
  cantonal entries on the same date are deduplicated.
* **Input Validation & Failures:**
  * Duplicate or mixed-case country inputs (like `NL,nl,NL`) are normalized and deduplicated before
    making upstream network calls.
  * If one country in a multi-country query fails, the entire request returns an error rather than
    partial results.
* **Lookbacks & Bounds:** Query 1 scans a maximum of 3 years backwards to avoid unbounded historical
  lookups for countries with few holidays. Valid year ranges are derived dynamically from the system
  clock to prevent hardcoded year boundaries from expiring over time.

## Trade-offs & What I'd Change

Every decision above cost something. These are the ones I'd expect to be asked about:

* **Redis rather than an in-process cache.** It is shared across instances and survives restarts,
  which matters because the upstream is a free API I don't want to hammer. The cost is one more
  moving part. A cache error handler stops Redis from being a hard dependency: if it is
  unreachable, requests fall through to the upstream API, so an outage costs latency and upstream
  load rather than availability.
* **One 24-hour TTL for everything.** Simple and good enough. Past years never change and could be
  cached far longer, while the current year occasionally gets corrected upstream and wants a
  shorter TTL. I kept one value rather than two cache configurations.
* **All-or-nothing for multi-country requests.** If one country fails, the whole request returns a
  `502` rather than partial results. Partial data would be more available, but a ranking with a
  country silently missing is quietly wrong, so I chose a clear failure. A per-country status
  would be the next step if clients preferred partial answers.
* **Virtual threads and a blocking `RestClient` instead of `WebClient`.** Same concurrency benefit
  for an I/O-bound fan-out, with plain code and normal stack traces. Requests are capped at 250
  countries, but on a cold cache that is still up to 250 concurrent calls to a free API, so I'd
  add a semaphore to limit concurrency separately from request size.
* **Two retries with exponential backoff, on `5xx` and I/O errors only.** A `4xx` won't succeed the
  second time, so it is never retried. There is no jitter; fine at this scale, but it would matter
  with many instances retrying at once.
* **Server timezone for "today".** Deterministic, and returned as `asOf` so the answer is never
  ambiguous. The cost is that near midnight a holiday already over in, say, New Zealand is not
  counted yet. Using each country's own timezone would be more correct but needs a
  country-to-timezone mapping.
* **Three-year lookback cap for query 1.** Keeps latency and upstream calls bounded. A country with
  very few holidays can return fewer than the requested limit.
* **Nationwide holidays only in query 2.** Stops federal countries like Switzerland being inflated
  by cantonal variants, at the cost of undercounting days some regions actually have off.
* **A stale country list over no country list.** A failed background refresh keeps serving the
  cached list: availability over freshness, which is the right call for data that almost never
  changes.

---

## Getting Started

### Prerequisites

* **JDK 21**
* **Docker** (Optional, used by Spring Boot Docker Compose support to manage Redis locally)
* Maven wrapper included (`./mvnw`)

### Running Locally

To run the application with automatic Redis container management:

```bash
./mvnw spring-boot:run

```
Spring Boot will automatically detect `docker-compose.yaml`, spin up a temporary Redis container,
bind the ports, and shut it down when the app stops. The service starts on `http://localhost:8080`.

To connect to an existing local Redis instance instead:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="\
  --spring.docker.compose.enabled=false \
  --spring.data.redis.host=localhost --spring.data.redis.port=6379"

```

### Running with Docker / Jib

The container image is built using Jib, which compiles layers directly without requiring a Docker
daemon or Dockerfile.

To build the image and run the full stack:

```bash
./mvnw jib:dockerBuild
docker compose --profile full up

```

The application container runs under an unprivileged user (UID 1000).

### Continuous Integration

`.github/workflows/build.yml` runs `./mvnw verify` on every push and pull request. GitHub runners
provide Docker, so the Testcontainers integration tests that skip on a machine without it do run
there. The JaCoCo report is uploaded as a build artifact, and the surefire reports too when the build
fails.

### Running Tests

Execute the full test suite:

```bash
./mvnw verify

```

The suite includes 157 tests covering unit logic, web layers, and integration workflows. JaCoCo
generates a code coverage report at `target/site/jacoco/index.html`.

Integration tests requiring a live Redis instance will automatically skip if Docker is not available
in the execution environment, keeping builds green.

---

## API Examples

Swagger documentation is available at `http://localhost:8080/swagger-ui.html`.

### 1. Last Celebrated Holidays

```bash
curl 'http://localhost:8080/api/v1/countries/NL/holidays/last'

```

**Response:**

```json
{
  "countryCode": "NL",
  "countryName": "Netherlands",
  "asOf": "2026-09-11",
  "holidays": [
    { "date": "2026-05-25", "localName": "Tweede Pinksterdag", "name": "Whit Monday" },
    { "date": "2026-05-24", "localName": "Eerste Pinksterdag", "name": "Pentecost" },
    { "date": "2026-05-14", "localName": "Hemelvaartsdag",     "name": "Ascension Day" }
  ]
}

```

Use `?limit=N` to override the default limit of 3.

### 2. Weekday Holiday Counts

```bash
curl 'http://localhost:8080/api/v1/holidays/weekday-counts?year=2026&countryCodes=NL,DE,US'

```

**Response:**

```json
[
  { "countryCode": "US", "countryName": "United States", "weekdayHolidayCount": 10 },
  { "countryCode": "DE", "countryName": "Germany",       "weekdayHolidayCount": 7 },
  { "countryCode": "NL", "countryName": "Netherlands",   "weekdayHolidayCount": 6 }
]

```

"Weekday" means a working day *in that country*, not Monday to Friday. Egypt rests Friday and
Saturday, so its working days are Sunday to Thursday - see `WeekendCalendar`.

### 3. Shared Holidays Between Two Countries

```bash
curl 'http://localhost:8080/api/v1/holidays/shared?year=2026&first=NL&second=DE'

```

**Response:**

```json
{
  "year": 2026,
  "countries": [
    { "countryCode": "NL", "countryName": "Netherlands" },
    { "countryCode": "DE", "countryName": "Germany" }
  ],
  "commonHolidays": [
    { "date": "2026-01-01", "localNames": { "NL": ["Nieuwjaarsdag"],   "DE": ["Neujahr"] } },
    { "date": "2026-12-26", "localNames": { "NL": ["Tweede Kerstdag"], "DE": ["Zweiter Weihnachtstag"] } }
  ]
}
```

The two codes are resolved to names once in `countries`, not repeated on every date - they are the
same for the whole response.

---

## Error Handling & Operations

### Error Responses

All API errors return a standard `application/json` payload containing structured details (`type`,
`title`, `status`, `detail`, `instance`):

```json
{
  "type": "https://example.com/holiday-explorer/errors/unknown-country",
  "title": "Unknown country",
  "status": 404,
  "detail": "Unknown country code(s): XX.",
  "instance": "/api/v1/countries/XX/holidays/last"
}

```

| HTTP Status | Trigger Condition |
| --- | --- |
| `400 Bad Request` | Invalid year, unknown parameters, `first == second`, or `limit < 1` |
| `404 Not Found` | Unknown country code or invalid route |
| `405 Method Not Allowed` | Invalid HTTP verb used on a valid route |
| `502 Bad Gateway` | Upstream API failure or network timeout |
| `500 Internal Server Error` | Unexpected internal exception |

### Actuator & OpenAPI Endpoints

* **Health Check:** `http://localhost:8080/actuator/health`
* **Metrics:** `http://localhost:8080/actuator/metrics`
* **Caches:** `http://localhost:8080/actuator/caches`
* **OpenAPI Spec:** `http://localhost:8080/v3/api-docs.yaml`

A copy of the OpenAPI spec is versioned at `src/main/resources/static/openapi.yaml`. An automated
integration test asserts that the live runtime spec matches the checked-in specification file.

### Logging

`INFO` is the default and stays quiet: the upstream base URL and retry settings, the cache names and
TTL, and each country-list refresh. A failed refresh logs `WARN` and keeps serving the cached list.

Turn on `DEBUG` to trace a request end to end:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--logging.level.com.example.holidays=DEBUG
```

Each query logs its parameters and its result, the concurrent fan-out logs how long it waited, and
the client logs every outbound call. That last one is the useful part: the client method is
`@Cacheable`, so a `GET /PublicHolidays/...` line only appears on a cache miss.

```
DEBUG c.e.h.service.impl.HolidayServiceImpl : Weekday holiday counts for [NL, DE, US, CH, EG] in 2026
DEBUG c.e.holidays.client.RestNagerDateClient : GET /PublicHolidays/2026/CH
DEBUG c.e.holidays.client.RestNagerDateClient : CH returned 33 holidays for 2026
DEBUG c.e.h.service.impl.HolidayServiceImpl : Fetched 5 country/countries for 2026 in 69 ms
```

The same request once the cache is warm logs no `GET` at all, and the fan-out drops to 4 ms.

---

## Core Algorithms & Data Flow

### Package Structure

```text
web/           HolidayController, ApiExceptionHandler  — REST Endpoints
web/mapper/    HolidayMapper                           — DTO Conversions
service/       HolidayService, CountryService          — Business Logic Interfaces
service/impl/  HolidayServiceImpl, CountryServiceImpl  — Algorithm Implementations
domain/        Holiday, CountryCode, WeekendCalendar   — Core Domain Models
client/        NagerDateClient + RestNagerDateClient   — Upstream API Integration
exception/     ErrorCode + Error Model Definitions
config/        Cache, HTTP Client, OpenAPI Configuration
health/        HolidayApiHealthIndicator               — Health Status Integration

```

### Algorithm Complexity

1. **Last N Celebrated Holidays:** Scans calendar years backwards starting from the current year.
   Once `limit` items are accumulated, processing breaks early, eliminating unnecessary upstream
   network calls. Time complexity is $O(n \log n)$ where $n$ is the number of holidays in the
   evaluated lookback years.
2. **Weekday Holiday Counts:** Upstream calls for multiple countries execute concurrently using
   **Java 21 Virtual Threads**. Overall request latency is bounded by the slowest individual country
   lookup rather than the sum of all lookups. Time complexity is $O(\sum n_i + m \log m)$ for $m$
   countries.
3. **Shared Dates:** Constructs hash maps grouped by date for both target countries, then calculates
   the key intersection. Time complexity is $O(n + m)$ compared to an $O(n \cdot m)$ nested loop
   approach.

### Caching Strategy

* **Upstream Data:** Public holiday responses are cached in Redis using `(year, countryCode)` keys
  with a 24-hour TTL.
* **Country Metadata Snapshot:** The global country list is warmed at application startup and
  refreshed every 6 hours via a background cron schedule using `@CachePut`. If the upstream provider
  goes down during a background refresh, the existing cached list remains active to prevent service
  disruptions.
* **Redis Outages:** A cache error handler logs Redis failures and lets the call proceed to the
  upstream API, so losing Redis degrades latency and upstream load, not availability.
* **Serialization Safety:** Jackson serialization uses explicit class bindings rather than default
  typing to ensure type safety and prevent deserialization vulnerabilities.
