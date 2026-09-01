# Sunday 부하·동시성 검증

이 문서는 로컬 부하 테스트의 조건과 원자료를 남기기 위한 것이다. 수치는 운영 환경 처리량이 아니라 동일 장비에서 두 구현을 비교한 결과다.

## 환경

- 측정일: 2026-08-13
- CPU: Intel Core i7-8750H, 6코어 12스레드
- 메모리: 15.9 GiB
- OS: Windows 11
- JVM: Amazon Corretto 21.0.11
- PostgreSQL: 17.7 Docker 컨테이너
- k6: 2.2.0 Docker 컨테이너
- API: member/account/order/payment 각 1개 JVM 프로세스
- 별도 JVM 힙, Tomcat 스레드, Hikari 풀 튜닝 없음

k6, PostgreSQL, 네 JVM이 같은 노트북의 CPU와 메모리를 공유한다. 따라서 절대 TPS를 운영 용량으로 해석하지 않는다.

## 주문 재고 비교

### 비교 대상

- 기준선: `product`의 단일 재고 행을 `PESSIMISTIC_WRITE`로 잠근 뒤 차감
- 실제 경로: 수량별 `product_stock` 행을 `FOR UPDATE SKIP LOCKED`로 선점

두 방식 모두 초기 재고 100개에서 성공 예약이 정확히 100건인지, 초과 판매가 없는지 검증한다. `SKIP LOCKED` 방식은 최종 `availableUnitStocks + pendingReservations = initialStock`도 확인한다.

### 부하 모델

- 시작 50 RPS에서 3초 동안 200 RPS로 증가
- 200 RPS를 5초 유지한 뒤 2초 동안 0 RPS로 감소
- 예정 iteration: 1,574건
- pre-allocated VU 300, 최대 VU 1,000
- 매 실행 전 order JVM 재기동 및 관련 테이블 `VACUUM ANALYZE`
- 각 JVM 기동 후 해당 주문 경로 20건 워밍업, 테스트 데이터 재설정
- 실행 순서: SKIP LOCKED → 비관적 락 → 비관적 락 → SKIP LOCKED → SKIP LOCKED → 비관적 락

`dropped iterations`는 서버가 받은 요청을 잃었다는 뜻이 아니다. 이전 요청이 끝나지 않아 필요한 VU가 상한을 넘으면서 k6가 해당 요청을 시작하지 못한 수다.

### 원자료

| 방식 | 실행 | 비즈니스 요청 p95 | 성공 요청 p95 | 완료 iteration | dropped iteration | 최대 VU | 예상 밖 응답 |
|---|---:|---:|---:|---:|---:|---:|---:|
| SKIP LOCKED | 1 | 64.54 ms | 126.78 ms | 1,574 | 0 | 9 | 0 |
| SKIP LOCKED | 2 | 138.71 ms | 312.01 ms | 1,574 | 0 | 26 | 0 |
| SKIP LOCKED | 3 | 114.79 ms | 248.79 ms | 1,574 | 0 | 15 | 0 |
| 비관적 락 | 1 | 5,465.55 ms | 5,496.04 ms | 1,167 | 407 | 679 | 0 |
| 비관적 락 | 2 | 3,823.37 ms | 3,524.69 ms | 1,351 | 223 | 509 | 0 |
| 비관적 락 | 3 | 3,466.16 ms | 3,543.84 ms | 1,388 | 186 | 462 | 0 |

3회 중앙값은 다음과 같다.

| 지표 | 비관적 락 | SKIP LOCKED | 차이 |
|---|---:|---:|---:|
| 비즈니스 요청 p95 | 3,823.37 ms | 114.79 ms | 97.00% 감소, 33.31배 |
| 성공 요청 p95 | 3,543.84 ms | 248.79 ms | 92.98% 감소, 14.24배 |
| 완료 iteration | 1,351 | 1,574 | 예정 요청 전부 처리 |
| dropped iteration | 223 | 0 | 시작하지 못한 요청 제거 |
| 최대 VU | 509 | 15 | 대기 요청 누적 감소 |

