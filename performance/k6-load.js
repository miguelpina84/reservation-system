import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:2222';
const AUTH = `Basic ${__ENV.AUTH_BASIC || 'YWRtaW46YWRtaW4xMjM='}`; // admin:admin123

const creadas = new Counter('reservas_creadas');
const conflictos = new Counter('reservas_en_conflicto');
const otrosErrores = new Counter('reservas_con_error');
const latenciaCrear = new Trend('latencia_crear_reserva', true);

const headersJson = {
  'Content-Type': 'application/json',
  Authorization: AUTH,
};

/**
 * Prepara el escenario: crea una sala y devuelve su id.
 */
export function setup() {
  const nombre = `Sala Carga ${Date.now()}`;
  const res = http.post(`${BASE_URL}/api/rooms`, JSON.stringify({ name: nombre, capacity: 50 }), { headers: headersJson });
  if (res.status !== 201) {
    throw new Error(`No se pudo crear la sala para la prueba: ${res.status} ${res.body}`);
  }
  const sala = res.json();
  return { roomId: sala.id };
}

/**
 * Escenario 1 - conflicto bajo concurrencia:
 * muchas peticiones simultaneas por el MISMO horario en la MISMA sala.
 * Solo una debe ganar (201); todas las demas deben recibir 409.
 */
export function conflictoConcurrente(data) {
  const inicio = '2033-01-01T10:00:00';
  const fin = '2033-01-01T11:00:00';
  const payload = JSON.stringify({
    roomId: data.roomId,
    responsable: `Usuario Carga ${__VU}`,
    startTime: inicio,
    endTime: fin,
  });

  const res = http.post(`${BASE_URL}/api/reservations`, payload, { headers: headersJson });
  latenciaCrear.add(res.timings.duration);

  if (res.status === 201) {
    creadas.add(1);
  } else if (res.status === 409) {
    conflictos.add(1);
  } else {
    otrosErrores.add(1);
    console.error(`Estado inesperado: ${res.status} ${res.body}`);
  }

  check(res, {
    'responde 201 o 409': (r) => r.status === 201 || r.status === 409,
    'no devuelve 500': (r) => r.status !== 500,
  });
}

/**
 * Escenario 2 - lectura sostenida: listado paginado, que es el endpoint de lectura calientisimo.
 */
export function lecturaSostenida() {
  const res = http.get(`${BASE_URL}/api/reservations?page=0&size=20`);
  check(res, {
    'listado responde 200': (r) => r.status === 200,
    'devuelve paginacion': (r) => r.body !== null && r.body.includes('totalElements'),
  });
  sleep(0.1);
}

export const options = {
  scenarios: {
    conflicto: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 50 },
        { duration: '20s', target: 50 },
        { duration: '10s', target: 0 },
      ],
      gracefulRampDown: '10s',
      exec: 'conflictoConcurrente',
      tags: { escenario: 'conflicto' },
    },
    lectura: {
      executor: 'constant-vus',
      vus: 20,
      duration: '40s',
      exec: 'lecturaSostenida',
      tags: { escenario: 'lectura' },
    },
  },
  thresholds: {
    // Ninguna peticion debe fallar por error del servidor
    http_req_failed: ['rate<0.01'],
    'http_req_duration{escenario:conflicto}': ['p(95)<2000'],
    'http_req_duration{escenario:lectura}': ['p(95)<1000'],
    checks: ['rate>0.99'],
  },
};

export function handleSummary(data) {
  const creadas = data.metrics.reservas_creadas ? data.metrics.reservas_creadas.values.count : 0;
  const conflictos = data.metrics.reservas_en_conflicto ? data.metrics.reservas_en_conflicto.values.count : 0;
  const otros = data.metrics.reservas_con_error ? data.metrics.reservas_con_error.values.count : 0;

  const resumen = [
    '',
    '===== RESUMEN DE LA PRUEBA DE CARGA =====',
    `Reservas creadas (201):      ${creadas}`,
    `Conflictos (409):             ${conflictos}`,
    `Errores inesperados:          ${otros}`,
    `Peticiones HTTP totales:      ${data.metrics.http_reqs ? data.metrics.http_reqs.values.count : 0}`,
    `Tasa de error HTTP:           ${data.metrics.http_req_failed ? data.metrics.http_req_failed.values.rate : 0}`,
    '==========================================',
    '',
  ].join('\n');

  return {
    stdout: resumen,
    'performance/summary.json': JSON.stringify(data, null, 2),
  };
}
