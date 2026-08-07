# Warehouse Management System (WMS)

A comprehensive warehouse management system built with Spring Boot 3 and Java 21.

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

- **Java**: 21
- **Spring Boot**: 3.5.3
- **Database**: PostgreSQL 16
- **Migration**: Flyway 11.8.2
- **Build Tool**: Maven
- **Documentation**: Swagger/OpenAPI 3 (springdoc-openapi 2.8.6)
- **Utilities**: Lombok 1.18.38, ModelMapper 3.2.4
- **Containerization**: Docker & Docker Compose

## Prerequisites

- Java 21 or higher
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

2. Start all services:
```bash
docker compose up -d
```

3. Access the application:
    - API: http://localhost:8080
    - Swagger UI: http://localhost:8080/swagger-ui.html
    - PgAdmin: http://localhost:5050 (admin@wms.com / admin)

### Option 2: Local Development

1. Install PostgreSQL and create the database:
```sql
CREATE DATABASE wms;
```

2. By default, `application-dev.yml` connects as `postgres` on `localhost:5432/wms` with the password `274136`. Update the `spring.datasource` and `spring.flyway` credentials in `application-dev.yml` (or your local `postgres` user's password) to match your environment.

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