모든 6회 실행에서 예약 성공은 100건, 예상 밖 응답은 0건이었고 DB 재고 불변식이 유지됐다. 중앙값만 제시할 때도 SKIP LOCKED 비즈니스 요청 p95가 64.54~138.71 ms였다는 실행 간 편차를 함께 공개한다.

원본 k6 summary:

- [SKIP LOCKED 1회](results/order-skip-locked-200rps-isolated-run1.json), [2회](results/order-skip-locked-200rps-isolated-run2.json), [3회](results/order-skip-locked-200rps-isolated-run3.json)
- [비관적 락 1회](results/order-pessimistic-200rps-isolated-run1.json), [2회](results/order-pessimistic-200rps-isolated-run2.json), [3회](results/order-pessimistic-200rps-isolated-run3.json)

### 해석 범위

- 확인한 것은 초과 판매 방지와 락 대기 감소다.
- `SKIP LOCKED`는 잠긴 행을 건너뛰므로 엄격한 요청 도착 순서를 보장하지 않는다.
- 이 결과는 `local` 전용 직접 처리 경로의 두 DB 락 방식을 비교한 기준선이다.
- 실제 주문 접수 경로의 Redis Streams 단일 워커는 아래 대기열 테스트에서 별도로 측정한다.

## 주문 대기열

### 부하 모델

- 측정일: 2026-08-14
- 실행 프로세스: order API 1개, PostgreSQL, Redis, k6
- 초기 재고 100개, 200 RPS로 5초간 주문 약 1,000건 접수
- 본 실행 전 동일 경로 20건 워밍업
- 개선 전: 스케줄이 실행될 때 메시지 1건만 처리하고 100ms 대기
- 연속 처리: 한 주기에서 `COUNT 1` 조회를 반복해 최대 100건을 처리한 뒤 100ms 대기
- 차단 묶음 조회: 기존 pending 메시지를 먼저 묶음 조회하고, 새 메시지는 `XREADGROUP BLOCK 1s`와 `COUNT`로 한 번에 최대 100건 조회한 뒤 100ms 대기

### 결과

| 방식 | 실행 | 접수 | dropped iteration | 예상 밖 응답 | 접수 p95 | 워커 처리량 | 전체 처리 p95 | 부하 종료 후 대기열 소진 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 단건 폴링 | 1 | 1,001건 | 0건 | 0건 | 23.28 ms | 7.47건/초 | 123.06초 | 127.51초 |
| 연속 처리 | 1 | 1,001건 | 0건 | 0건 | 28.91 ms | 36.33건/초 | 21.88초 | 21.46초 |
| 차단 묶음 조회 | 1 | 1,000건 | 0건 | 0건 | 16.98 ms | 40.05건/초 | 19.55초 | 17.94초 |
| 차단 묶음 조회 | 2 | 1,001건 | 0건 | 0건 | 13.23 ms | 33.64건/초 | 24.05초 | 23.43초 |
| 차단 묶음 조회 | 3 | 1,001건 | 0건 | 0건 | 9.02 ms | 36.45건/초 | 21.92초 | 20.37초 |

차단 묶음 조회 3회의 중앙값은 접수 p95 13.23ms, 워커 처리량 36.45건/초, 전체 처리 p95 21.92초, 대기열 소진 시간 20.37초다. 세 실행 모두 성공 100건, 나머지는 품절로 종료됐으며 거절·실패·재시도와 예상 밖 응답은 0건이었다. 최종 상태도 대기 예약 100건, 판매 가능 단위 재고 0건으로 초기 재고와 일치했다.

단건 폴링과 비교하면 워커 처리량은 4.88배가 됐고 전체 처리 p95는 82.2%, 대기열 소진 시간은 84.0% 감소했다. 연속 처리와 차단 묶음 조회의 처리량 및 소진 시간 차이는 작았다. 따라서 주된 성능 개선은 메시지마다 발생하던 100ms 대기를 제거한 결과이며, 차단 묶음 조회는 Redis 왕복 횟수와 빈 대기열 조회를 줄이는 구조 개선으로 해석한다.

