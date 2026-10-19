# Dispatch Load Balancer

A Spring Boot REST service that assigns delivery orders to a fleet of vehicles. It respects each vehicle's
capacity, places high-priority orders first and keeps travel distance low. Distances use the Haversine formula.

- **REST API**: submit orders, submit vehicles, get the dispatch plan (plus reset endpoints)
- **Swappable algorithm**: dispatching is a `DispatchStrategy` (Strategy pattern). The default is a greedy nearest-vehicle heuristic.
- **Efficient persistence**: bulk upsert with a single ID lookup and JDBC batch inserts and updates
- **Consistent errors**: every failure returns the same JSON error body
- **Deployable**: Dockerfile and a Render Blueprint (`render.yaml`) with a managed PostgreSQL database
- **Tests**: 114 tests (unit, MockMvc slice and integration) with 100% line coverage, enforced by JaCoCo at 85%+

---

## Contents

1. [Tech stack](#tech-stack)
2. [Running the application](#running-the-application)
   - [Deploying to Render](#deploying-to-render)
3. [Running tests and viewing coverage](#running-tests-and-viewing-coverage)
4. [API reference](#api-reference)
5. [Error format](#error-format)
6. [Dispatch algorithm](#dispatch-algorithm)
7. [Design and architecture](#design-and-architecture)
8. [Assumptions and decisions](#assumptions-and-decisions)

---

## Tech stack

| Concern        | Choice                                                        |
|----------------|---------------------------------------------------------------|
| Language       | Java 17                                                       |
| Framework      | Spring Boot 3.5 (Web, Data JPA, Validation)                   |
| Database       | H2 in-memory (default), PostgreSQL (`postgres` profile)       |
| API docs       | springdoc-openapi 2.8 (Swagger UI)                            |
| Testing        | JUnit 5, Mockito, AssertJ, Spring Boot Test (MockMvc)         |
| Coverage       | JaCoCo (build fails below 85% line coverage)                  |
| Build          | Maven (wrapper included: `./mvnw`)                            |

---

## Running the application

**Prerequisite:** JDK 17 or newer. Maven is optional because the wrapper downloads it.

```bash
# Default profile: H2 in-memory database
./mvnw spring-boot:run            # Windows: mvnw.cmd spring-boot:run
# or, with Maven installed:
mvn spring-boot:run
```

The service starts on **http://localhost:8080**.

| URL                                     | Purpose                                       |
|-----------------------------------------|-----------------------------------------------|
| http://localhost:8080/swagger-ui.html   | Interactive API documentation (Swagger UI)    |
| http://localhost:8080/v3/api-docs       | OpenAPI 3 specification (JSON)                |
| http://localhost:8080/actuator/health  | Health check (used by Render)                 |
| http://localhost:8080/h2-console        | H2 console (JDBC URL `jdbc:h2:mem:dispatchdb`, user `sa`, no password) |

To build and run the executable JAR:

```bash
./mvnw clean package
java -jar target/dispatch-load-balancer-1.0.0.jar
```

### PostgreSQL profile

1. Create a database, for example with Docker:
   ```bash
   docker run -d --name dispatch-pg -p 5432:5432 \
     -e POSTGRES_DB=dispatch -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres postgres:16
   ```
2. Run with the `postgres` profile. Connection settings come from environment variables, with the defaults shown below:
   ```bash
   export DB_URL="jdbc:postgresql://localhost:5432/dispatch?reWriteBatchedInserts=true"
   export DB_USERNAME=postgres
   export DB_PASSWORD=postgres
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres
   # or: SPRING_PROFILES_ACTIVE=postgres java -jar target/dispatch-load-balancer-1.0.0.jar
   ```
   Hibernate creates the `orders` and `vehicles` tables on first start (`ddl-auto: update`).

### Deploying to Render

The repository includes everything Render needs:

| File            | Purpose |
|-----------------|---------|
| `Dockerfile`    | Multi-stage build: Maven builds the jar, then a slim JRE 17 image runs it as a non-root user. The heap is sized from the container memory limit. |
| `.dockerignore` | Keeps `target/`, IDE files and similar out of the build context |
| `render.yaml`   | Render Blueprint: a Docker **web service** plus a **PostgreSQL** database, wired together |

How the app adapts to Render:

- It binds to the `PORT` environment variable that Render sets (`server.port: ${PORT:8080}`).
- `SPRING_PROFILES_ACTIVE=postgres` switches from H2 to PostgreSQL, so data survives restarts and redeploys.
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME` and `DB_PASSWORD` are injected from the Render database by `render.yaml`. `application-postgres.yml` builds the JDBC URL from these values. You can also set a full `DB_URL` to override it.
- `/actuator/health` is the health check, and it includes database connectivity.
- `forward-headers-strategy: framework` makes Swagger UI use the public `https://` URL behind Render's proxy.

**Steps**

1. Push this project to its own GitHub or GitLab repository, with `pom.xml`, `Dockerfile` and `render.yaml` at the repository root.
2. In the Render dashboard, choose **New → Blueprint**, select the repository and click **Apply**. Render creates `dispatch-db` and `dispatch-load-balancer`, then builds the Docker image.
3. When the deploy is live, open `https://<your-service>.onrender.com/swagger-ui.html`. For Postman, set the collection variable `baseUrl` to `https://<your-service>.onrender.com`.

To use the in-memory H2 database instead, with no Postgres, delete the `databases` section and the `SPRING_PROFILES_ACTIVE`/`DB_*` variables from `render.yaml`. Data is then lost whenever the service restarts.

**Free-tier caveats (check Render's current pricing page):** free web services sleep after a period of
inactivity, so the first request after idle takes about a minute while the JVM starts. Free PostgreSQL
databases are time-limited. The Docker build skips tests to keep deploys fast. Run `./mvnw clean verify`
locally or in CI before pushing.

To test the image locally:

```bash
docker build -t dispatch-load-balancer .
docker run -p 8080:8080 dispatch-load-balancer      # H2 profile
```

---

## Running tests and viewing coverage

```bash
./mvnw clean verify
```

This compiles the code, runs all tests, writes the JaCoCo report and **fails the build if line coverage is below 85%**.

- Coverage report: `target/site/jacoco/index.html` (open it in a browser)
- Test reports: `target/surefire-reports/`

Current result: **114 tests, 0 failures. Line coverage 100%, branch coverage 93.9%.**

| Test class                           | Type             | What it covers |
|--------------------------------------|------------------|----------------|
| `HaversineDistanceCalculatorTest`    | Unit             | Same point = 0, known city pairs (Delhi–Noida, Bangalore–Chennai ≈ 290 km, London–Paris), symmetry, pole to pole, antimeridian crossing, antipodes (no NaN) |
| `GreedyNearestVehicleStrategyTest`   | Unit             | HIGH before LOW under tight capacity, capacity never exceeded, exact-capacity fit (including floating-point weights), nearest vehicle chosen, tie-break by vehicleId, distance accumulated leg by leg, vehicle position moves, order heavier than every vehicle, empty orders/vehicles, determinism, inputs not modified |
| `SampleDataDispatchTest`             | Unit             | The 30 orders and 5 vehicles from the PDF: no vehicle over capacity, every order appears exactly once, all HIGH orders assigned, stops in non-increasing priority on every route, totals consistent with legs and weights |
| `OrderServiceImplTest`, `VehicleServiceImplTest` | Mockito | Insert vs. update (upsert), duplicate-ID detection before any DB access, chunked ID lookup, single `saveAll`, bulk delete |
| `DispatchServiceImplTest`            | Mockito          | Delegation to the strategy, response mapping, edge-case messages |
| `*ControllerTest`                    | `@WebMvcTest`    | Every endpoint: success, each validation rule, missing fields, empty list, null element, malformed JSON, invalid enum, wrong type, duplicates, 404, 405, 415, 500 without leaked details |
| `DispatchFlowIntegrationTest`        | `@SpringBootTest`| POST orders → POST vehicles → GET plan against H2. Also: upsert does not duplicate rows, rejected duplicates save nothing, empty plan, OpenAPI docs served |
| `UpsertBatchingIntegrationTest`      | `@SpringBootTest`| Uses Hibernate statistics to show that upserting 250 orders takes at most 4 JDBC statements (1 lookup plus batches), not 250 |

The sample data lives in `src/test/resources/samples/` and is shared by the tests, the curl examples below and the Postman collection.

---

## API reference

Base path: `/api/dispatch`. All bodies are JSON.

| Method   | Path                       | Description                                  | Success          |
|----------|----------------------------|----------------------------------------------|------------------|
| `POST`   | `/api/dispatch/orders`     | Submit delivery orders (upsert by `orderId`) | `200 OK`         |
| `POST`   | `/api/dispatch/vehicles`   | Submit fleet details (upsert by `vehicleId`) | `200 OK`         |
| `GET`    | `/api/dispatch/plan`       | Compute the dispatch plan                    | `200 OK`         |
| `DELETE` | `/api/dispatch/orders`     | Delete all orders (optional extra)           | `204 No Content` |
| `DELETE` | `/api/dispatch/vehicles`   | Delete all vehicles (optional extra)         | `204 No Content` |

### Postman

Import `postman/DispatchLoadBalancer.postman_collection.json`. The `baseUrl` variable defaults to `http://localhost:8080`. It has three folders:

1. **Sample data flow**: reset, then submit the 30 PDF orders and the 5 PDF vehicles, then get the plan. Each request has test assertions.
2. **Specification example**: the Bangalore/Chennai example from the API specification.
3. **Error handling**: validation errors, missing fields, invalid priority, malformed JSON, empty list, duplicate IDs and an order heavier than any vehicle.

### 1. Submit delivery orders

`POST /api/dispatch/orders`

| Field           | Type   | Rules                               |
|-----------------|--------|-------------------------------------|
| `orderId`       | string | required, non-blank, ≤ 64 chars, unique within the request |
| `latitude`      | number | required, −90 to 90                 |
| `longitude`     | number | required, −180 to 180               |
| `address`       | string | required, non-blank, ≤ 255 chars    |
| `packageWeight` | number | required, > 0                       |
| `priority`      | enum   | required, `HIGH`, `MEDIUM` or `LOW` (case-insensitive) |

```bash
curl -X POST http://localhost:8080/api/dispatch/orders \
  -H "Content-Type: application/json" \
  -d '{
        "orders": [
          { "orderId": "ORD001", "latitude": 12.9716, "longitude": 77.5946,
            "address": "MG Road, Bangalore, Karnataka, India", "packageWeight": 10, "priority": "HIGH" },
          { "orderId": "ORD002", "latitude": 13.0827, "longitude": 80.2707,
            "address": "Anna Salai, Chennai, Tamil Nadu, India", "packageWeight": 20, "priority": "MEDIUM" }
        ]
      }'

# or the 30 sample orders from the PDF:
curl -X POST http://localhost:8080/api/dispatch/orders \
  -H "Content-Type: application/json" -d @src/test/resources/samples/orders-request.json
```

```json
{ "message": "Delivery orders accepted.", "status": "success" }
```

Posting an `orderId` that already exists **updates** that order instead of creating a duplicate.

### 2. Submit fleet details

`POST /api/dispatch/vehicles`

| Field              | Type   | Rules                               |
|--------------------|--------|-------------------------------------|
| `vehicleId`        | string | required, non-blank, ≤ 64 chars, unique within the request |
| `capacity`         | number | required, > 0                       |
| `currentLatitude`  | number | required, −90 to 90                 |
| `currentLongitude` | number | required, −180 to 180               |
| `currentAddress`   | string | required, non-blank, ≤ 255 chars    |

```bash
curl -X POST http://localhost:8080/api/dispatch/vehicles \
  -H "Content-Type: application/json" -d @src/test/resources/samples/vehicles-request.json
```

```json
{ "message": "Vehicle details accepted.", "status": "success" }
```

### 3. Get the dispatch plan

`GET /api/dispatch/plan`

```bash
curl http://localhost:8080/api/dispatch/plan
```

Actual response for the PDF sample data (abridged):

```json
{
  "dispatchPlan": [
    {
      "vehicleId": "VEH001",
      "totalLoad": 100,
      "totalDistance": "14.44 km",
      "assignedOrders": [
        { "orderId": "ORD026", "latitude": 28.7041, "longitude": 77.1025,
          "address": "Karol Bagh, Delhi, India", "packageWeight": 25, "priority": "HIGH" },
        "... ORD007, ORD011, ORD015 (Karol Bagh), then ORD001 (Connaught Place) ..."
      ]
    },
    { "vehicleId": "VEH002", "totalLoad": 80,  "totalDistance": "0.00 km",  "assignedOrders": ["ORD020, ORD023, ORD027"] },
    { "vehicleId": "VEH003", "totalLoad": 115, "totalDistance": "24.74 km", "assignedOrders": ["ORD013, ORD005, ORD009, ORD017, ORD010"] },
    { "vehicleId": "VEH004", "totalLoad": 90,  "totalDistance": "19.80 km", "assignedOrders": ["ORD029, ORD004, ORD008, ORD012"] },
    { "vehicleId": "VEH005", "totalLoad": 105, "totalDistance": "14.44 km", "assignedOrders": ["ORD018, ORD021, ORD025, ORD002, ORD006"] }
  ],
  "unassignedOrders": [
    { "orderId": "ORD016", "reason": "No vehicle has enough remaining capacity for package weight 30" },
    { "orderId": "ORD003", "reason": "No vehicle has enough remaining capacity for package weight 20" },
    "... ORD019, ORD022, ORD030, ORD014, ORD024, ORD028 ..."
  ],
  "message": "8 order(s) could not be assigned. See unassignedOrders for details."
}
```

The sample orders weigh 645 in total, but the fleet can carry only 500. All 12 HIGH orders (275) are
assigned. The 8 unassigned orders are LOW orders that no longer fit anywhere.

**Response fields**

| Field                               | Description |
|-------------------------------------|-------------|
| `dispatchPlan[]`                    | One entry per vehicle, sorted by `vehicleId`. Vehicles with no orders are included with `totalLoad: 0` and `"0.00 km"`. |
| `dispatchPlan[].totalLoad`          | Sum of assigned package weights |
| `dispatchPlan[].totalDistance`      | Route length start → stop 1 → … → stop n, formatted `"X.XX km"` |
| `dispatchPlan[].assignedOrders[]`   | Full order objects, including `address`, **in delivery (stop) order** |
| `unassignedOrders[]`                | Extra field: orders that could not be placed, each with a `reason` |
| `message`                           | Extra field: only present for edge cases (no data, no orders, no vehicles, unassigned orders) |

Apart from the two extra fields, the response matches the shape in the specification.

**Edge cases.** `GET /plan` always returns `200 OK`, even with no orders or no vehicles. An empty plan is a
valid answer, not an error, so the `message` field explains the situation:

| Situation                 | `dispatchPlan`         | `unassignedOrders`               | `message` |
|---------------------------|------------------------|----------------------------------|-----------|
| No orders and no vehicles | `[]`                   | `[]`                             | `No orders or vehicles available. Add orders and vehicles to generate a plan.` |
| No orders                 | every vehicle, idle    | `[]`                             | `No orders available to dispatch.` |
| No vehicles               | `[]`                   | every order, `No vehicles available` | `No vehicles available. All orders are unassigned.` |
| Order heavier than every vehicle | …               | `Package weight 500 exceeds the capacity of every vehicle (largest capacity: 120)` | `N order(s) could not be assigned. …` |

### 4. Reset data

```bash
curl -X DELETE http://localhost:8080/api/dispatch/orders     # 204 No Content
curl -X DELETE http://localhost:8080/api/dispatch/vehicles   # 204 No Content
```

---

## Error format

Every error response has the same body:

```json
{
  "status": "error",
  "message": "Validation failed",
  "errors": [
    { "field": "orders[0].latitude",      "message": "latitude must be between -90 and 90" },
    { "field": "orders[0].packageWeight", "message": "packageWeight must be greater than 0" }
  ],
  "timestamp": "2026-10-19T10:55:22.699Z"
}
```

| Situation                                          | Status | `message`                         | `errors` |
|----------------------------------------------------|--------|-----------------------------------|----------|
| Bean Validation failure (range, required, > 0, empty list, null element) | 400 | `Validation failed` | one entry per field, with the path, e.g. `orders[3].priority` |
| Invalid enum, e.g. `"priority": "URGENT"`          | 400    | `Invalid value in request body`   | `Invalid value 'URGENT'. Allowed values: [HIGH, MEDIUM, LOW]` |
| Wrong type, e.g. `"latitude": "north"`             | 400    | `Invalid value in request body`   | `Invalid value 'north'; expected Double` |
| Malformed or missing JSON body                     | 400    | `Malformed JSON request`          | `[]` |
| Same ID twice in one request                       | 400    | `Duplicate IDs in request`        | `'ORD001' appears more than once in the request` |
| Unknown path                                       | 404    | `Resource not found: /...`        | `[]` |
| Wrong HTTP method                                  | 405    | `HTTP method GET is not supported for this endpoint` | `[]` |
| Non-JSON `Content-Type`                            | 415    | `Content-Type must be application/json` | `[]` |
| Any unexpected server error                        | 500    | `An unexpected error occurred. Please try again later.` | `[]` (details are logged, never returned) |

A request rejected for duplicate IDs saves nothing.

---

## Dispatch algorithm

Implemented in `GreedyNearestVehicleStrategy`, behind the `DispatchStrategy` interface.

```
sort orders by: priority (HIGH → MEDIUM → LOW), then packageWeight descending, then orderId
for each vehicle: remaining = capacity, position = current location, distance = 0

for each order (in sorted order):
    candidates = vehicles with remaining ≥ order.weight
    if none: mark unassigned with a reason (no vehicles / heavier than every vehicle / no space left)
    else:
        v = candidate with the smallest Haversine(v.position, order.location)   // ties → lowest vehicleId
        v.distance += that leg; v.remaining -= weight; v.position = order.location; append order to v's stops
```

**How it meets the requirements**

| Requirement                    | How |
|--------------------------------|-----|
| Load ≤ capacity                | A vehicle is a candidate only if its *remaining* capacity fits the order. A 1e-9 tolerance absorbs floating-point error, so 0.1 + 0.2 fits a capacity of 0.3. |
| High priority first            | Orders are processed strictly by priority, so a LOW order can never take space a HIGH order needs. |
| Minimise distance              | Each order goes to the vehicle whose *current* position (its last drop, or its depot) is nearest. Vehicles naturally build compact routes, and an order at a vehicle's location adds 0 km. |
| Haversine                      | `HaversineDistanceCalculator` (R = 6371 km), behind a `DistanceCalculator` interface. |
| Heaviest first within a priority | First-fit-decreasing: placing big packages while space is plentiful packs bins noticeably better than arbitrary order. |

**Complexity.** O(n log n + n·m) time and O(n + m) memory for n orders and m vehicles. The plan uses exactly two
database queries (`findAll` on each table). The algorithm runs fully in memory with no database access inside loops.
For example, 100,000 orders × 100 vehicles is 10⁷ cheap distance evaluations, which takes well under a second.

**Trade-offs.** Assigning orders to capacitated vehicles while minimising route length is the
Capacitated Vehicle Routing Problem, which is NP-hard. This greedy heuristic is fast, deterministic and easy to
reason about, but **it is not globally optimal**:

- Each choice is local. The nearest vehicle now may not be the best choice for the overall fleet distance.
- Stops are visited in assignment (priority) order. There is no reordering into the shortest tour (no 2-opt or TSP step).
- Priority is strict. A single heavy HIGH order is placed before many light LOW orders, even if that leaves less total weight delivered.
- Distances are straight-line (great-circle), not road distances.

Because the algorithm is a Strategy, a better optimiser (Clarke–Wright savings, OR-Tools or simulated annealing)
or a road-distance `DistanceCalculator` can replace it without changing the controllers, services or tests of other layers.

---

## Design and architecture

```
com.dispatch
├── controller   REST endpoints only: validate input, delegate, shape the HTTP response
├── dto          Java records for requests and responses, with Bean Validation annotations
├── entity       JPA entities Order and Vehicle, plus the Priority enum
├── repository   Spring Data JPA repositories
├── service      OrderService, VehicleService, DispatchService interfaces
│   └── impl     Implementations plus UpsertSupport (shared bulk-upsert logic)
├── strategy     DispatchStrategy, GreedyNearestVehicleStrategy and result types
├── util         DistanceCalculator, HaversineDistanceCalculator
├── mapper       Entity ↔ DTO conversion
├── exception    DuplicateIdException, GlobalExceptionHandler
└── config       OpenAPI metadata, Jackson module (writes 10 rather than 10.0)

Dockerfile, render.yaml   Container image and Render Blueprint
```

Request flow: `Controller → Service (interface) → Repository / Strategy → Mapper → DTO`.

**Design patterns and principles**

| Pattern / principle            | Where |
|--------------------------------|-------|
| **Strategy**                   | `DispatchStrategy` (algorithm) and `DistanceCalculator` (metric) are interfaces injected by Spring. Both are swappable and testable in isolation. |
| **Layered architecture**       | Controllers have no business logic. Services own transactions and rules. Repositories own persistence. |
| **Dependency inversion**       | Controllers depend on service interfaces, and the service depends on `DispatchStrategy`, not a concrete class. Constructor injection is used throughout. |
| **DTO + Mapper**               | The API contract (records) is decoupled from the persistence model (entities). Mappers are the only place that converts between them. |
| **Global exception handling**  | `@RestControllerAdvice` turns every exception into the same `ErrorResponse`. |
| **Template / shared helper**   | `UpsertSupport.prepareUpsert` contains the generic upsert workflow. Order and vehicle services supply ID extractors and mapping functions. |
| **Immutability**               | DTOs and strategy results are records with defensive `List.copyOf`. The strategy keeps mutable planning state private to a single call. |

**Database efficiency**

- **Upsert without N+1 queries.** Entities have client-assigned IDs and implement `Persistable`, so Spring Data does not `SELECT` before each save. The service loads existing rows with one `findAllById` query, chunked at 1,000 IDs. It updates the loaded entities (dirty checking emits batched `UPDATE`s) and creates the rest, then makes a single `saveAll` call (batched `INSERT`s).
- **JDBC batching.** `hibernate.jdbc.batch_size=100`, with `order_inserts` and `order_updates` enabled. On PostgreSQL, `reWriteBatchedInserts=true` turns each batch into multi-row INSERTs. `UpsertBatchingIntegrationTest` checks this.
- **Bulk delete.** `deleteAllInBatch()` issues a single `DELETE` statement.
- **Read-only plan transaction.** `@Transactional(readOnly = true)` with two queries. `open-in-view` is disabled.

---

## Assumptions and decisions

- **Units.** Weights and capacities share one unspecified unit (e.g. kg). Distances are in km.
- **Upsert semantics.** `POST` replaces all fields of an existing order or vehicle with the same ID. The operation is idempotent and returns `200 OK` (not `201 Created`) because a batch is accepted and no single new resource gets a `Location`.
- **Duplicate IDs in one request** are rejected with `400` instead of letting the last one win silently.
- **Plan is computed on demand** from the current orders and vehicles and is not stored, so it can never be stale. Calling `GET /plan` twice with the same data returns the same plan, because the algorithm is deterministic.
- **Empty data returns `200 OK`** with an explanatory `message`, not `404`. See [Edge cases](#3-get-the-dispatch-plan).
- **Route model.** Each vehicle starts at its current location and drives to its stops in the order they were assigned. There is no return-to-depot leg.
- **Display formatting.** `totalDistance` is rounded to 2 decimals as `"X.XX km"`. `totalLoad` is rounded to 2 decimals. Whole numbers are written as `10`, not `10.0`, as in the specification.
- **Priority input** is case-insensitive (`"high"` is accepted). Unknown JSON properties are ignored.
- **No authentication**, because the assignment does not require it.
#   d i s p a t c h - l o a d - b a l a n c e r  
 