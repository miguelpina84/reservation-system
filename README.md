# reservation-system

API REST para reservar salas de reuniones. Java 17+ / Spring Boot 3.3 / H2 (por defecto) o PostgreSQL / Flyway / Spring Security (HTTP Basic).

## Cómo ejecutarlo

Requisitos: JDK 17 o superior y Maven 3.9+.

```bash
# 1. Tests (11 tests: 4 unitarios, 6 de integracion MockMvc, 1 de concurrencia)
mvn test

# 2. Arranque (H2 en memoria, datos semilla de la migracion V1)
mvn spring-boot:run

# 3. o bien, empaquetado
mvn clean package
java -jar target/reservation-system-0.0.1-SNAPSHOT.jar
```

La app escucha en `http://localhost:8080`.

### Cobertura

El build trae JaCoCo integrado; el reporte se genera en `target/site/jacoco/index.html` al correr `mvn test`.

Cobertura actual: **87.2 % de lineas, 83.8 % de instrucciones y 73.1 % de ramas**.

### Con PostgreSQL

```bash
docker compose up -d postgres
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

o con variables de entorno: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.

### Todo en Docker (PostgreSQL + aplicación)

```bash
docker compose up -d --build          # PostgreSQL + API en http://localhost:8080
docker compose ps                     # esperar a que app reporte (healthy)
docker compose logs -f app
docker compose down -v                # apagar y borrar el volumen
```

El `Dockerfile` es multi-stage (build con Maven + runtime JRE 21 de Liberica) y el `compose` define el healthcheck de la aplicación, así que `depends_on` espera a que la API esté lista y no a que el contenedor exista.

En redes restringidas (sin acceso a Docker Hub) hay una variante que reutiliza las imágenes ya presentes en el registro local:

```bash
mvn -B clean package
DOCKERFILE=Dockerfile.offline docker compose up -d --build
```

Los puertos son parametrizables: `APP_PORT` (8080) y `PG_PORT` (5432).

### Prueba de carga

```bash
docker compose --profile load up --build     # levanta el stack y ejecuta k6
```

El escenario (ver `performance/k6-load.js`) mide dos cosas:

1. **Conflicto bajo concurrencia**: 50 usuarios virtuales piden **el mismo horario de la misma sala** al mismo tiempo. Solo una debe recibir 201; las demás deben recibir 409. Es la prueba que valida que el bloqueo pesimista de la sala aguanta la concurrencia.
2. **Lectura sostenida**: 20 usuarios virtuales consultando el listado paginado, con umbrales de latencia p95.

Umbrales configurados en el script: `http_req_failed < 1 %`, p95 < 2000 ms en el escenario de conflicto, p95 < 1000 ms en lectura, y `checks > 99 %`. El resumen se guarda en `performance/summary.json`.

Para medir rápido sin k6 ni Docker hay una **sonda en Node sin dependencias** (`performance/concurrency-probe.mjs`):

```bash
node performance/concurrency-probe.mjs http://localhost:8080 100
```

### Resultados medidos

Medido en este entorno (Docker: app en contenedor Liberica JRE 21 + PostgreSQL 16, 100 peticiones en paralelo):

| Escenario | Resultado |
|---|---|
| 100 peticiones al **mismo** horario de la misma sala | **1 creada (201) + 99 conflictos (409)**, 0 errores 500 → el bloqueo de concurrencia aguanta |
| 100 peticiones en **horarios distintos** | **100 creadas (201)** → ninguna se pierde ni se bloquea entre sí |
| Latencia bajo saturación | p50 ~1.9 s, p95 ~2.4 s (las 100 peticiones se serializan en el lock de la sala) |
| Throughput de escritura | ~40 escrituras/s en el pico con 100 peticiones en paralelo |

> El tiempo alto de la latencia es esperable y es la consecuencia del diseño: el bloqueo pesimista es **por sala**, así que las 100 peticiones se atienden en serie. Con un volumen real de reservas por sala, el cuello de botella aparece ahí; la mejora natural es granular el bloqueo (por sala y franja) o sustituirlo por una restricción de exclusión de rangos en PostgreSQL (`EXCLUDE USING gist`).

### CI (GitHub Actions)

| Workflow | Qué hace |
|---|---|
| `.github/workflows/ci.yml` | En cada push y PR: `mvn verify` (tests + JaCoCo), publica el reporte de cobertura como artefacto, construye la imagen Docker y hace un **smoke test** contra el contenedor con PostgreSQL (crear sala, crear reserva, 409 por horario ocupado, 401 sin credenciales). |
| `.github/workflows/load-test.yml` | Ejecución manual (`workflow_dispatch`) o programada: levanta el stack en Docker y corre la prueba de k6, publicando `summary.json` como artefacto. |

### Credenciales

Usuarios en memoria, definidos en `application.yml` (`app.security.users`):

| usuario  | contraseña | roles      |
|----------|------------|------------|
| `admin`  | `admin123` | ADMIN,USER |
| `user`   | `user123`  | USER       |

```bash
curl -u admin:admin123 -H 'Content-Type: application/json' \
  -d '{"name":"Sala Tesla","capacity":6}' http://localhost:8080/api/rooms
