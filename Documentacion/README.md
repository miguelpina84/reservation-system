# Documentación de pruebas — API de reservas de salas

Esta carpeta contiene los artefactos para probar la API de forma manual y reproducible.

## Contenido

| Archivo | Qué es |
|---|---|
| `reservation-system.postman_collection.json` | Colección Postman (v2.1) con los casos de éxito y de error, y scripts de aserción. |
| `postman_environment_local.json` | Variables de entorno para desarrollo local (URL, credenciales). |

## Requisitos

- La API corriendo (por defecto con H2 en memoria):

  ```bash
  mvn spring-boot:run
  ```

- [Postman](https://www.postman.com/downloads/) o `newman` (CLI).

## Uso con Postman

1. Importa la colección: **Import → Link/Upload → `reservation-system.postman_collection.json`**.
2. Importa el entorno: **Import → `postman_environment_local.json`** y selecciona *reservation-system - Local*.
3. Ejecuta la colección completa con el **Collection Runner** (o `Ctrl+R` sobre la colección).

Las variables `{{roomId}}` y `{{reservationToCancel}}` se rellenan solas al ejecutar las peticiones que las producen, por lo que el orden de las carpetas importa:

1. `00 Salud` — el servicio responde.
2. `01 Salas` — crea una sala (`{{roomId}}`) y verifica el listado público.
3. `02 Reservas` — crea una reserva, comprueba el **409** al duplicar el horario y el **400** con `fieldErrors` en un payload inválido; lista con filtro por sala y fecha.
4. `03 Cancelación` — crea, cancela y comprueba el **409** al cancelar dos veces.
5. `04 Autenticación y errores` — **401** en las tres operaciones de escritura sin credenciales, **404** de recurso inexistente, **400** de nombre duplicado y de rango horario inválido.

> La carpeta `04` espera que exista la sala semilla **"Sala Ada"** (viene en la migración `V1__init.sql`) para el caso de nombre duplicado.

## Uso con Newman (CI)

```bash
npm install -g newman
newman run reservation-system.postman_collection.json \
  -e postman_environment_local.json \
  --reporters cli,html --reporter-html-export report.html
```

El comando sale con código distinto de 0 si alguna aserción falla, así que sirve como paso de una tubería de CI.

## Casos cubiertos

| Caso | Respuesta esperada |
|---|---|
| `GET /actuator/health` | 200, `status: UP` |
| `GET /api/rooms` (sin credenciales) | 200, arreglo no vacío |
| `POST /api/rooms` (con credenciales) | 201, guarda `{{roomId}}` |
| `POST /api/rooms` (sin credenciales) | 401 |
| `POST /api/rooms` con nombre duplicado | 400, "Ya existe una sala..." |
| `GET /api/rooms/999999` | 404 |
| `POST /api/reservations` | 201, `status: ACTIVA` |
| `POST /api/reservations` en horario ocupado | 409, "ya está ocupada..." |
| `POST /api/reservations` con payload inválido | 400, `fieldErrors` por campo |
| `POST /api/reservations` con `inicio > fin` | 400, regla de negocio |
| `POST /api/reservations` (sin credenciales) | 401 |
| `GET /api/reservations?roomId=&date=&page=&size=` | 200, paginado |
| `DELETE /api/reservations/{id}` | 200, `status: CANCELADA` |
| `DELETE` dos veces la misma reserva | 409, "ya estaba cancelada" |
| `DELETE` (sin credenciales) | 401 |

## Comprobaciones que no cubre Postman

El **conflicto por concurrencia** (dos peticiones simultáneas por el mismo horario) no se puede provocar de forma fiable desde Postman; está automatizado en `ReservationConcurrencyTest` y se ejecuta con `mvn test`.
