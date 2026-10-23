# PROGRESS.md

## 1. Next action

Phase 0 is done. Start Phase 1: bump the parent to Spring Boot 3.5.16, set `java.version` to 25, replace the Lombok `1.18.38` annotation processor version with `${lombok.version}`, drop the hardcoded compiler `source`/`target`, move the Dockerfile to temurin 25 and the wrapper to Maven 3.9.16. Then run the Phase 1 command in section 7. Re-check whether the `testcontainers.version` override is still needed once the parent is 3.5.16.

## 2. Goal

Upgrade the Java application (not `forecasting/`) from Java 21 / Spring Boot 3.5.3 to Java 25 / Spring Boot 4.1.x, in phases that are each committed and verified on their own.

| Phase | Scope |
|---|---|
| 0 | Make tests runnable without a live database, capture an API baseline on JDK 21 / Boot 3.5.3 |
| 1 | Java 21 to 25 on Boot 3.5.16 (Lombok unpinned, Dockerfile on temurin 25, wrapper 3.9.16) |
| 2 | Boot 3.5 to 4.1 (`spring-boot-starter-webmvc`, `spring-boot-starter-flyway`, springdoc 3.x, Testcontainers 2.x) |
| 3 | Runtime and container polish (optional) |
| 4 | Final checklist and merge |

## 3. Decisions

- Work happens on branch `chore/java25-boot4`, cut from `main`, in the worktree `../wms-upgrade`. The original checkout stays on `feature/demand-forecasting-pipeline` with its uncommitted `forecasting/pyproject.toml` change untouched, because `main` has no `forecasting/` and a checkout would collide with it.
- Integration tests use the `itest` profile, not `dev`. `application-dev.yml` sets `spring.flyway.url` explicitly, which Testcontainers' `@ServiceConnection` does not override, so Flyway would still try `localhost:5432`.
- One HTTP-level contract test replaces the planned per-controller `@WebMvcTest` classes. It exercises the real Jackson, Hibernate and Flyway stack, which is what the Boot 4 upgrade changes.
- Tests call the API with the JDK `HttpClient`, not MockMvc or TestRestTemplate, because both of those move packages in Boot 4. The tests should not need edits during Phase 2, so that a green run proves behaviour is unchanged.
- Baseline JSON in `src/test/resources/api-baseline/` was captured from the running app on JDK 21 / Boot 3.5.3. `api-docs.json` there is a reference for manual diffing only; springdoc 3 is expected to change it in harmless ways, so no test asserts it.

## 4. Tasks

| Task | Status |
|---|---|
| Phase 0: branch and worktree | DONE |
| Phase 0: Testcontainers setup, `contextLoads` passing | DONE |
| Phase 0: Flyway migration test | DONE |
| Phase 0: API contract test with baseline snapshots | DONE |
| Phase 1 | TODO |
| Phase 2 | TODO |
| Phase 3 | TODO |
| Phase 4 | TODO |

## 5. Environment notes

- JDK 21 at `/usr/lib/jvm/java-21-openjdk-amd64`, JDK 25 at `/usr/lib/jvm/java-25-openjdk-amd64`. The default `java` on PATH is 25, so set `JAVA_HOME` explicitly for every build.
- `mvnw` is committed without the executable bit (`core.fileMode=false`), so run it as `sh mvnw`.
- Docker Engine 29.x is available locally; Testcontainers needs it. Engine 29 rejects Docker API clients older than 1.44, so Testcontainers below 1.21.4 fails with "client version 1.32 is too old". `pom.xml` overrides `testcontainers.version` to 1.21.4 for that reason. In Phase 2, Boot 4 brings Testcontainers 2.x: the override goes away, the artifact becomes `testcontainers-postgresql` and `PostgreSQLContainer` moves to `org.testcontainers.postgresql`.
- The three `@SpringBootTest` classes use identical annotations so they share one cached context and one container. Keep it that way when adding integration tests.

## 6. Noticed, not fixed

- `mvnw` lacks the executable bit in git (the Dockerfile works around it with `chmod`).
- `application.properties` sets `spring.application.name=Warehouse`, which conflicts with `warehouse-management-system` in `application.yml`.
- Actuator exposes `prometheus`, but `micrometer-registry-prometheus` is not a dependency, so the endpoint does not exist.
- `application.yml` sets `hibernate.dialect` explicitly, which is unnecessary and deprecated.
- H2 is a test dependency but nothing uses it.
- `InventoryService.adjustStock` and `reserveStock` return a stale `availableQuantity` in the response (the `@PreUpdate` recalculation runs after the DTO is built). Persisted data is correct. The contract test deliberately checks state via a follow-up GET.
- Over-reserving stock returns 400 via `IllegalArgumentException`, although an unused `InsufficientStockException` handler returning 409 exists. The contract test pins the current 400.
- Database password is committed in `docker-compose.yml`, `application-dev.yml` and `.env`.
- `.github/modernize/` in the original checkout holds an abandoned August upgrade attempt; delete it once this upgrade lands.

## 7. Verification commands

| Phase | Command | Pass condition |
|---|---|---|
| 0 | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 sh mvnw -B clean verify` | BUILD SUCCESS, all tests green |
| 1 | `JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 sh mvnw -B clean verify` | BUILD SUCCESS, no Lombok errors |
| 2 | same as Phase 1 | BUILD SUCCESS with no edits to the contract or migration tests beyond Testcontainers 2.x imports |

## 8. Session log

### 2026-10-23

- Wrote the phased upgrade plan (summarised in section 2).
- Created branch `chore/java25-boot4` in worktree `../wms-upgrade`.
- Baseline on JDK 21: 6 unit tests pass, `WarehouseApplicationTests.contextLoads` fails with "Connection to localhost:5432 refused".
- Captured the API baseline by running the app on JDK 21 / Boot 3.5.3 against a throwaway `postgres:16-alpine` container and saving each list endpoint plus `/api-docs` into `src/test/resources/api-baseline/`.
- Added `spring-boot-testcontainers` and `org.testcontainers:postgresql`, a shared `TestcontainersConfiguration`, and the `itest` profile on `WarehouseApplicationTests`.
- Hit the Docker 29 / Testcontainers 1.21.2 API incompatibility; fixed by overriding `testcontainers.version` to 1.21.4.
- Added `FlywayMigrationTest` (all 6 migrations applied, update trigger refreshes `updated_at`) and `ApiContractTest` (list endpoints strictly match the baseline, 404 and validation error shapes, and an inventory create/adjust/reserve/delete round trip).
- Confirmed the contract test catches regressions by altering `shipments.json` and `zones.json`; both changes failed as expected, then the files were restored.
- Phase 0 verification passes: 17 tests, BUILD SUCCESS on JDK 21.