```

## Endpoints

| Método   | Ruta                    | Auth | Descripción                                             |
|----------|-------------------------|------|---------------------------------------------------------|
| `POST`   | `/api/rooms`            | Sí   | Crear sala (201)                                       |
| `GET`    | `/api/rooms`            | No   | Listar salas                                            |
| `GET`    | `/api/rooms/{id}`       | No   | Consultar sala (404 si no existe)                       |
| `POST`   | `/api/reservations`     | Sí   | Crear reserva (201 / 400 / 409)                         |
| `GET`    | `/api/reservations`     | No   | Listar reservas con filtros y paginación                |
| `DELETE` | `/api/reservations/{id}`| Sí   | Cancelar reserva (200)                                  |

### Listado de reservas

`GET /api/reservations?roomId=2&date=2030-05-20&page=0&size=20&sortBy=startTime`

- `roomId` (opcional): filtra por sala.
- `date` (opcional, `YYYY-MM-DD`): reservas que se solapan con ese día.
- `page` (0-based, por defecto 0) y `size` (por defecto 20, máximo 100).

Respuesta:

```json
{
  "content": [ { "id": 1, "roomId": 2, "roomName": "Sala Curie", "responsable": "Ana",
                 "responsableEmail": null, "startTime": "2030-05-20T09:00:00",
                 "endTime": "2030-05-20T10:00:00", "status": "ACTIVA",
                 "createdAt": "2026-11-14T18:00:00Z" } ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
}
```

### Errores

Todos los errores devuelven el mismo formato:

```json
{
  "timestamp": "2026-11-14T18:00:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "La sala 'Sala Curie' ya está ocupada en el horario solicitado",
  "path": "/api/reservations",
  "fieldErrors": {}
}
```

- `400`: datos inválidos o regla de negocio incumplida (inicio ≥ fin, nombre de sala duplicado). Los errores de campo de Bean Validation van en `fieldErrors`.
- `401`: operación de escritura sin credenciales.
- `404`: sala o reserva inexistente.
- `409`: la sala ya está ocupada en ese horario, o se cancela dos veces la misma reserva.

## Pruebas

`mvn test` corre **11 tests**:

| Tipo | Clase | Qué cubre |
|---|---|---|
| Unitario (Mockito) | `ReservationServiceTest` | Solapamiento → 409; sin solapamiento → reserva creada |
| Unitario (Mockito) | `RoomServiceTest` | Nombre duplicado; normalización de nombre y ubicación |
| Integración (MockMvc) | `ReservationApiIntegrationTest` | 401 sin credenciales, 201 al crear, 409 por horario ocupado, 400 con `fieldErrors`, filtros y paginación, cancelación que libera el horario |
| Concurrencia | `ReservationConcurrencyTest` | Dos peticiones simultáneas por el mismo horario: solo una se crea, la otra recibe 409 (verifica el bloqueo pesimista de la sala) |

Cobertura medida con JaCoCo: **87.2 % de líneas, 83.8 % de instrucciones, 73.1 % de ramas** (reporte en `target/site/jacoco/index.html`).

Para las pruebas manuales hay una **colección de Postman** en `Documentacion/` con los casos de éxito y de error y sus aserciones; también se puede ejecutar en CI con `newman`. Ver `Documentacion/README.md`.

## Decisiones que tomé

- **Java 17 + Spring Boot 3.3** (target 17 para no atar el proyecto a la JDK del entorno; probado con JDK 21).
- **H2 en memoria por defecto y PostgreSQL con perfil `postgres`**: el enunciado acepta cualquiera de los dos. Las migraciones son SQL compatible con ambos, así que el mismo esquema sirve para los dos motores.
- **Flyway** en lugar de `ddl-auto: create`: el esquema se versiona y `hibernate.ddl-auto: validate` garantiza que el modelo JPA y la migración no se separen.
- **HTTP Basic** en lugar de JWT (el enunciado permite cualquiera de los dos): Basic cumple el requisito de "los endpoints que crean o cancelan requieren autenticación" sin introducir una dependencia de emisión/validación de tokens. Los usuarios se configuran en `application.yml` y se autentican en memoria; el `SecurityFilterChain` es el punto de extensión natural para sustituir por LDAP/OIDC cuando exista un IdP real.
- **Solapamiento = solapamiento estricto**: dos reservas se solapan si `inicioExistente < finNuevo && finExistente > inicioNuevo`. Una reserva que termina a las 10:00 y otra que empieza a las 10:00 **no** se solapan (los bordes no se consideran ocupados).
- **Bloqueo pesimista de la sala** (`SELECT ... FOR UPDATE` sobre `rooms`) al crear una reserva: serializa las verificaciones concurrentes para que dos peticiones simultáneas sobre la misma franja no se cuelen. Una restricción `UNIQUE` no serviría porque el conflicto es de rango, no de valor.
- **Cancelación lógica** (`status = CANCELADA`) en vez de borrado: se conserva el histórico y el horario queda libre de inmediato.
- **Mapeo de DTO dentro de la transacción** (`open-in-view: false`): evita `LazyInitializationException` y mantiene el contrato de la API desacoplado de la entidad.
- **Errores en JSON uniforme** con `@RestControllerAdvice`, incluyendo el detalle por campo de las validaciones y un `401`/`403` con cuerpo JSON en lugar de la página de error por defecto de Spring.

## Qué haría con más tiempo

- **JWT de verdad** (access + refresh token) y rotación de refresh tokens, para no depender de que el cliente envíe credenciales en cada escritura.
- **Tests con Testcontainers** sobre PostgreSQL real (además de los de H2), para validar que el SQL de la migración se comporta igual en ambos motores.
- **Endpoint de disponibilidad** (`GET /api/rooms/{id}/availability?date=...`) con huecos libres, que es lo que de verdad necesita un calendario en pantalla.
- **Reservas recurrentes** y **buffer de transición** entre reuniones (5–15 min entre una y otra).
- **Notificaciones** (coro/Teams) al reservar y al cancelar, y recordatorios.
- **OpenAPI/Swagger** con springdoc y contrato versionado.
- **Auditoría de cambios** (quién creó/canceló cada reserva) y perfil de administrador para forzar cancelaciones.

## Cómo usé la IA

- **Qué le pedí**: la implementación completa del proyecto a partir del enunciado (estructura Maven, entidades, migraciones, servicios, controladores, seguridad, tests) y el README. Escribí yo el diseño y las reglas de negocio: solapamiento estricto, bordes que no se solapan, cancelación lógica, bloqueo pesimista de la sala y el formato de errores.
- **Qué acepté**: la estructura en capas (controller → service → repository), las anotaciones de Bean Validation, el `@RestControllerAdvice` para los errores y la configuración de Spring Security; la parte repetitiva (DTOs, getters, wiring) la acepté tal cual, revisando nombres y mensajes.
- **Qué corregí**: el `pom.xml` (el módulo `flyway-database-h2` no existe para esa versión; el soporte de H2 ya viene en `flyway-core`), el manejo de `LazyInitializationException` al mapear entidades fuera de la transacción, la consulta de filtros con parámetros nulos (la sustituí por `Specification` de JPA) y los tests unitarios con mocks mal alineados por el `id` nulo de una entidad no persistida.
- **Qué revisé y acepté con verificación propia**: la colección de Postman no se dio por buena hasta ejecutarla con `newman` contra la aplicación real (18 peticiones, 29 aserciones, 0 fallos) y el test de concurrencia hasta verlo fallar con dos reservas aceptadas y ajustarlo.
- **Qué NO le dejé decidir**: el modelo de datos y las reglas de solapamiento/cancelación, que son lo que más se nota en una revisión de código.

## Estructura

```
src/main/java/com/example/reservation
├── config/     SecurityConfig (HTTP Basic, rutas públicas y protegidas)
├── domain/     Room, Reservation, ReservationStatus
├── repository/ RoomRepository, ReservationRepository (+ Specification para filtros)
├── service/    RoomService, ReservationService (+ exceptions)
└── web/        RoomController, ReservationController, GlobalExceptionHandler, dto/
src/main/resources/db/migration/V1__init.sql   esquema + salas semilla
src/test/java/...                               unitarios (Mockito) + integración (MockMvc)
```
