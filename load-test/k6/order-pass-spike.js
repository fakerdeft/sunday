import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';

// 주문 서버 앞단 게이트 부하 테스트.
//   gate-api : 통행증 요청 -> 통과 또는 품절 (즉시 응답)
//   order-api : 통과한 요청만 주문
const gateBaseUrl = __ENV.GATE_BASE_URL || 'http://host.docker.internal:8085';
const orderBaseUrl = __ENV.ORDER_BASE_URL || 'http://host.docker.internal:8084';
const productId = Number(__ENV.PRODUCT_ID || 1);
const stock = Number(__ENV.STOCK || 100);
const targetRate = Number(__ENV.TARGET_RATE || 200);
const duration = __ENV.DURATION || '5s';
const preAllocatedVUs = Number(__ENV.PRE_ALLOCATED_VUS || 300);
const maxVUs = Number(__ENV.MAX_VUS || 1000);
const warmupRequests = Number(__ENV.WARMUP_REQUESTS || 20);

const passDuration = new Trend('pass_request_duration', true);
const orderDuration = new Trend('pass_order_duration', true);
const journeyDuration = new Trend('pass_journey_duration', true);

const reservedCount = new Counter('pass_reserved_count');
const soldOutAtGateCount = new Counter('pass_sold_out_at_gate_count');
const soldOutAtOrderCount = new Counter('pass_sold_out_at_order_count');
const notAdmittedCount = new Counter('pass_not_admitted_count');

// 서버별 요청 수. 대기 트래픽이 주문 서버에 도달하지 않는지 확인하기 위한 지표다.
const gateApiRequests = new Counter('pass_gate_api_requests');
const orderApiRequests = new Counter('pass_order_api_requests');

const unexpectedResponseRate = new Rate('pass_unexpected_response_rate');

http.setResponseCallback(http.expectedStatuses(200, 201, 204, 403, 409));

export const options = {
  scenarios: {
    gate_spike: {
      executor: 'constant-arrival-rate',
      rate: targetRate,
      timeUnit: '1s',
      duration,
      preAllocatedVUs,
      maxVUs,
      gracefulStop: '30s',
    },
  },
  thresholds: {
    pass_unexpected_response_rate: ['rate==0'],
    dropped_iterations: ['count==0'],
  },
};

export function setup() {
  prepareProduct(warmupRequests > 0 ? warmupRequests : stock);

  for (let i = 0; i < warmupRequests; i += 1) {
    runJourney(-(i + 1), true);
  }

  prepareProduct(stock);

  return { memberIdBase: Date.now() * 1000 };
}

export default function (data) {
  runJourney(data.memberIdBase + exec.scenario.iterationInTest, false);
}

function runJourney(memberId, isWarmup) {
  // 워밍업은 JIT 예열이 목적이므로 지표에 섞지 않는다.
  const record = !isWarmup;
  const startedAt = Date.now();

  const gate = http.post(
    `${gateBaseUrl}/api/order-pass/${productId}`,
    null,
    { headers: { 'X-USER-ID': String(memberId) }, tags: { name: 'order_pass' } },
  );

  if (record) gateApiRequests.add(1);

  if (gate.status !== 200) {
    if (record) unexpectedResponseRate.add(true);

    return;
  }

  const pass = gate.json();

  if (record) {
    unexpectedResponseRate.add(false);
    passDuration.add(gate.timings.duration);
    check(gate, { 'gate answered': () => pass !== null });
  }

  // 재고가 없으면 여기서 끝난다. 주문 서버로 가지 않는다.
  if (pass.status !== 'PASSED') {
    if (record) {
      soldOutAtGateCount.add(1);
      journeyDuration.add(Date.now() - startedAt);
    }

    return;
  }

  const ordered = http.post(
    `${orderBaseUrl}/api/orders/reservations`,
    JSON.stringify({ productId, quantity: 1 }),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-USER-ID': String(memberId),
        'X-ADMISSION-TOKEN': pass.token,
      },
      tags: { name: 'order_reservation' },
    },
  );

  if (record) {
    orderApiRequests.add(1);
    orderDuration.add(ordered.timings.duration);
    journeyDuration.add(Date.now() - startedAt);
  }

  // 주문이 끝났으면 통행증을 반납해 다음 사람이 바로 들어올 수 있게 한다.
  http.del(
    `${gateBaseUrl}/api/order-pass/${productId}`,
    null,
    { headers: { 'X-USER-ID': String(memberId) }, tags: { name: 'order_pass_release' } },
  );

  if (record) gateApiRequests.add(1);

  if (ordered.status === 201) {
    if (record) {
      reservedCount.add(1);
      unexpectedResponseRate.add(false);
    }

    return;
  }

  if (ordered.status === 409) {
    if (record) {
      soldOutAtOrderCount.add(1);
      unexpectedResponseRate.add(false);
    }

    return;
  }

  if (ordered.status === 403) {
    if (record) {
      notAdmittedCount.add(1);
      unexpectedResponseRate.add(false);
    }

    return;
  }

  if (record) unexpectedResponseRate.add(true);
}

function prepareProduct(quantity) {
  const response = http.post(
    `${orderBaseUrl}/load-tests/orders/setup`,
    JSON.stringify({ productId, quantity }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'load_test_setup' } },
  );

  if (response.status !== 200) {
    throw new Error(`주문 테스트 데이터 준비에 실패했습니다: ${response.status} ${response.body}`);
  }

  // 주문 서버 재고를 맞춘 뒤 게이트 상태를 초기화하고 재고를 다시 읽어 오게 한다.
  const reset = http.post(`${gateBaseUrl}/load-tests/order-pass/${productId}/reset`);

  if (reset.status !== 204) {
    throw new Error(`게이트 초기화에 실패했습니다: ${reset.status} ${reset.body}`);
  }
}

export function teardown() {
  const stateResponse = http.get(`${orderBaseUrl}/load-tests/orders/products/${productId}/state`);
  const state = stateResponse.status === 200 ? stateResponse.json() : null;

  check(stateResponse, {
    'state endpoint succeeds': (response) => response.status === 200,
    'successful reservations never exceed stock': () =>
      state !== null && Number(state.pendingReservations) <= stock,
    'exactly the configured stock is reserved': () =>
      state !== null && Number(state.pendingReservations) === stock,
    'DB unit stock invariant holds': () =>
      state !== null &&
      Number(state.availableUnitStocks) + Number(state.pendingReservations) === stock,
  });

  if (state !== null) {
    console.log(`FINAL_STATE ${JSON.stringify(state)}`);
  }
}
