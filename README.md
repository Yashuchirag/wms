# Warehouse Management System (WMS)

A comprehensive warehouse management system built with Spring Boot 4 and Java 25.

## Features

- 📦 Real-time inventory tracking across multiple warehouses
- 🏢 Warehouse and zone management
- 🗄️ Storage locations (bins) per zone, with receiving and packing locations
- 🔒 Concurrency-safe stock reservations that cannot oversell
- 📒 Inventory ledger: every stock change is recorded and can be reconciled against balances
- 🧾 Outbound orders from an order management system, with idempotent intake and stock allocation
- 📊 Item catalog management with turnover rates
- 🚚 Shipment tracking (inbound/outbound) with enforced status transitions
- 🔄 Automated inventory reconciliation
- 📈 Low stock alerts and reorder points
- 🎯 Zone-based storage optimization
- 📝 Complete REST API with Swagger documentation

## Technology Stack

- **Java**: 25
- **Spring Boot**: 4.1.2
- **Database**: PostgreSQL 16
- **Migration**: Flyway 12.4.0
- **Build Tool**: Maven
- **Documentation**: Swagger/OpenAPI 3 (springdoc-openapi 3.1.1)
- **Utilities**: Lombok 1.18.48, ModelMapper 3.2.8
- **Containerization**: Docker & Docker Compose

## Prerequisites

- Java 25 or higher
- Maven 3.9+
- PostgreSQL 16+ (or use Docker)
- Docker & Docker Compose (optional)

## Getting Started

### Option 1: Using Docker Compose (Recommended)

1. Clone the repository:
```bash
git clone https://github.com/Yashuchirag/wms.git
cd wms
```

2. Create your local `.env` and set real passwords for `DB_PASSWORD` and `PGADMIN_PASSWORD`:
```bash
cp .env.example .env
```
`.env` is gitignored. Never commit it. Compose refuses to start if either password is missing.

3. Start all services:
```bash
docker compose up -d
```

4. Access the application:
    - API: http://localhost:8080
    - Swagger UI: http://localhost:8080/swagger-ui.html
    - PgAdmin: http://localhost:5050 (log in with `PGADMIN_EMAIL` / `PGADMIN_PASSWORD` from `.env`)

All ports are bound to `127.0.0.1`, so the services are only reachable from your own machine.

### Option 2: Local Development

1. Install PostgreSQL and create the database:
```sql
CREATE DATABASE wms;
```

2. Copy `.env.example` to `.env` and set `DB_PASSWORD` (and `DB_URL` / `DB_USERNAME` if they differ) to match your local `postgres` user. The `dev` profile reads `.env` from the directory you start the app in, and real environment variables take precedence. Flyway uses the same connection. If the password is missing, startup fails with "password authentication failed".

3. Build and run:
```bash
./mvnw clean install
./mvnw spring-boot:run
```

## API Documentation

Once the application is running, access the Swagger UI at:
```
http://localhost:8080/swagger-ui.html
```

### Main API Endpoints

#### Warehouse Management
- `GET /api/v1/warehouses` - List all warehouses
- `POST /api/v1/warehouses` - Create new warehouse
- `GET /api/v1/warehouses/{id}` - Get warehouse by ID
- `PUT /api/v1/warehouses/{id}` - Update warehouse
- `DELETE /api/v1/warehouses/{id}` - Delete warehouse

#### Inventory Management
- `GET /api/v1/inventory` - List all inventory
- `POST /api/v1/inventory` - Create inventory record
- `GET /api/v1/inventory/warehouse/{id}` - Get inventory by warehouse
- `PATCH /api/v1/inventory/{id}/adjust` - Adjust stock levels
- `PATCH /api/v1/inventory/{id}/reserve` - Reserve stock
- `GET /api/v1/inventory/low-stock` - Get low stock items
- `GET /api/v1/inventory/reconciliation`: List warehouse, item and location groups whose ledger totals differ from their inventory (an empty list means everything balances)

`adjust` and `reserve` accept an optional `Idempotency-Key` header. A repeated key changes nothing and returns 200 with the current state of the record. A request that fails does not use up its key.

#### Item Management
- `GET /api/v1/items` - List all items
- `POST /api/v1/items` - Create new item
- `GET /api/v1/items/sku/{sku}` - Get item by SKU
- `GET /api/v1/items/high-demand` - Get high-demand items

