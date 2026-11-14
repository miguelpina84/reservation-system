# AGENTS.md — reservation-system

Goal: **right change, smallest diff, fewest turns**. Deliverables (code, commits, docs) in Spanish. This file is the source of truth for how to work in this repo.

## 1. Loop (follow in order)
1. **Batch 1 — all greps in ONE turn** (symbol, endpoint, table, migration). If a skill covers the area, load it instead of grepping.
2. **Batch 2 — all reads in ONE turn**: the files you will edit + ONE file to copy the pattern from.
3. **Edit.** You are ready when you know file, lines and the pattern to copy. Anything else is proven by the test in step 4, not by reading more.
4. **Directed test** (see §3). Red → fix only what failed.
5. **Report** in ≤5 lines. **Atomic commits** (see §4). Push only on request.

Budget: 2 batches before the first edit. Still unsure after that? Stop and ask ONE question with your proposed default. Never investigate to avoid asking.

## 2. Hard limits
- **User scope is final.** "No toques X" = don't read, verify or analyze X. Once the user answers, edit — no new verification round.
- **Never read one file, think, read another.** Independent greps/reads always go in the same turn.
- **Don't re-read** a file already read in this task. Don't re-verify a fact you already stated.
- **No narration** between tool calls ("Antes de tocar, verifico…"). Reason briefly; at most one plan message.
- **Don't touch the DB** (data, migrations already applied) without explicit OK. New migration = new file `db/migration/V<n>__*.sql`, never edit an applied one.
- **NEVER run the full suite** (`mvn test` on everything) unless the user asks; it costs ~60 s. Use a directed test.
- **No live screenshots**; validate with assertions against the real container.
- No refactors, renames, cleanups or full-file rewrites.
- **Don't change the API contract** (status codes, JSON shape, field names) without saying so explicitly first — it breaks the Postman collection and the CI smoke test.

## 3. Validation (minimum that proves the change)
- **Service logic**: directed test of the touched class (`mvn -Dtest=ClaseTest test`).
- **HTTP surface**: the MockMvc integration test (`ReservationApiIntegrationTest`).
- **Concurrency**: `ReservationConcurrencyTest` — one winner, the rest 409. If you touch the reservation lock or the overlap query, run it.
- **PostgreSQL behaviour**: the H2 tests do NOT prove the PostgreSQL path. Verify in Docker (§5) before claiming it works.
- **After deploy, prove it**: one `curl` against the running container. A green build is not proof.

## 4. Commits and branches
- **Atomic commits** (client requirement): one topic per commit, Conventional Commits, **message in Spanish** with a body explaining the why. Example: `fix: agrega el modulo de Flyway para PostgreSQL`.
- **Never one big commit per turn.** Split by logical change (domain / service / API / config / tests / docs).
- **Work goes to a feature branch** (`feature/...`). `main` stays clean for the production merge; never push straight to `main`.
- **Push only on explicit request.**

## 5. Stack & Commands
**Java 17 (target) / JDK 21 (toolchain) / Spring Boot 3.3.5 / Spring Data JPA / Flyway 10 / Spring Security (HTTP Basic) / H2 (default) + PostgreSQL 16 (profile `postgres`) / JUnit 5 + Mockito + MockMvc / JaCoCo / k6 + Node probe / Docker.**

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'; $env:Path="$env:JAVA_HOME\bin;C:\apache-maven\bin;$env:Path"
Set-Location D:\salva_pinna\reservation-system

# Directed test (one class, not the whole suite)
mvn -B test -Dtest=ReservationApiIntegrationTest
mvn -B test -Dtest=ReservationConcurrencyTest

# Package ALWAYS with clean
mvn -B clean package -DskipTests

# Stack in Docker (PostgreSQL + API). APP_PORT/PG_PORT avoid clashes with other services on this machine
$env:DOCKERFILE='Dockerfile.offline'; $env:APP_PORT='18080'; $env:PG_PORT='15432'
docker compose up -d --build postgres app
docker compose ps            # wait for (healthy)
docker compose down -v

# API proof against the running container
curl.exe -s http://localhost:18080/actuator/health
curl.exe -s http://localhost:18080/api/rooms

# Postman collection (18 requests, 29 assertions)
cd Documentacion; npx.cmd -y newman@6 run reservation-system.postman_collection.json -e postman_environment_local.json --env-var baseUrl=http://localhost:18080

# Concurrency probe (no dependencies)
node performance/concurrency-probe.mjs http://localhost:18080 100
```

- **`package` SIEMPRE con `clean`**: the Dockerfile copies `target/*.jar`, so stale classes of a deleted file can ship inside the JAR.
- **Ports 8080 and 5432 are taken on this machine** (`AgentService` and Docker). Always use `APP_PORT` / `PG_PORT`.
- **PowerShell + `curl.exe` drops quotes**: use `--data-binary "@archivo.json"`; a JSON body inline arrives malformed and yields a misleading 400.

## 6. Traps (verified, do not rediscover)
- **Flyway 10 modularized the DB support**: `flyway-core` only covers H2. PostgreSQL needs `flyway-database-postgresql` in `pom.xml`. Without it the app dies with `Unsupported Database: PostgreSQL 16`.
- **H2 ≠ PostgreSQL**: a green `mvn test` on H2 says nothing about the PostgreSQL path. Always check the schema in the Docker stack (that is how the Flyway bug above was found).
- **The pessimistic lock is per room**, so all reservations of one room serialize: ~40 writes/s in the measured peak, p95 ~2.4 s with 100 parallel requests. Correctness first; granularity is a known future improvement (per-slot lock or `EXCLUDE USING gist`).
- **Overlap is strict**: `startExistente < endNuevo && endExistente > startNuevo`. Touching borders do NOT collide. A reservation ending 10:00 and one starting 10:00 are compatible.
- **Cancel is logical** (`status = CANCELADA`), it never deletes; cancelling twice returns 409.
- **Docker Hub is very slow from here**: prefer the images already in the local registry (`bellsoft/liberica-openjdk-alpine:21.0.1-12-cds-x86_64`, `postgres:16-alpine`). `Dockerfile.offline` builds from the pre-built JAR without pulling anything; `apk add` does NOT work (no network to the Alpine repos).
- **A failure with 403 on every endpoint is not the app**: another container/proxy was answering on that port. Check `Get-NetTCPConnection -LocalPort <p>` before debugging the API.
- **The `Documentacion/` Postman collection is the manual contract**: if you change a status code or a field, update the collection and its assertions in the same commit.
