# Warehouse Management System (WMS)

A comprehensive warehouse management system built with Spring Boot 4 and Java 25.

## Features

- 📦 Real-time inventory tracking across multiple warehouses
- 🏢 Warehouse and zone management
- 📊 Item catalog management with turnover rates
- 🚚 Shipment tracking (inbound/outbound)
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

## Database Schema

The system uses PostgreSQL with Flyway migrations. Key tables:

- **warehouses**: Warehouse locations and details
- **zones**: Storage zones within warehouses
- **items**: Product catalog
- **inventory**: Stock levels per warehouse/zone
- **shipments**: Inbound and outbound shipments

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

### Warehouse Zoning Strategy
- HIGH_DEMAND zones for fast-moving items
- MEDIUM_DEMAND for regular items
- BULK storage for large quantities
- FRAGILE zones for sensitive items

### Shipment Management
- Inbound/outbound shipments
- Tracking number generation
- Status tracking (Pending → Processing → Shipped → Delivered)
- Automated inventory updates

### Operational Insights
- Total inventory per warehouse
- Low stock item reports
- Shipment freshness tracking
- Turnover rate analytics

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