#### Shipment Management
- `GET /api/v1/shipments` - List all shipments
- `POST /api/v1/shipments` - Create shipment
- `GET /api/v1/shipments/tracking/{trackingNumber}` - Track shipment
- `POST /api/v1/shipments/{id}/process` - Process shipment
- `PATCH /api/v1/shipments/{id}/status` - Update shipment status

#### Order Management
- `POST /api/v1/orders`: Receive an order (201). Sending the same `externalOrderId` for the same warehouse again returns the existing order with 200, even when two copies arrive at the same time
- `GET /api/v1/orders?status=`: List orders, optionally filtered by status
- `GET /api/v1/orders/{id}`: Get an order with its lines and allocations
- `POST /api/v1/orders/{id}/allocate`: Reserve stock for whatever each line still needs
- `POST /api/v1/orders/{id}/release`: Release an allocated order to the floor, creating one pick task per allocation
- `POST /api/v1/orders/{id}/cancel`: Cancel an order before picking starts, release its allocations and cancel its open tasks

An order identifies its warehouse by code and its items by SKU:

```json
{"externalOrderId": "OMS-1001", "warehouseCode": "WH-001", "shipBy": "2026-11-20T17:00:00Z",
 "carrier": "UPS", "serviceLevel": "GROUND", "priority": 5,
 "shipTo": {"name": "Jane Smith", "address": {"line1": "1 Main St", "city": "New York", "zip": "10001"}},
 "lines": [{"lineNo": 1, "sku": "SKU-001", "qty": 2}]}
```

An unknown warehouse code or SKU, or a repeated line number, returns 400. `priority` is a number; higher is more urgent and the default is 0.

#### Task Management
- `POST /api/v1/tasks/next?zoneId=`: Claim the most urgent READY pick task, optionally in one zone (204 if there is none)
- `GET /api/v1/tasks/{id}`: Get a task
- `POST /api/v1/tasks/{id}/confirm`: Confirm a claimed pick with the scanned location and the quantity picked

There is no authentication yet, so both POST endpoints need an `X-User-Id` header naming the picker, and only the user who claimed a task can confirm it. A confirmation looks like this:

```json
{"locationCode": "Z-A1-01", "qty": 5}
```

The scanned code must match the task's location. Stock that has no location has nothing to scan, so its tasks skip that check. A confirmation can carry an `Idempotency-Key` header; repeating it returns the task unchanged.

#### Location Management
- `GET /api/v1/locations?zoneId=&type=`: List locations, optionally filtered by zone and type (both filters are optional)
- `POST /api/v1/locations`: Create a location
- `GET /api/v1/locations/{id}`: Get a location by ID
- `PUT /api/v1/locations/{id}`: Update a location (it cannot move to another warehouse)
- `DELETE /api/v1/locations/{id}`: Delete a location that holds no stock

Location types are `RECEIVING`, `PICK_FACE`, `RESERVE`, `STAGING`, `PACK` and `DOCK`. A location code is unique within its warehouse, and a location's zone must belong to the same warehouse. To retire a bin without deleting it, set `isActive` to `false`.

### Error Responses

| Status | When |
|---|---|
| 400 | Request validation fails (field errors are listed under `errors`), or a request is invalid, such as a zone from another warehouse, a scanned location that does not match the task, or a missing `X-User-Id` header |
| 404 | The requested record does not exist |
| 409 | Not enough available stock to reserve or adjust, an illegal shipment, order or task status change, receiving into a warehouse that has no receiving location, releasing in a warehouse that has no pack location, confirming a task someone else claimed, a record modified concurrently by another request, or a database constraint violation (a duplicate code, deleting an item that still has inventory, deleting a zone that still has locations) |

## Database Schema

The system uses PostgreSQL with Flyway migrations. Key tables:

- **warehouses**: Warehouse locations and details
- **zones**: Storage zones within warehouses
- **items**: Product catalog
- **inventory**: Stock levels per warehouse/zone
- **shipments**: Inbound and outbound shipments
- **locations**: Bins within a warehouse, optionally inside a zone, with a type, pick sequence and capacity limits
- **lpns**: License plates (pallets, cases, totes, cartons) that can sit in a location and nest inside each other
- **inventory_txn**: The append-only inventory ledger, partitioned by month. Each row records one stock movement (receipt, move, allocate, deallocate, pick, ship or adjustment) with its quantity, locations and the reference that caused it
- **processed_requests**: Idempotency keys that have already been handled
- **outbound_orders** and **outbound_lines**: Orders received from the order management system, one row per line with ordered, allocated, picked and shipped quantities
- **allocations**: Which inventory row each order line's stock is reserved on, and whether that reservation is open, picked or cancelled
- **outbox_events**: Events waiting to be published (or recently published), written in the same transaction as the change they describe
- **tasks**: Work for the floor (pick, count, and later put-away, replenishment and moves), with its status, priority, locations, the order it belongs to and who claimed it

