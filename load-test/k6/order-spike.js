import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';

const orderBaseUrl = __ENV.ORDER_BASE_URL || 'http://host.docker.internal:8084';
const method = __ENV.ORDER_METHOD || 'skip-locked';
const productId = Number(__ENV.PRODUCT_ID || 1);
const stock = Number(__ENV.STOCK || 100);
const targetRate = Number(__ENV.TARGET_RATE || 200);
const duration = __ENV.DURATION || '10s';
const preAllocatedVUs = Number(__ENV.PRE_ALLOCATED_VUS || 200);
const maxVUs = Number(__ENV.MAX_VUS || 1000);
const warmupRequests = Number(__ENV.WARMUP_REQUESTS || 20);

const successCount = new Counter('order_success_count');
const rejectionCount = new Counter('order_business_rejection_count');
const unexpectedResponseRate = new Rate('order_unexpected_response_rate');
const acceptedDuration = new Trend('order_accepted_duration', true);
const rejectedDuration = new Trend('order_rejected_duration', true);
const businessDuration = new Trend('order_business_duration', true);

http.setResponseCallback(http.expectedStatuses(200, 201, 409));

export const options = {
  scenarios: {
    flash_sale: {
      executor: 'ramping-arrival-rate',
      startRate: Math.max(1, Math.floor(targetRate / 4)),
      timeUnit: '1s',
      preAllocatedVUs,
      maxVUs,
      stages: [
        { target: targetRate, duration: '3s' },
        { target: targetRate, duration },
        { target: 0, duration: '2s' },
      ],
    },
  },
  thresholds: {
    order_unexpected_response_rate: ['rate==0'],
    checks: ['rate>0.99'],
  },
};

export function setup() {
  const path = method === 'pessimistic'
    ? '/load-tests/orders/reservations/pessimistic'
    : '/load-tests/orders/reservations/skip-locked';

  const warmupReset = http.post(
    `${orderBaseUrl}/load-tests/orders/setup`,
    JSON.stringify({ productId, quantity: warmupRequests }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  if (warmupReset.status !== 200) {
    throw new Error(`Could not reset warm-up data: ${warmupReset.status} ${warmupReset.body}`);
  }

  for (let i = 0; i < warmupRequests; i += 1) {
    const memberId = `${Date.now()}${String(i).padStart(3, '0')}`;
    const response = http.post(
      `${orderBaseUrl}${path}`,
      JSON.stringify({ productId, quantity: 1 }),
      {
        headers: {
          'Content-Type': 'application/json',
          'X-USER-ID': memberId,
        },
      },
    );
    if (response.status !== 201) {
      throw new Error(`Warm-up request failed: ${response.status} ${response.body}`);
    }
  }

  const reset = http.post(
    `${orderBaseUrl}/load-tests/orders/setup`,
    JSON.stringify({ productId, quantity: stock }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  check(reset, { 'load-test data reset succeeds': (response) => response.status === 200 });
  if (reset.status !== 200) {
    throw new Error(`Could not reset test data: ${reset.status} ${reset.body}`);
  }
  return { memberIdBase: Date.now() * 1000 };
}

export default function (data) {
  const memberId = data.memberIdBase + exec.scenario.iterationInTest;
  const path = method === 'pessimistic'
    ? '/load-tests/orders/reservations/pessimistic'
    : '/load-tests/orders/reservations/skip-locked';

  const response = http.post(
    `${orderBaseUrl}${path}`,
    JSON.stringify({ productId, quantity: 1 }),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-USER-ID': String(memberId),
      },
      tags: { order_method: method },
    },
  );

  const accepted = response.status === 201;
  const rejected = response.status === 409;
  const expected = accepted || rejected;

  businessDuration.add(response.timings.duration);
  successCount.add(accepted ? 1 : 0);
  rejectionCount.add(rejected ? 1 : 0);
  unexpectedResponseRate.add(!expected);
  if (accepted) acceptedDuration.add(response.timings.duration);
  if (rejected) rejectedDuration.add(response.timings.duration);

  check(response, {
    'order is accepted or rejected by business rule': () => expected,
  });
}

export function teardown() {
  const stateResponse = http.get(
    `${orderBaseUrl}/load-tests/orders/products/${productId}/state`,
  );
  const state = stateResponse.status === 200 ? stateResponse.json() : null;

  check(stateResponse, {
    'state endpoint succeeds': (response) => response.status === 200,
    'successful reservations never exceed stock': () =>
      state !== null && Number(state.pendingReservations) <= stock,
    'exactly the configured stock is reserved': () =>
      state !== null && Number(state.pendingReservations) === stock,
    'DB unit stock invariant holds': () =>
      state !== null && (
        method === 'pessimistic'
          ? Number(state.productStockColumn) + Number(state.pendingReservations) === stock
          : Number(state.availableUnitStocks) + Number(state.pendingReservations) === stock
      ),
  });

  if (state !== null) {
    console.log(`FINAL_STATE ${JSON.stringify(state)}`);
  }
}
