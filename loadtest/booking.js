// Run: GATEWAY_RATE=100000 GATEWAY_BURST=100000 docker compose up --build --scale booking-service=2
//      k6 run loadtest/booking.js        (FAIL_RATIO=0.1 BASE_URL=http://localhost:8080 are the defaults)
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const FAIL_RATIO = Number(__ENV.FAIL_RATIO || 0.1);

const e2e = new Trend('saga_e2e_ms', true);          // POST until the saga reaches a final state
const confirmed = new Counter('sagas_confirmed');
const cancelled = new Counter('sagas_cancelled');
const unfinished = new Counter('sagas_unfinished');
const wrongOutcome = new Counter('sagas_wrong_outcome'); // correctness under load

export const options = {
  scenarios: {
    bookings: {
      executor: 'ramping-arrival-rate',
      startRate: 5, timeUnit: '1s', preAllocatedVUs: 50, maxVUs: 400,
      stages: [
        { target: 20, duration: '30s' },
        { target: 50, duration: '1m' },
        { target: 50, duration: '1m' },
        { target: 0, duration: '15s' },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:create}': ['p(95)<500'],
    saga_e2e_ms: ['p(95)<3000'],
    sagas_unfinished: ['count==0'],
    sagas_wrong_outcome: ['count==0'],
  },
};

export default function () {
  const shouldFail = Math.random() < FAIL_RATIO;
  const started = Date.now();
  const res = http.post(
    `${BASE}/api/bookings`,
    JSON.stringify({ destination: 'Goa', budget: 30000, notes: shouldFail ? 'FAIL_HOTEL' : '' }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'create' } },
  );
  if (!check(res, { accepted: (r) => r.status === 202 })) return;
  const id = res.json('id');

  for (let i = 0; i < 100; i++) {
    sleep(0.2);
    const status = http.get(`${BASE}/api/bookings/${id}`, { tags: { name: 'poll' } }).json('status');
    if (status === 'CONFIRMED' || status === 'CANCELLED') {
      e2e.add(Date.now() - started);
      (status === 'CONFIRMED' ? confirmed : cancelled).add(1);
      if ((status === 'CANCELLED') !== shouldFail) wrongOutcome.add(1);
      return;
    }
  }
  unfinished.add(1);
}
