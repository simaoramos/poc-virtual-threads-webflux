import http from 'k6/http';
import { check } from 'k6';

// Modelo fechado: cada usuário virtual dispara a próxima requisição
// assim que a anterior termina, sem pausa.
export const options = {
  vus: Number(__ENV.VUS || 100),
  duration: __ENV.DURATION || '1m',
  summaryTrendStats: ['avg', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  const res = http.get(__ENV.TARGET, { timeout: '10s' });
  check(res, { 'status 200': (r) => r.status === 200 });
}
