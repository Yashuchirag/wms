# PROGRESS.md

## 1. Next action

Phase A of `WMS_IMPLEMENTATION_PLAN.md` (inventory integrity, V7) is implemented and committed on branch `feat/phase-a-inventory-integrity`, cut from `main` at `997bafb`. It is not pushed or merged. At the user's request, Phase B (locations and LPNs, V8 and V9) is stacked on it in branch `feat/phase-b-locations`, cut from `0a92291`. When Phase A merges, rebase or merge Phase B onto `main` before its PR. Phase B is implemented and committed there. Both branches are pushed: PR #4 (https://github.com/Yashuchirag/wms/pull/4) merges Phase A into `main`, and PR #5 (https://github.com/Yashuchirag/wms/pull/5) is based on the Phase A branch so it shows only Phase B. PR #4 was merged into `main` with a merge commit (`5d1cef4`) and PR #5 was retargeted to `main` (mergeable, no conflicts). Next: the user reviews and merges PR #5. Do not start Phase C until the user says so. `WMS_IMPLEMENTATION_PLAN.md` is deliberately left untracked (user decision), so it exists only in this checkout.

Earlier: The upgrade (PR #1, `8a1865b`) and the credentials change (PR #2, `450bdf9`) are both merged into `main`, and `main` has been merged into `feature/demand-forecasting-pipeline` (`1708f5a`), and then the forecasting branch was merged into `main` via PR #3 (https://github.com/Yashuchirag/wms/pull/3), so `main` now holds the Java app and the `forecasting/` module. `feature/demand-forecasting-pipeline` was kept for further forecasting work. Remaining user follow-ups: rotate the dev DB and pgAdmin passwords (commands in the 2026-10-23 session log) and point the IDE at JDK 25.

## 2. Goal

Upgrade the Java application (not `forecasting/`) from Java 21 / Spring Boot 3.5.3 to Java 25 / Spring Boot 4.1.x, in phases that are each committed and verified on their own.

| Phase | Scope |
|---|---|
| 0 | Make tests runnable without a live database, capture an API baseline on JDK 21 / Boot 3.5.3 |
| 1 | Java 21 to 25 on Boot 3.5.16 (Lombok unpinned, Dockerfile on temurin 25, wrapper 3.9.16) |
| 2 | Boot 3.5 to 4.1 (`spring-boot-starter-webmvc`, `spring-boot-starter-flyway`, springdoc 3.x, Testcontainers 2.x) |
| 3 | Runtime and container polish (optional) |
| 4 | Final checklist and merge |

That upgrade is finished. The current goal is to implement `WMS_IMPLEMENTATION_PLAN.md` (Phases A to F: inventory integrity, locations, ledger, orders, tasks, outbox), one phase per branch and PR.

## 3. Decisions

- Work happened on branch `chore/java25-boot4`, cut from `main`, in the single checkout at `/root/Personal_Projects/wms` (no separate worktree). The branch was merged via PR #1 and deleted.
- Integration tests use the `itest` profile, not `dev`. Originally this was because `application-dev.yml` set `spring.flyway.url` explicitly, which Testcontainers' `@ServiceConnection` does not override. That block was removed in Phase 4, but `itest` stays so tests never pick up dev-only settings such as `clean-disabled: false` and DEBUG logging.
- One HTTP-level contract test replaces the planned per-controller `@WebMvcTest` classes. It exercises the real Jackson, Hibernate and Flyway stack, which is what the Boot 4 upgrade changes.
- Tests call the API with the JDK `HttpClient`, not MockMvc or TestRestTemplate, because both of those move packages in Boot 4. The tests should not need edits during Phase 2, so that a green run proves behaviour is unchanged.
- Baseline JSON in `src/test/resources/api-baseline/` was captured from the running app on JDK 21 / Boot 3.5.3. `api-docs.json` there is a reference for manual diffing only; springdoc 3 is expected to change it in harmless ways, so no test asserts it.

## 4. Tasks

| Task | Status |
|---|---|
| Phase 0: branch | DONE |
| Phase 0: Testcontainers setup, `contextLoads` passing | DONE |
| Phase 0: Flyway migration test | DONE |
| Phase 0: API contract test with baseline snapshots | DONE |
| Phase 1 | DONE |
| Phase 2 | DONE |
| Phase 3 | SKIPPED (optional, not requested) |
| Phase 4: remove properties migrator, update README, optional cleanups | DONE |
| Phase 4: open PR to `main` | DONE |
| Phase 4: merge with a merge commit | DONE |
| Cleanup: delete `appmod/java-upgrade-20260811013250` and `.github/modernize/` | DONE |
| Credentials: move out of tracked files into `.env` | DONE |
| Credentials: rotate the dev DB and pgAdmin passwords | TODO (user) |
| Merge `main` into `feature/demand-forecasting-pipeline` | DONE |
| Merge `feature/demand-forecasting-pipeline` into `main` (PR #3) | DONE |
| Phase A: inventory integrity and concurrency (V7) | DONE (committed, not pushed) |
| Phase A: push and open PR (#4) | DONE |
| Phase A: merge PR #4 | DONE (`5d1cef4`) |
| Phase B: locations and LPNs (V8, V9), stacked on Phase A | DONE (committed, not pushed) |
| Phase B: push and open stacked PR (#5, based on the Phase A branch) | DONE |
| Phase B: retarget PR #5 to `main` | DONE |
| Phase B: merge PR #5 | TODO (user review) |

## 5. Environment notes

- JDK 21 at `/usr/lib/jvm/java-21-openjdk-amd64`, JDK 25 at `/usr/lib/jvm/java-25-openjdk-amd64`. The default `java` on PATH is 25, so set `JAVA_HOME` explicitly for every build.
- `mvnw` is committed without the executable bit (`core.fileMode=false`), so run it as `sh mvnw`.
- The Docker daemon does not autostart (no systemd in this WSL session). `service docker start` fails with `ulimit: error setting limit (Invalid argument)` because the shell's soft fd limit (1048576) is above the hard limit the init script sets (524288). Start it with `bash -c 'ulimit -Sn 1024; service docker start'`.
- Docker Engine 29.x is available locally; Testcontainers needs it. Engine 29 rejects Docker API clients older than 1.44, so Testcontainers below 1.21.4 fails with "client version 1.32 is too old". Phase 0 overrode `testcontainers.version` to 1.21.4 for that reason; Boot 3.5.16 manages 1.21.4 itself, so Phase 1 removed the override. In Phase 2, Boot 4 brings Testcontainers 2.x: the artifact becomes `testcontainers-postgresql` and `PostgreSQLContainer` moves to `org.testcontainers.postgresql`.
- The VS Code Java extension rebuilds into `target/` when `pom.xml` changes, which can make `mvn clean` fail with "Failed to delete .../target". Rerunning the command fixes it.
- The three `@SpringBootTest` classes use identical annotations so they share one cached context and one container. Keep it that way when adding integration tests.

## 6. Noticed, not fixed

- `mvnw` lacks the executable bit in git (the Dockerfile works around it with `chmod`).
- `GlobalExceptionHandler`'s catch-all `@ExceptionHandler(Exception.class)` also catches Spring's `NoResourceFoundException`, so every unknown URL returns 500 (logged at ERROR) instead of 404. This predates the upgrade.
- README's "Run with coverage" command (`jacoco:report`) does not work: no JaCoCo plugin is configured in `pom.xml`.
- Fixed in Phase A: `adjustStock` and `reserveStock` used to return a stale `availableQuantity`; over-reserving used to return 400 instead of 409.
- `processShipment` reserves from the lowest-id inventory row for the item in that warehouse, whatever its zone. If that row is short but another zone has stock, the reservation fails with 409. Phase D's allocation replaces this.
- Cancelling a PROCESSING outbound shipment keeps its reservation, and SHIPPED does not decrement stock. Both are deliberate until Phase C (user decision, 2026-10-30).
- `PUT /api/v1/inventory/{id}` does not take a row lock; a concurrent change is caught by `@Version` and returns 409. It can also still try to set `reservedQuantity` above `quantity`, which the CHECK rejects with the generic 409 "The request conflicts with existing data." rather than a specific message.
- The `DataIntegrityViolationException` handler is global, so other constraint violations that used to return 500 (for example a duplicate shipment `trackingNumber`, or a second inventory row for the same item, warehouse and zone) now return 409 too.
- `DELETE /api/v1/inventory/{id}` still deletes a row that has reserved stock.
- No lock timeout is set, so a request waits as long as another transaction holds the inventory or shipment row lock.
- Inventory is still unique on (item, warehouse, zone), so one item cannot sit in two bins of the same zone. Deferred to Phase C, when put-away starts writing `inventory.location_id` (user decision, 2026-10-30).
- `inventory.location_id` and `inventory.lpn_id` exist in the schema but are not mapped in `Inventory` or exposed in `InventoryDTO` yet, so the `inventory.json` baseline is unchanged. LPNs have no entity or endpoints (Phase B scope was schema only).
- `DELETE /api/v1/zones/{id}` now returns 409 for any zone that has locations, which includes every seeded zone (consequence of RESTRICT on `locations.zone_id`).
- An invalid enum in a query or path parameter (for example `GET /api/v1/locations?type=SHELF`, or the existing `/api/v1/zones/type/{type}`) falls through to the catch-all handler and returns 500 instead of 400.
- A duplicate location code returns the generic 409 message "The request conflicts with existing data." rather than naming the code.
- On JDK 25, Lombok (`lombok.permit.Permit`, at compile time) and ModelMapper 3.2.4 (`TypeResolver`, at runtime) call deprecated `sun.misc.Unsafe` methods and print warnings. Neither breaks anything yet. ModelMapper 3.2.8 (Phase 2) removed its warning; the Lombok one (still present with 1.18.48) needs an upstream fix.
- With Jackson 3, `/actuator/health` orders its keys differently (`components` before `status`). Our own endpoints' JSON is byte-identical apart from timestamps. Nothing known depends on key order; the compose healthcheck uses `pg_isready`.
- The old dev database password (and pgAdmin `admin`/`admin`) remain in public git history. The user decided against rewriting history; rotating the password makes the old value useless. Until rotated, the running stack still uses the old value from the local `.env`.

## 7. Verification commands

| Phase | Command | Pass condition |
|---|---|---|
| 0 | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 sh mvnw -B clean verify` | BUILD SUCCESS, all tests green |
| 1 | `JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 sh mvnw -B clean verify`, then `docker build -t wms:phase1 .` | BUILD SUCCESS, no Lombok errors, no Mockito dynamic agent warning, image builds |
| 2 | `JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 sh mvnw -B clean verify`, then `docker build -t wms:phase2 .` | BUILD SUCCESS with no edits to the contract or migration tests beyond Testcontainers 2.x imports; no properties-migrator or `HHH90000025` warnings in the log |
| A | `JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 sh mvnw -B clean verify`, then `docker build -t wms:phase-a .` | BUILD SUCCESS, 24 tests green including `InventoryConcurrencyTest`; image builds |
| B | `JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 sh mvnw -B clean verify`, then `docker build -t wms:phase-b .` | BUILD SUCCESS, 34 tests green; image builds and `/actuator/flyway` lists V1 to V9 as SUCCESS |

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
- Committed `CLAUDE.md` on `main` (b453017) and cherry-picked it onto `feature/demand-forecasting-pipeline` and this branch, so every branch cut from `main` from now on has it.
- Removed the `../wms-upgrade` worktree at the user's request; all work now happens in `/root/Personal_Projects/wms`. The branch and its commits are unchanged.
- Phase 1: parent to Spring Boot 3.5.16, `java.version` 25, removed the hardcoded compiler `source`/`target` (the parent sets `release` from `java.version`), Lombok processor now uses `${lombok.version}` (1.18.46), removed the `testcontainers.version` override, Dockerfile on `eclipse-temurin:25-*-alpine`, Maven wrapper 3.9.16. Kept the `flyway.version` 11.8.2 pin because Boot 3.5.16 manages the older 11.7.2.
- Attached Mockito as a `-javaagent` in surefire (via `maven-dependency-plugin:properties`), which removed the JDK dynamic agent warnings.
- Phase 1 verification passes on JDK 25: 17 tests, BUILD SUCCESS, classes compiled at major version 69. The Docker image builds and runs Java 25.0.3; a smoke run with the `prod` profile against a throwaway Postgres applied all 6 migrations, reported health UP and served `/api/v1/warehouses` and Swagger UI.
- Pushed Phase 1.
- Phase 2, step 1: bumped only the parent to 4.1.2 (plus the forced Testcontainers 2 rename). The build compiled and `contextLoads` passed, but Flyway never ran and 9 tests failed with `relation "items" does not exist`. This confirmed the plan's Flyway trap and that `FlywayMigrationTest` and `ApiContractTest` catch it.
- Phase 2, step 2: `spring-boot-starter-web` to `spring-boot-starter-webmvc`; `flyway-core` replaced by `spring-boot-starter-flyway`, `flyway-database-postgresql` unversioned, `flyway.version` pin removed (now Flyway 12.4.0); springdoc 3.1.1; ModelMapper 3.2.8; `testcontainers-postgresql` with `org.testcontainers.postgresql.PostgreSQLContainer` (Testcontainers 2.0.5); `spring-boot-properties-migrator` added temporarily.
- The migrator flagged `server.error.include-message`; moved it to `spring.web.error.include-message` (same `always` value; the old key would silently default to `never`). Removed the explicit `hibernate.dialect` after Hibernate 7.4 logged `HHH90000025`.
- Phase 2 verification passes: 17 tests on Spring Boot 4.1.2 / Hibernate 7.4.13 / Jackson 3.1.7, with the contract and migration tests unchanged apart from the Testcontainers import.
- Docker smoke: a database migrated by the Phase 1 image (Flyway 11.8) was validated by the Phase 2 image (Flyway 12.4) with "Schema is up to date", `/actuator/flyway` lists V1 to V6 as SUCCESS, Swagger UI loads, and the prod profile logs no migrator warnings. The `dev` profile also starts cleanly from the packaged jar.
- `/api-docs` diff against the baseline: springdoc 3 now marks `@NotBlank` fields (`sku`, `name`, `code`, `trackingNumber`) as `required`. No other changes.
- Pushed Phase 2.
- Phase 4 step 1: removed `spring-boot-properties-migrator`; updated README (Java 25, Spring Boot 4.1.2, Flyway 12.4.0, springdoc 3.1.1, Lombok 1.18.48, ModelMapper 3.2.8, Docker needed for tests, dev Flyway now shares the datasource). README uses CRLF line endings with no final newline; preserve that when editing it.
- Optional cleanups at the user's request: removed the unused H2 test dependency; removed `prometheus` from the actuator exposure list (no registry dependency, so it never worked); deleted `application.properties`, so `spring.application.name` is now `warehouse-management-system` from `application.yml` instead of `Warehouse`; removed the duplicate `url`/`user`/`password` from the dev Flyway block so Flyway uses the main datasource.
- Verification: 17 tests pass on JDK 25, the jar contains neither H2 nor the migrator, and the packaged jar with the `dev` profile migrated a fresh local Postgres through the main datasource and served data.
- Post-merge steps for the user: `git checkout main && git pull`; `docker compose up --build`; point the IDE at JDK 25; delete the `appmod/java-upgrade-20260811013250` branch and `.github/modernize/`; merge `main` into `feature/demand-forecasting-pipeline`.
- Pushed Phase 4 step 1 and opened PR #1 to `main` (7 commits, 19 files).
- Merged PR #1 into `main` with a merge commit (`8a1865b`); `gh` deleted `chore/java25-boot4`.
- Deleted the untracked `.github/modernize/` folder (the abandoned August attempt; never tracked in git).
- Before deleting `appmod/java-upgrade-20260811013250`, found that its commit `915fbc6` (stop documenting the dev database password in the README) existed on no other branch. At the user's request, ported that change to `main`'s README, then deleted the branch locally and on GitHub. Its other commits were the superseded Java 25 attempt and `40da2aa`, which is also on `feature/demand-forecasting-pipeline`.
- Credentials (branch `chore/externalize-credentials`): the repo is public, so the dev DB password was exposed in `docker-compose.yml` and `application-dev.yml`. `.env` was already gitignored and never committed (the earlier note in section 6 saying otherwise was wrong). `docker-compose.yml` now reads `DB_USERNAME`, `DB_PASSWORD`, `PGADMIN_EMAIL` and `PGADMIN_PASSWORD` from `.env` and refuses to start if a password is missing; all published ports bind to `127.0.0.1`; the Postgres healthcheck uses `$${POSTGRES_USER}`. `application-dev.yml` imports `optional:file:.env[.properties]` and uses `${DB_URL}`, `${DB_USERNAME}`, `${DB_PASSWORD}` (no password default). Added `.env.example`; README explains the setup. Added `PGADMIN_EMAIL`/`PGADMIN_PASSWORD` to the user's local `.env` with the current values so the stack keeps working.
- Verification: 17 tests pass; recreated the compose stack (kept the `wms_postgres_data` volume; a Docker recreate race left temporary container names, fixed with `docker compose down` and `up -d`, no `-v`); app connects ("Schema is up to date"), pgAdmin login works with the `.env` values, Windows can still reach ports 8080, 5050 and 5432 on localhost; the dev profile connects using `.env` and fails without it.
- Rotation commands for the user (run from the repo root, with the stack up): pick a new password, then `docker exec -it wms-postgres psql -U postgres -c "ALTER USER postgres PASSWORD 'NEW_PASSWORD';"`, set `DB_PASSWORD=NEW_PASSWORD` in `.env`, and run `docker compose up -d` to restart the app. For pgAdmin, change `PGADMIN_PASSWORD` in `.env` and run `docker compose up -d pgadmin` (it has no volume, so it re-initialises with the new password). If a native local Postgres also uses the old password, run the same `ALTER USER` there.
- Pushed `chore/externalize-credentials` and opened PR #2 to `main`.
- Merged PR #2 into `main` (`450bdf9`).
- Scanned `feature/demand-forecasting-pipeline` for secrets: the forecasting Python code has none (no DSN, tokens or DB URLs; `sentry-sdk` is a dependency but unconfigured); its only exposure was the pre-fix copies of `docker-compose.yml`, `application-dev.yml` and `README.md`. Merged `main` into the branch (`1708f5a`, no conflicts, no changes under `forecasting/`). Java tests (17) and forecasting pytest (4) pass on the branch. `forecasting/.gitignore` covers `.venv/`, `mlflow.db` and data; there is no `.env` under `forecasting/`.
- At the user's request, opened PR #3 to merge `feature/demand-forecasting-pipeline` into `main` (the branch already contained `main`, so no conflicts) and merged it with a merge commit, keeping the feature branch.

### 2026-10-30

- Started `WMS_IMPLEMENTATION_PLAN.md`, Phase A. The Docker daemon was down and `service docker start` failed on a `ulimit` error (cause and fix in section 5). After starting it, the baseline on `main` passed: 17 tests, BUILD SUCCESS on JDK 25.
- Created `feat/phase-a-inventory-integrity` from `main` at `997bafb` (same as `origin/main`).
- User decisions: keep `ON DELETE RESTRICT` on `inventory.item_id`; `PATCH /shipments/{id}/status` must not move a shipment to PROCESSING (only `/process` does, because it reserves stock); cancelling a PROCESSING shipment keeps its reservation for now; do not commit `WMS_IMPLEMENTATION_PLAN.md`. The user did not answer the locking question, so the recommended `PESSIMISTIC_WRITE` was used.
- Wrote `InventoryConcurrencyTest` first (50 concurrent `PATCH /reserve?quantity=1` on virtual threads against a row with quantity 30). On the old code it failed: all 50 requests returned 200 (an oversell), but `reserved_quantity` ended at 6, so 44 updates were lost.
- Locking choice: `reserveStock` and `adjustStock` load the row with `SELECT ... FOR UPDATE` (`InventoryRepository.findByIdForUpdate`). `@Version` was added as a backstop for paths that do not lock. @Version plus retry was rejected because 50 contenders on one row would exhaust 3 retries and reject requests that had stock, and because a correct retry needs a fresh transaction per attempt (the conflict surfaces at flush, and `processShipment` calls `reserveStock` inside its own transaction).
- `V7__inventory_integrity.sql`: `version` column, `chk_reserved_within_quantity`, `uq_inventory_item_warehouse_zone` recreated as `UNIQUE NULLS NOT DISTINCT`, `fk_inventory_item` recreated as `ON DELETE RESTRICT`.
- `InventoryService`: both stock methods throw `InsufficientStockException` (409). `adjustStock` now rejects a result below `reserved_quantity` (it used to check only below zero), otherwise the new CHECK would surface as a 500 at commit. `convertToDTO` computes `availableQuantity`, which fixes the stale response value for every endpoint.
- `ShipmentService`: `processShipment` and `updateShipmentStatus` lock the shipment row. `processShipment` accepts only PENDING. Status updates follow one allowed-moves map (PENDING to CANCELLED, PROCESSING to SHIPPED or CANCELLED, SHIPPED to DELIVERED). Illegal moves throw the new `InvalidStatusTransitionException` (409).
- Found while implementing: `processShipment` called `getInventoryByItem`, which loaded the inventory entity unlocked into the same persistence context, so a later `FOR UPDATE` query would return that possibly stale instance. It now looks up only the id (`findIdsByItemIdAndWarehouseId`).
- `GlobalExceptionHandler`: 409 "Conflict" for `InvalidStatusTransitionException`, `ObjectOptimisticLockingFailureException` and `DataIntegrityViolationException` (the last one covers deleting an item that still has stock).
- Deliberate test changes: `ApiContractTest` over-reserve 400 to 409 (error "Insufficient Stock", same message); the adjust and reserve responses are now asserted directly, not only through a follow-up GET; deleting an item that has stock returns 409. `FlywayMigrationTest` expects V7 and checks that the CHECK rejects an oversell and that the unique constraint rejects a second null-zone row. `InventoryServiceTest` stubs `findByIdForUpdate` instead of `findById`. List baselines are unchanged, and the new tests create and delete their own rows so the STRICT baselines stay valid.
- New `ShipmentWorkflowTest`: double `/process` reserves once and the second call returns 409; an illegal transition returns 409; `PATCH status=PROCESSING` returns 409 and reserves nothing; the legal path PENDING, PROCESSING, SHIPPED, DELIVERED works.
- Verification: `clean verify` passes with 24 tests in one shared Spring context; `InventoryConcurrencyTest` passed on two more isolated runs. `docker build -t wms:phase-a .` succeeds. A smoke run of the image with the `prod` profile against a throwaway `postgres:16-alpine` reached health UP, returned fresh quantities from `/reserve`, and returned 409 for an over-reserve and for deleting a stocked item (which proves V7's RESTRICT key is in place). The smoke containers and network were removed afterwards.
- Phase B, at the user's request, before Phase A was merged. The user chose to stack it: `feat/phase-b-locations` was cut from `feat/phase-a-inventory-integrity` (`0a92291`). The optional CSV import for locations was skipped (user decision).
- User decisions for Phase B: leave the inventory unique key alone until Phase C; `locations.zone_id` is `ON DELETE RESTRICT`; LPN status values are ACTIVE, CLOSED and SHIPPED with a CHECK.
- `V8__create_locations.sql`: `locations` table as specified (nullable `zone_id`, type CHECK, `UNIQUE (warehouse_id, code)`, partial index on (zone_id, type) WHERE is_active, updated_at trigger reusing V6's function), seeded with three bins per seeded zone (RESERVE in the bulk zone Z-C1, PICK_FACE elsewhere, pick_sequence 10/20/30) plus RCV-01 and PACK-01 in WH-001 with no zone, and nullable `inventory.location_id` (RESTRICT, indexed). Seeds select zones and the warehouse by code and use `ON CONFLICT DO NOTHING`, so a dev database with extra or missing seed rows still migrates.
- `V9__create_lpns.sql`: `lpns` table (type and status CHECKs, `UNIQUE (warehouse_id, code)`, RESTRICT on location and parent LPN, warehouse CASCADE) and nullable `inventory.lpn_id` (RESTRICT, indexed).
- Checked before relying on it: with RESTRICT keys, deleting a warehouse still cascades through zones, locations, nested LPNs and inventory. A probe on a throwaway Postgres 16 showed both RESTRICT and NO ACTION allow the cascade and both block a direct zone delete; `FlywayMigrationTest` now covers this on the real schema.
- Location API in the Zone layering: `GET /api/v1/locations?zoneId=&type=` (both optional, ordered by id), `GET/PUT/DELETE /api/v1/locations/{id}`, `POST /api/v1/locations`. A zone from another warehouse or a PUT that changes the warehouse returns 400; a duplicate code in one warehouse returns 409 through the Phase A constraint handler; `isActive` defaults to true and `isMixedSkuAllowed` to false. No caching, unlike `ZoneService`.
- Tests: `FlywayMigrationTest` expects V8 and V9 (deliberate) and checks the seeds, the type and status CHECKs, the zone RESTRICT and the warehouse cascade. New `LocationApiTest` covers the filters, a create/get/update/delete round trip, per-warehouse code uniqueness, the zone/warehouse checks and validation errors; it deletes its own `TST-` rows. `ApiContractTest` is unchanged.
- Verification: `clean verify` passes with 34 tests in one shared Spring context. `docker build -t wms:phase-b .` succeeds. A `prod` profile smoke run against a throwaway Postgres reached health UP, `/actuator/flyway` listed V1 to V9 as SUCCESS, `GET /api/v1/locations?type=RECEIVING` returned RCV-01, and deleting a seeded zone returned 409. The smoke containers were removed.
- At the user's request, pushed both branches and opened PR #4 (Phase A into `main`) and PR #5 (Phase B, based on `feat/phase-a-inventory-integrity`), so each PR shows one phase. Nothing was pushed to `main` directly.
- At the user's request, merged PR #4 with a merge commit (`5d1cef4`); `feat/phase-a-inventory-integrity` was kept. `gh pr edit 5 --base main` failed with a GraphQL error about the deprecated classic Projects API (a `gh` bug, not a repo problem), so PR #5 was retargeted with `gh api -X PATCH repos/Yashuchirag/wms/pulls/5 -f base=main`. It is mergeable with no conflicts and still shows only the Phase B commits.
