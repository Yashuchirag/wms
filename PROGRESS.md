# PROGRESS.md

## 1. Next action

The upgrade is complete: PR #1 was merged into `main` as merge commit `8a1865b`. Nothing in the upgrade itself is outstanding. Remaining follow-ups belong to the user: run `docker compose up --build`, point the IDE at JDK 25, and merge `main` into `feature/demand-forecasting-pipeline`. Phase 3 (optional runtime polish) was skipped and can be picked up as new work. Section 6 lists the pre-existing issues found along the way, as candidates for future work.

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

## 5. Environment notes

- JDK 21 at `/usr/lib/jvm/java-21-openjdk-amd64`, JDK 25 at `/usr/lib/jvm/java-25-openjdk-amd64`. The default `java` on PATH is 25, so set `JAVA_HOME` explicitly for every build.
- `mvnw` is committed without the executable bit (`core.fileMode=false`), so run it as `sh mvnw`.
- Docker Engine 29.x is available locally; Testcontainers needs it. Engine 29 rejects Docker API clients older than 1.44, so Testcontainers below 1.21.4 fails with "client version 1.32 is too old". Phase 0 overrode `testcontainers.version` to 1.21.4 for that reason; Boot 3.5.16 manages 1.21.4 itself, so Phase 1 removed the override. In Phase 2, Boot 4 brings Testcontainers 2.x: the artifact becomes `testcontainers-postgresql` and `PostgreSQLContainer` moves to `org.testcontainers.postgresql`.
- The VS Code Java extension rebuilds into `target/` when `pom.xml` changes, which can make `mvn clean` fail with "Failed to delete .../target". Rerunning the command fixes it.
- The three `@SpringBootTest` classes use identical annotations so they share one cached context and one container. Keep it that way when adding integration tests.

## 6. Noticed, not fixed

- `mvnw` lacks the executable bit in git (the Dockerfile works around it with `chmod`).
- `GlobalExceptionHandler`'s catch-all `@ExceptionHandler(Exception.class)` also catches Spring's `NoResourceFoundException`, so every unknown URL returns 500 (logged at ERROR) instead of 404. This predates the upgrade.
- README's "Run with coverage" command (`jacoco:report`) does not work: no JaCoCo plugin is configured in `pom.xml`.
- `InventoryService.adjustStock` and `reserveStock` return a stale `availableQuantity` in the response (the `@PreUpdate` recalculation runs after the DTO is built). Persisted data is correct. The contract test deliberately checks state via a follow-up GET.
- Over-reserving stock returns 400 via `IllegalArgumentException`, although an unused `InsufficientStockException` handler returning 409 exists. The contract test pins the current 400.
- On JDK 25, Lombok (`lombok.permit.Permit`, at compile time) and ModelMapper 3.2.4 (`TypeResolver`, at runtime) call deprecated `sun.misc.Unsafe` methods and print warnings. Neither breaks anything yet. ModelMapper 3.2.8 (Phase 2) removed its warning; the Lombok one (still present with 1.18.48) needs an upstream fix.
- With Jackson 3, `/actuator/health` orders its keys differently (`components` before `status`). Our own endpoints' JSON is byte-identical apart from timestamps. Nothing known depends on key order; the compose healthcheck uses `pg_isready`.
- Database password is committed in `docker-compose.yml`, `application-dev.yml` and `.env` (the README no longer repeats it).

## 7. Verification commands

| Phase | Command | Pass condition |
|---|---|---|
| 0 | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 sh mvnw -B clean verify` | BUILD SUCCESS, all tests green |
| 1 | `JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 sh mvnw -B clean verify`, then `docker build -t wms:phase1 .` | BUILD SUCCESS, no Lombok errors, no Mockito dynamic agent warning, image builds |
| 2 | `JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 sh mvnw -B clean verify`, then `docker build -t wms:phase2 .` | BUILD SUCCESS with no edits to the contract or migration tests beyond Testcontainers 2.x imports; no properties-migrator or `HHH90000025` warnings in the log |

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