Integrity rules enforced by the database:

- `reserved_quantity` can never exceed `quantity` on an inventory row.
- An item appears at most once per warehouse, zone and location, and missing values count as one value (`UNIQUE NULLS NOT DISTINCT`).
- An item that still has inventory cannot be deleted, and neither can a zone that still has locations or a location that still holds inventory or LPNs.
- Ledger quantities are always positive; the transaction type gives the direction. The ledger has no foreign keys, so its history outlives the items and locations it mentions.
- `inventory.location_id` is set for stock that the ledger places in a location, such as received stock. Neither it nor `inventory.lpn_id` is exposed through the API yet.

Migrations live in `src/main/resources/db/migration` (V1 to V14). Applied migrations are never edited; every schema change is a new `V<n>__*.sql` file.

## Configuration

### Profiles

- `dev`: Development environment (local PostgreSQL)
- `prod`: Production environment (uses environment variables)

### Environment Variables (Production)

```bash
DB_URL=jdbc:postgresql://localhost:5432/wms_prod
DB_USERNAME=your_username
DB_PASSWORD=your_password
SPRING_PROFILES_ACTIVE=prod
```

### Event Relay

| Property | Default | Meaning |
|---|---|---|
| `wms.outbox.relay.enabled` | `true` | Run the outbox relay and cleanup on a schedule (the `itest` profile turns this off) |
| `wms.outbox.relay.poll-interval` | `1000` | Milliseconds between relay runs |
| `wms.outbox.cleanup.cron` | `0 0 3 * * *` | When to delete events published more than 7 days ago |

## Monitoring

Health check endpoint:
```
http://localhost:8080/actuator/health
```

Metrics:
```
http://localhost:8080/actuator/metrics
```

## Testing

Run tests:
```bash
./mvnw test
```

Integration tests start a throwaway PostgreSQL 16 container through Testcontainers, so Docker must be running. No local database is needed.

The suite includes an HTTP contract test that pins the existing API responses, a migration test for every Flyway version, and a concurrency test that sends 50 simultaneous reservations against one inventory row.

Run with coverage:
```bash
./mvnw clean test jacoco:report
```

## Project Structure

```
src/
├── main/
│   ├── java/com/warehouse/wms/
│   │   ├── config/          # Configuration classes
│   │   ├── controller/      # REST controllers
│   │   ├── dto/             # Data Transfer Objects
│   │   ├── exception/       # Exception handling
│   │   ├── model/           # JPA entities
│   │   ├── repository/      # Data access layer
│   │   └── service/         # Business logic
│   └── resources/
│       ├── application.yml  # Main configuration
│       ├── application-dev.yml
│       ├── application-prod.yml
│       └── db/migration/    # Flyway SQL scripts
└── test/                    # Unit and integration tests
```

## Performance Optimizations

- ✅ Database connection pooling (HikariCP)
- ✅ JPA batch processing
- ✅ Caching with Spring Cache
- ✅ Database indexes on frequently queried columns
- ✅ Pagination support
- ✅ Async processing capabilities

## Business Features Implemented

### Inventory Tracking
- Real-time stock updates
- Multi-warehouse support
- Zone-based storage
- Reserved quantity tracking
- Low stock alerts
- Reserve and adjust lock the inventory row (`SELECT ... FOR UPDATE`), so concurrent requests cannot lose updates or oversell. Other writes are protected by an optimistic `@Version` check.
- Every quantity change goes through one ledger service, which updates the balance and writes one ledger row in the same transaction. That includes creating, editing and deleting inventory records, which write `INITIAL`, `MANUAL_UPDATE` and `RECORD_DELETED` adjustments.
- Existing stock was given opening-balance ledger rows, so the ledger reconciles from the start.

### Storage Locations
- Three seeded bins per seeded zone (reserve bins in the bulk zone, pick faces elsewhere)
- `RCV-01` (receiving) and `PACK-01` (packing) in WH-001, outside any zone
- Pick sequence per bin, for ordering pick paths later