원본 결과:

- 개선 전: [k6 접수 결과](results/order-queue-200rps-5s-run1.json), [처리 및 정합성 집계](results/order-queue-200rps-5s-run1-analysis.json)
- 연속 처리: [k6 접수 결과](results/order-queue-200rps-5s-run2.json), [처리 및 정합성 집계](results/order-queue-200rps-5s-run2-analysis.json)
- 차단 묶음 조회 1회: [k6 접수 결과](results/order-queue-blocking-batch-200rps-5s-run1.json), [처리 및 정합성 집계](results/order-queue-blocking-batch-200rps-5s-run1-analysis.json)
- 차단 묶음 조회 2회: [k6 접수 결과](results/order-queue-blocking-batch-200rps-5s-run2.json), [처리 및 정합성 집계](results/order-queue-blocking-batch-200rps-5s-run2-analysis.json)
- 차단 묶음 조회 3회: [k6 접수 결과](results/order-queue-blocking-batch-200rps-5s-run3.json), [처리 및 정합성 집계](results/order-queue-blocking-batch-200rps-5s-run3-analysis.json)

### 수만 명 접수 검증

초기 재고 100개인 핫딜에 서로 다른 회원이 주문을 한 건씩 요청하는 조건이다. 500 RPS에서 300 VU를 미리 할당하고 접수 시간을 늘려 1만 건과 2만 건을 검증했다.

| 요청 규모 | 접수 | dropped iteration | 접수 p95 | 워커 처리량 | 전체 처리 p95 | 부하 종료 후 대기열 소진 |
|---:|---:|---:|---:|---:|---:|---:|
| 1만 건 | 10,001건 | 0건 | 58.42 ms | 50.63건/초 | 170.52초 | 176.67초 |
| 2만 건 | 20,001건 | 0건 | 33.20 ms | 57.88건/초 | 292.09초 | 303.90초 |

1만 건은 성공 100건과 품절 9,901건, 2만 건은 성공 100건과 품절 19,901건으로 종료됐다. 두 실행 모두 예상 밖 응답, 거절, 실패, 재시도는 0건이었고 대기열이 완전히 비워졌다. 최종 상태도 대기 예약 100건과 판매 가능 단위 재고 0건으로 일치했다.

2,000 RPS로 5초간 1만 건을 보내는 탐색 실행에서는 최대 500 VU를 모두 사용해 2,853건만 전송하고 7,147건을 시작하지 못했다. 서버가 받은 2,853건은 모두 정상 접수됐지만 접수 p95가 1.42초까지 증가했다. 500 RPS 첫 실행도 VU 동적 할당 중 267건이 누락되어, 300 VU를 처음부터 할당한 뒤 1만 건 전체 전송에 성공했다. `dropped iteration`은 서버에서 유실된 요청이 아니라 k6가 시작하지 못한 요청이다.

실행 중 수동 관측값은 CPU 약 73%, 최저 가용 메모리 약 0.62 GiB였다. 절대적인 최대·최솟값을 연속 수집한 결과는 아니며, 같은 노트북에서 k6와 모든 인프라를 함께 실행한 로컬 측정값이다. 이 환경에서는 2만 건을 상한으로 두고 더 큰 규모는 별도 부하 발생 환경에서 측정한다.

원본 결과:

- 2,000 RPS 한계 탐색: [k6 접수 결과](results/order-queue-2000rps-5s-run1.json)
- 500 RPS VU 조정 전: [k6 접수 결과](results/order-queue-500rps-20s-run1.json)
- 1만 건: [k6 접수 결과](results/order-queue-500rps-20s-run2.json), [처리 및 정합성 집계](results/order-queue-500rps-20s-run2-analysis.json)
- 2만 건: [k6 접수 결과](results/order-queue-500rps-40s-run1.json), [처리 및 정합성 집계](results/order-queue-500rps-40s-run1-analysis.json)

## 결제 멱등성

