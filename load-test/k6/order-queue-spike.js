import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';

const orderBaseUrl = __ENV.ORDER_BASE_URL || 'http://host.docker.internal:8084';
const productId = Number(__ENV.PRODUCT_ID || 1);
const stock = Number(__ENV.STOCK || 100);
const targetRate = Number(__ENV.TARGET_RATE || 200);
const duration = __ENV.DURATION || '5s';
const preAllocatedVUs = Number(__ENV.PRE_ALLOCATED_VUS || 100);
const maxVUs = Number(__ENV.MAX_VUS || 500);
const warmupRequests = Number(__ENV.WARMUP_REQUESTS || 20);
const runId = __ENV.RUN_ID || String(Date.now());
const memberIdBase = Number(__ENV.MEMBER_ID_BASE || Date.now() * 1000);

const acceptedCount = new Counter('order_queue_accepted_count');
const unexpectedResponseRate = new Rate('order_queue_unexpected_response_rate');
const acceptedDuration = new Trend('order_queue_accepted_duration', true);

const terminalStatuses = new Set(['SUCCEEDED', 'SOLD_OUT', 'REJECTED', 'FAILED']);

http.setResponseCallback(http.expectedStatuses(200, 202));

export const options = {
  scenarios: {
    queue_spike: {
      executor: 'constant-arrival-rate',
      rate: targetRate,
      timeUnit: '1s',
      duration,
      preAllocatedVUs,
      maxVUs,
      gracefulStop: '5s',
    },
  },
  thresholds: {
    order_queue_unexpected_response_rate: ['rate==0'],
    dropped_iterations: ['count==0'],
    checks: ['rate==1'],
  },
};

export function setup() {
  if (warmupRequests > 0) {
    prepareProduct(warmupRequests);

    const warmupOrders = [];
    for (let i = 0; i < warmupRequests; i += 1) {
      const memberId = memberIdBase - warmupRequests + i;
      const response = enqueue(memberId, `queue-warmup-${runId}-${i}`);

      if (response.status !== 202) {
        throw new Error(`대기열 워밍업 접수에 실패했습니다: ${response.status} ${response.body}`);
      }

      warmupOrders.push({
        memberId,
        requestId: response.json('requestId'),
      });
    }

    waitUntilTerminal(warmupOrders);
  }

  prepareProduct(stock);

  return { memberIdBase, runId };
}

export default function (data) {
  const iteration = exec.scenario.iterationInTest;
  const memberId = data.memberIdBase + iteration;
  const response = enqueue(memberId, `queue-load-${data.runId}-${iteration}`);
  const accepted = response.status === 202 && typeof response.json('requestId') === 'string';

  acceptedCount.add(accepted ? 1 : 0);
  unexpectedResponseRate.add(!accepted);
  if (accepted) acceptedDuration.add(response.timings.duration);

  check(response, {
    '주문이 대기열에 정상 접수됨': () => accepted,
  });
}

function prepareProduct(quantity) {
  const response = http.post(
    `${orderBaseUrl}/load-tests/orders/setup`,
    JSON.stringify({ productId, quantity }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { name: 'load_test_setup' },
    },
  );

  if (response.status !== 200) {
    throw new Error(`주문 테스트 데이터 준비에 실패했습니다: ${response.status} ${response.body}`);
  }
}

function enqueue(memberId, idempotencyKey) {
  return http.post(
    `${orderBaseUrl}/load-tests/orders/requests`,
    JSON.stringify({ productId, quantity: 1 }),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-USER-ID': String(memberId),
        'Idempotency-Key': idempotencyKey,
      },
      tags: { name: 'order_queue_enqueue' },
    },
  );
}

function waitUntilTerminal(orders) {
  const pending = new Map(orders.map((order) => [order.requestId, order.memberId]));
  const deadline = Date.now() + 30000;

  while (pending.size > 0 && Date.now() < deadline) {
    for (const [requestId, memberId] of pending.entries()) {
      const response = http.get(
        `${orderBaseUrl}/load-tests/orders/requests/${requestId}`,
        {
          headers: { 'X-USER-ID': String(memberId) },
          tags: { name: 'order_queue_warmup_status' },
        },
      );

      if (response.status === 200 && terminalStatuses.has(response.json('status'))) {
        pending.delete(requestId);
      }
    }

    if (pending.size > 0) sleep(0.1);
  }

  if (pending.size > 0) {
    throw new Error(`대기열 워밍업 ${pending.size}건이 제한 시간 안에 완료되지 않았습니다.`);
  }
}
