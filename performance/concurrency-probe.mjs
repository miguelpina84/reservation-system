/**
 * Sonda de concurrencia sin dependencias externas (Node >= 18).
 * Complementa al escenario de k6 para medir rapido en local o en un pipeline sin Docker.
 *
 *   node performance/concurrency-probe.mjs [http://localhost:18080] [100]
 *
 * Escenario 1 - mismo horario: 1 debe ganar (201) y el resto recibir 409.
 * Escenario 2 - horarios distintos: todos deben crear (201) y se mide la latencia.
 */
const BASE_URL = process.argv[2] || process.env.BASE_URL || 'http://localhost:2222';
const CONCURRENCIA = Number(process.argv[3] || 100);
const AUTH = 'Basic ' + Buffer.from('admin:admin123').toString('base64');

const headersJson = {
  'Content-Type': 'application/json',
  Authorization: AUTH,
};

function percentile(values, p) {
  if (!values.length) return 0;
  const sorted = [...values].sort((a, b) => a - b);
  const index = Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1);
  return Math.round(sorted[index]);
}

async function timedRequest(url, options) {
  const inicio = performance.now();
  const res = await fetch(url, options);
  const cuerpo = await res.text();
  return { status: res.status, ms: performance.now() - inicio, cuerpo };
}

async function main() {
  console.log(`\nSonda de concurrencia contra ${BASE_URL} (${CONCURRENCIA} peticiones en paralelo)\n`);

  const sala = await fetch(`${BASE_URL}/api/rooms`, {
    method: 'POST',
    headers: headersJson,
    body: JSON.stringify({ name: `Sala Sonda ${Date.now()}`, capacity: 100 }),
  });
  if (sala.status !== 201) {
    console.error(`No se pudo crear la sala: ${sala.status} ${await sala.text()}`);
    process.exit(1);
  }
  const roomId = (await sala.json()).id;
  console.log(`Sala creada: ${roomId}\n`);

  // --- Escenario 1: todos por el MISMO horario ---
  const inicio = '2033-06-01T10:00:00';
  const fin = '2033-06-01T11:00:00';
  const payload = JSON.stringify({ roomId, responsable: 'Sonda', startTime: inicio, endTime: fin });

  const mismos = await Promise.all(
    Array.from({ length: CONCURRENCIA }, () =>
      timedRequest(`${BASE_URL}/api/reservations`, { method: 'POST', headers: headersJson, body: payload })
    )
  );

  const creadas = mismos.filter((r) => r.status === 201).length;
  const conflictos = mismos.filter((r) => r.status === 409).length;
  const otros = mismos.filter((r) => r.status !== 201 && r.status !== 409);
  const latenciasMismo = mismos.map((r) => r.ms);

  console.log('--- Escenario 1: N peticiones al MISMO horario ---');
  console.log(`Creadas (201):   ${creadas}`);
  console.log(`Conflictos (409): ${conflictos}`);
  console.log(`Otros estados:   ${otros.length}${otros.length ? ' -> ' + otros.map((r) => r.status).join(',') : ''}`);
  console.log(`Latencia p50/p95/max: ${percentile(latenciasMismo, 50)}ms / ${percentile(latenciasMismo, 95)}ms / ${Math.round(Math.max(...latenciasMismo))}ms`);
  console.log(creadas === 1 && otros.length === 0 ? 'OK: una sola reserva creada, el resto en conflicto' : 'FALLO: el bloqueo de concurrencia no se comporto como se espera');

  // --- Escenario 2: cada peticion con su propio horario (30 s cada uno, sin solaparse) ---
  const distintos = await Promise.all(
    Array.from({ length: CONCURRENCIA }, (_, i) => {
      const s = new Date(2033, 5, 2, 0, 0, 0);
      s.setMinutes(s.getMinutes() + i);
      const e = new Date(s.getTime() + 30 * 1000);
      const iso = (d) => d.toISOString().slice(0, 19);
      return timedRequest(`${BASE_URL}/api/reservations`, {
        method: 'POST',
        headers: headersJson,
        body: JSON.stringify({ roomId, responsable: `Sonda ${i}`, startTime: iso(s), endTime: iso(e) }),
      });
    })
  );

  const ok = distintos.filter((r) => r.status === 201).length;
  const latencias = distintos.map((r) => r.ms);
  const duracionTotal = Math.max(...distintos.map((r) => r.ms));
  const reparto = distintos.reduce((acc, r) => {
    acc[r.status] = (acc[r.status] || 0) + 1;
    return acc;
  }, {});
  const ejemploError = distintos.find((r) => r.status !== 201);

  console.log('\n--- Escenario 2: N peticiones en horarios DISTINTOS ---');
  console.log(`Creadas (201):   ${ok}/${CONCURRENCIA}`);
  console.log(`Reparto de estados: ${JSON.stringify(reparto)}`);
  if (ejemploError) {
    console.log(`Ejemplo de respuesta no 201: ${ejemploError.cuerpo.slice(0, 300)}`);
  }
  console.log(`Latencia p50/p95/max: ${percentile(latencias, 50)}ms / ${percentile(latencias, 95)}ms / ${Math.round(Math.max(...latencias))}ms`);
  console.log(`Throughput estimado: ~${Math.round(CONCURRENCIA / (duracionTotal / 1000))} escrituras/s en el pico`);

  const todoOk = creadas === 1 && otros.length === 0 && ok === CONCURRENCIA;
  console.log(`\nResultado global: ${todoOk ? 'TODO OK' : 'REVISAR'}\n`);
  process.exit(todoOk ? 0 : 1);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