### Warehouse Zoning Strategy
- HIGH_DEMAND zones for fast-moving items
- MEDIUM_DEMAND for regular items
- BULK storage for large quantities
- FRAGILE zones for sensitive items

### Shipment Management
- Inbound/outbound shipments
- Tracking number generation
- Status tracking: outbound Pending → Processing → Shipped → Delivered; inbound Pending → Processing → Received; either type can go from Pending or Processing to Cancelled
- Processing an outbound shipment reserves its stock. Only `POST /api/v1/shipments/{id}/process` moves a shipment from PENDING to PROCESSING, so the reservation cannot be skipped, and processing the same shipment twice returns 409.
- Any other illegal status change returns 409.
- Moving an outbound shipment to SHIPPED removes the reserved stock from the warehouse, and cancelling a PROCESSING outbound shipment releases its reservation.
- Moving an inbound shipment to RECEIVED adds its quantity at the warehouse's receiving location (`RCV-01`).

### Outbound Orders
- Order states: Created → Allocated → Released → Picking → Picked → Packed → Shipped, plus Short and Cancelled. One state machine rejects every other move with 409. Release and picking use Released, Picking and Picked; Packed and Shipped are not reached yet.
- Allocation takes stock from pick faces first, then reserve bins (by pick sequence), then stock that has no location. It never takes stock from receiving, staging, packing or dock locations, or from inactive bins.
- Each allocation locks one inventory row at a time with `FOR UPDATE SKIP LOCKED`, so concurrent allocations never over-allocate and never wait on each other. The cost is that an allocation can pass over stock another allocation is holding at that moment and come out Short.
- A Short order keeps what it got and can be allocated again to fill the rest. It becomes Allocated once every line is covered.
- Every allocation writes an ALLOCATE ledger entry, and cancelling writes the matching DEALLOCATE entries, so the ledger still reconciles.

### Picking
- Releasing an Allocated order creates one pick task per allocation. Each task takes its zone and pick sequence from the bin and its priority from the order, and its destination is the warehouse's pack location (`PACK-01`).
- Pickers claim tasks with `FOR UPDATE SKIP LOCKED`, so concurrent pickers always get different tasks without waiting on each other. The most urgent task comes first, then the lowest pick sequence.
- Confirming a pick moves the stock to the pack location with a PICK ledger entry; the reservation moves with it. The first confirmation moves the order to Picking, and the order becomes Picked once no pick task is left open.
- A short pick (fewer units than the task asks for) picks what was found, writes the missing units off with an ADJUST_OUT entry (reason `SHORT_PICK`), marks the task Short, creates a count task for the bin, and allocates the remainder again with new pick tasks. If no stock is left, the order still finishes picking and the line's picked quantity shows the gap.
- Count tasks are created but not handed out by `/tasks/next` yet, because nothing can confirm them.

### Events
- Order changes are announced as events through a transactional outbox: `OrderAccepted.v1` when a new order arrives (not for a duplicate), and `OrderAllocated.v1` or `OrderShort.v1` after each allocation. `OrderShipped.v1` is defined for when orders can ship.
- An event is written in the same database transaction as the change, so it exists exactly when the change commits.
- A relay publishes unsent events in order every second, in batches of 100, using `FOR UPDATE SKIP LOCKED`. Delivery is at least once, so each payload carries an `eventId` for consumers to drop duplicates, plus `occurredAt`, the order's id, external id, warehouse, status and lines.
- A failed publish keeps the event, counts the attempt, records the error and retries on the next run. Later events of the same order wait behind it, so each order's events arrive in order.
- For now events are written to the application log. Events published more than 7 days ago are deleted daily.

### Operational Insights
- Total inventory per warehouse
- Low stock item reports
- Shipment freshness tracking
- Turnover rate analytics

## Roadmap

The system is being extended one phase at a time, each in its own pull request:

| Phase | Scope | Status |
|---|---|---|
| A | Inventory integrity and concurrency | Done |
| B | Storage locations and LPNs | Done |
| C | Inventory ledger and idempotent stock changes | Done |
| D | Outbound orders and allocation | Done |
| E | Task engine (release, pick, short picks and count tasks) | Done |
| F | Transactional outbox for events | Done |

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## License

This project is licensed under the MIT License.

## Support

For support, email chiragyashu97@gmail.com or open an issue in the repository.