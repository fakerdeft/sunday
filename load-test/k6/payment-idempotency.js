import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';

const accountBaseUrl = __ENV.ACCOUNT_BASE_URL || 'http://host.docker.internal:8082';
const orderBaseUrl = __ENV.ORDER_BASE_URL || 'http://host.docker.internal:8084';
const paymentBaseUrl = __ENV.PAYMENT_BASE_URL || 'http://host.docker.internal:8083';
const keyMode = __ENV.KEY_MODE || 'same';
const requests = Number(__ENV.REQUESTS || 100);
const vus = Number(__ENV.VUS || 50);
const memberId = 1;
const productId = Number(__ENV.PRODUCT_ID || 1);

const completedCount = new Counter('payment_completed_response_count');
const conflictCount = new Counter('payment_conflict_response_count');
const unexpectedResponseRate = new Rate('payment_unexpected_response_rate');
const paymentDuration = new Trend('payment_response_duration', true);

http.setResponseCallback(http.expectedStatuses(200, 201, 409));

export const options = {
  scenarios: {
    duplicate_payment: {
      executor: 'shared-iterations',
      vus,
      iterations: requests,
      maxDuration: '30s',
    },
  },
  thresholds: {
    payment_unexpected_response_rate: ['rate==0'],
    checks: ['rate>0.99'],
  },
};

function jsonHeaders(userId) {
  return {
    headers: {
      'Content-Type': 'application/json',
      'X-USER-ID': String(userId),
    },
  };
}

function getAccount() {
  return http.get(`${accountBaseUrl}/api/accounts/me`, jsonHeaders(memberId));
}

function extractInteger(body, field) {
  const match = body.match(new RegExp(`"${field}"\\s*:\\s*(\\d+)`));
  if (match === null) {
    throw new Error(`Could not extract ${field} from response: ${body}`);
  }
  return match[1];
}

export function setup() {
  const reset = http.post(
    `${orderBaseUrl}/load-tests/orders/setup`,
    JSON.stringify({ productId, quantity: 1 }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  check(reset, { 'order data reset succeeds': (response) => response.status === 200 });

  const deposit = http.post(
    `${accountBaseUrl}/api/accounts/me/deposit`,
    JSON.stringify({ amount: 1000000, description: 'k6 idempotency test setup' }),
    jsonHeaders(memberId),
  );
  check(deposit, { 'account setup succeeds': (response) => response.status === 200 });

  const account = getAccount();
  const reservation = http.post(
    `${orderBaseUrl}/load-tests/orders/reservations/skip-locked`,
    JSON.stringify({ productId, quantity: 1 }),
    jsonHeaders(memberId),
  );
  check(reservation, { 'reservation setup succeeds': (response) => response.status === 201 });

  if (reset.status !== 200 || deposit.status !== 200 || account.status !== 200 || reservation.status !== 201) {
    throw new Error('Could not prepare payment idempotency test data');
  }

  const reservationBody = reservation.json();
  return {
    // TSID is larger than JavaScript's safe integer range. Preserve its decimal text.
    reservationId: extractInteger(reservation.body, 'id'),
    amount: Number(reservationBody.totalAmount),
    balanceBefore: Number(account.json().balance),
    idempotencyKey: `k6-payment-${reservationBody.id}-${Date.now()}`,
  };
}

export default function (data) {
  const key = keyMode === 'same'
    ? data.idempotencyKey
    : `${data.idempotencyKey}-${exec.scenario.iterationInTest}`;

  const response = http.post(
    `${paymentBaseUrl}/api/payments`,
    `{"orderId":${data.reservationId},"idempotencyKey":${JSON.stringify(key)}}`,
    jsonHeaders(memberId),
  );

  const completed = response.status === 200 && response.json('status') === 'COMPLETED';
  const conflict = response.status === 409;
  const expected = keyMode === 'same' ? completed : completed || conflict;

  paymentDuration.add(response.timings.duration);
  completedCount.add(completed ? 1 : 0);
  conflictCount.add(conflict ? 1 : 0);
  unexpectedResponseRate.add(!expected);

  check(response, {
    'duplicate request follows the idempotency contract': () => expected,
  });
}

export function teardown(data) {
  const account = getAccount();
  const payment = http.get(`${paymentBaseUrl}/api/payments/order/${data.reservationId}`);
  const order = http.get(`${orderBaseUrl}/api/orders/${data.reservationId}`);

  const accountBody = account.status === 200 ? account.json() : null;
  const paymentBody = payment.status === 200 ? payment.json() : null;
  const orderBody = order.status === 200 ? order.json() : null;

  check(account, {
    'account is debited exactly once': () =>
      accountBody !== null && Number(accountBody.balance) === data.balanceBefore - data.amount,
  });
  check(payment, {
    'one payment reaches COMPLETED': () =>
      paymentBody !== null && paymentBody.status === 'COMPLETED',
  });
  check(order, {
    'one order reaches PAID': () => orderBody !== null && orderBody.status === 'PAID',
  });

  console.log(`FINAL_PAYMENT ${JSON.stringify(paymentBody)}`);
  console.log(`FINAL_ACCOUNT_BALANCE ${accountBody ? accountBody.balance : 'unavailable'}`);
}
