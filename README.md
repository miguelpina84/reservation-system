# reservation-system

API REST para reservar salas de reuniones. Java 17+ / Spring Boot 3.3 / H2 (por defecto) o PostgreSQL / Flyway / Spring Security (HTTP Basic).

## Cómo ejecutarlo

Requisitos: JDK 17 o superior y Maven 3.9+.

```bash
# 1. Tests
mvn test

# 2. Arranque (H2 en memoria, datos semilla de la migración V1)
mvn spring-boot:run

# 3. o bien, empaquetado
mvn clean package
java -jar target/reservation-system-0.0.1-SNAPSHOT.jar
```

La app escucha en `http://localhost:8080`.

### Con PostgreSQL

```bash
docker compose up -d postgres
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

o con variables de entorno: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.

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
- **Tests con Testcontainers** sobre PostgreSQL real (además de los de H2), para validar que el SQL de la migración se comporta igual en ambos motores y para probar concurrencia real con dos hilos pidiendo el mismo horario.
- **Endpoint de disponibilidad** (`GET /api/rooms/{id}/availability?date=...`) con huecos libres, que es lo que de verdad necesita un calendario en pantalla.
- **Reservas recurrentes** y **buffer de transición** entre reuniones (5–15 min entre una y otra).
- **Notificaciones** (coro/Teams) al reservar y al cancelar, y recordatorios.
- **OpenAPI/Swagger** con springdoc y contrato versionado.
- **Auditoría de cambios** (quién creó/canceló cada reserva) y perfil de administrador para forzar cancelaciones.

## Cómo usé la IA

- **Qué le pedí**: la implementación completa del proyecto a partir del enunciado (estructura Maven, entidades, migraciones, servicios, controladores, seguridad, tests) y el README. Escribí yo el diseño y las reglas de negocio: solapamiento estricto, bordes que no se solapan, cancelación lógica, bloqueo pesimista de la sala y el formato de errores.
- **Qué acepté**: la estructura en capas (controller → service → repository), las anotaciones de Bean Validation, el `@RestControllerAdvice` para los errores y la configuración de Spring Security; la parte repetitiva (DTOs, getters, wiring) la acepté tal cual, revisando nombres y mensajes.
- **Qué corregí**: el `pom.xml` (el módulo `flyway-database-h2` no existe para esa versión; el soporte de H2 ya viene en `flyway-core`), el manejo de `LazyInitializationException` al mapear entidades fuera de la transacción, la consulta de filtros con parámetros nulos (la sustituí por `Specification` de JPA) y los tests unitarios con mocks mal alineados por el `id` nulo de una entidad no persistida.
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
