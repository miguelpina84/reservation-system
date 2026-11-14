#!/usr/bin/env bash
# Smoke test de la API contra el contenedor en ejecucion.
#   bash scripts/smoke-test.sh [http://localhost:8080]
# Espera a que la app responda y comprueba el camino feliz y los errores clave.
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
AUTH_ADMIN="${AUTH_ADMIN:-admin:admin123}"
AUTH_USER="${AUTH_USER:-user:user123}"

echo "Esperando a $BASE_URL ..."
for i in $(seq 1 60); do
  if curl -fsS "$BASE_URL/actuator/health" | grep -q UP; then
    echo "Aplicacion lista"
    break
  fi
  if [ "$i" = "60" ]; then
    echo "La aplicacion no levanto a tiempo"
    exit 1
  fi
  sleep 2
done

# Crear sala (requiere credenciales). El nombre lleva timestamp para que el
# script se pueda repetir sobre la misma base de datos.
ROOM_NAME="Sala Smoke $(date +%s)"
ROOM_JSON=$(curl -fsS -u "$AUTH_ADMIN" -H 'Content-Type: application/json' \
  -d "{\"name\":\"$ROOM_NAME\",\"capacity\":8}" "$BASE_URL/api/rooms")
ROOM_ID=$(python3 -c "import json,sys;print(json.loads(sys.argv[1])['id'])" "$ROOM_JSON")
echo "Sala creada: $ROOM_ID"

# Crear reserva
curl -fsS -u "$AUTH_USER" -H 'Content-Type: application/json' \
  -d "{\"roomId\":$ROOM_ID,\"responsable\":\"Smoke\",\"startTime\":\"2033-03-01T10:00:00\",\"endTime\":\"2033-03-01T11:00:00\"}" \
  "$BASE_URL/api/reservations" > /dev/null
echo "Reserva creada"

# El mismo horario debe devolver 409
STATUS=$(curl -s -o /dev/null -w '%{http_code}' -u "$AUTH_USER" -H 'Content-Type: application/json' \
  -d "{\"roomId\":$ROOM_ID,\"responsable\":\"Smoke\",\"startTime\":\"2033-03-01T10:00:00\",\"endTime\":\"2033-03-01T11:00:00\"}" \
  "$BASE_URL/api/reservations")
test "$STATUS" = "409" || { echo "Se esperaba 409 y se obtuvo $STATUS"; exit 1; }
echo "409 en horario ocupado: OK"

# Una escritura sin credenciales debe devolver 401
STATUS=$(curl -s -o /dev/null -w '%{http_code}' -H 'Content-Type: application/json' \
  -d '{"name":"Sala Sin Auth","capacity":4}' "$BASE_URL/api/rooms")
test "$STATUS" = "401" || { echo "Se esperaba 401 y se obtuvo $STATUS"; exit 1; }
echo "401 sin credenciales: OK"

# La lectura es publica
curl -fsS "$BASE_URL/api/rooms" > /dev/null
echo "Lectura publica: OK"

echo "Smoke test OK"