초기 재고 1개로 예약을 만든 뒤 50 VU에서 같은 주문에 결제 요청 100건을 동시에 보냈다. API와 결제 경로를 워밍업한 뒤 측정했으며, teardown에서 계좌 잔액 감소가 결제 금액 1회분인지, 결제가 `COMPLETED`인지, 주문이 `PAID`인지 확인한다.

| 시나리오 | 완료 응답 | 409 | 예상 밖 응답 | 요청 p95 | 최종 검증 |
|---|---:|---:|---:|---:|---|
| 동일 멱등성 키 100건 | 100 | 0 | 0 | 1,229.96 ms | 출금·결제·주문 확정 각 1회 |
| 서로 다른 키/동일 주문 100건 | 1 | 99 | 0 | 275.46 ms | 출금·결제·주문 확정 각 1회 |

원본 k6 summary:

- [동일 키](results/payment-same-key-100-final.json)
- [서로 다른 키](results/payment-different-key-100-final.json)

### 테스트로 발견한 경쟁 조건

서로 다른 키로 같은 주문을 결제한 최초 실행에서는 1건 성공, 84건 409, 15건이 예상 밖의 400이었다. 늦게 도착한 요청이 이미 `CONFIRMED`된 예약을 보고 실패하면서, DB에 존재하는 기존 결제를 확인하지 못한 것이 원인이었다.

기존 결제를 외부 주문 API 호출 전과 결제 불가 판정 직전에 다시 확인하도록 검증 순서를 바꿨다. 수정 후 동일 조건에서 1건 성공과 99건 409로 일관되게 처리됐고 계좌 차감은 한 번만 반영됐다.

동일 키 폭주 시 p95 1.23초가 남아 있다. 정확성은 DB 유니크 제약과 계좌 작업 ID로 유지되지만 경쟁 요청이 처리 중 결제를 함께 재개하려는 비용이 있으므로, 후속 개선 후보는 DB 기반 실행 lease/CAS 또는 비동기 결제 접수와 상태 조회다.

## 탐색 과정에서 확인한 운영 요인

첫 200 RPS 탐색 실행에서는 Hibernate SQL과 bind 값을 동기식 TRACE로 기록해 요청 스레드가 로그 I/O에 묶였다. 당시 PostgreSQL에는 DB 락 대기가 없었지만 Hikari 대기가 누적됐고, 213건만 완료되며 예상 밖 응답률이 74.65%까지 올라갔다.

SQL/bind 로그를 WARN으로, 애플리케이션 로그를 INFO로 낮춘 뒤 같은 목표 RPS에서 예상 밖 응답 없이 테스트가 완료됐다. 이는 로컬 비교에서 확인한 결과이며 별도 프로파일러로 로그 비용을 분해한 것은 아니다.

## 재현

주문 비교는 order JAR을 먼저 만든 뒤 실행한다. 스크립트가 포트 8084의 Sunday order 프로세스인지 확인하고 각 회차마다 재기동한다.

```powershell
.\gradlew.bat :order-api:bootJar
.\load-test\run-order-benchmark.ps1 `
  -JavaHome 'C:\path\to\jdk-21' `
  -TargetRate 200 `
  -Duration '5s'
```

주문 대기열 테스트는 order JAR과 PostgreSQL, Redis가 준비된 상태에서 실행한다.

```powershell
.\load-test\run-order-queue-benchmark.ps1 `
  -JavaHome 'C:\path\to\jdk-21' `
  -TargetRate 500 `
  -Duration '20s' `
  -Stock 100 `
  -PreAllocatedVUs 300
```

결제 테스트는 account/order/payment API가 각각 8082/8084/8083에서 실행 중이어야 한다.

```powershell
docker run --rm -i `
  -e KEY_MODE=same -e REQUESTS=100 -e VUS=50 `
  -v "${PWD}\load-test:/scripts" `
  grafana/k6:2.2.0 run /scripts/k6/payment-idempotency.js

docker run --rm -i `
  -e KEY_MODE=different -e REQUESTS=100 -e VUS=50 `
  -v "${PWD}\load-test:/scripts" `
  grafana/k6:2.2.0 run /scripts/k6/payment-idempotency.js
```
