# 대규모 트래픽을 고려한 선착순 핫딜 & 결제 시스템

## 🚀 Objective

1. **대용량 트래픽 대응**: 선착순 핫딜 상황에서의 순간적인 Spike Traffic을 견디는 아키텍처 설계.
2. **데이터 정합성 보장**: 재고 초과 판매 및 중복 결제 방지.
3. **독립 실행 구조**: 비즈니스 도메인별 API를 독립 실행 가능한 멀티 모듈로 구성.

## 🗽 Architecture

### 도메인별 독립 실행 서버 구조 (레이어드 아키텍처)

도메인별 API를 별도의 Spring Boot 애플리케이션으로 실행하며, 각 서버는 API → Application → Domain → Repository 레이어드 아키텍처로 구성했습니다. 하나의 PostgreSQL 데이터베이스를 사용하되 서비스별 스키마와 계정을 분리해 각 서버가 자신이 소유한 테이블에만 접근하도록 제한했습니다.

### 데이터베이스 경계

모든 API는 하나의 PostgreSQL 데이터베이스(로컬 기준 `sunday_local`)를 공유하지만, 애플리케이션 계정에는 자신의 스키마에 대한 권한만 부여했습니다. 다른 도메인의 데이터가 필요한 경우 해당 API를 호출하며 다른 서비스의 테이블을 직접 조회하지 않습니다.

| API | DB 계정 | 스키마 | 소유 테이블 |
|---|---|---|---|
| Member | `member_app` | `member_service` | `member` |
| Account | `account_app` | `account_service` | `account`, `account_transaction`, `transfer` |
| Order | `order_app` | `order_service` | `product`, `product_stock`, `order_reservations`, `orders` |
| Payment | `payment_app` | `payment_service` | `payment` |

```
Sunday-Server
├── common              # 공통 인증 어노테이션, 예외 인터페이스, 에러 응답 DTO
├── member-api          # 회원 서비스 (port: 8081)
│   ├── api                 # REST 컨트롤러, 요청/응답 DTO
│   ├── application         # 비즈니스 로직
│   ├── domain              # 도메인 모델, 예외
│   └── repository          # JPA Entity, Repository
├── account-api         # 계좌/송금 서비스 (port: 8082)
│   ├── api
│   ├── application
│   ├── domain
│   └── repository
├── order-api           # 주문/재고 서비스 (port: 8083)
│   ├── api
│   ├── application
│   ├── domain
│   ├── repository          # JPA + PostgreSQL SKIP LOCKED
│   └── config/scheduler    # 만료 예약 처리
├── payment-api         # 결제 서비스 (port: 8084)
│   ├── api
│   ├── application         # 단계별 결제 처리 및 보상
│   ├── domain
│   ├── repository          # JPA 기반 결제 상태 관리
│   └── client              # Account/Order API 호출
└── sunday-config       # 실행 설정, DB 스키마, Docker Compose, 부하 테스트
```

## 🛠 Tech Stack

- **Language**: Kotlin 2.3, JDK 21
- **Framework**: Spring Boot 4.0.1
- **Database**: PostgreSQL 17 (서비스별 스키마/계정 분리)
- **Persistence & Locking**: Spring Data JPA, QueryDSL, `FOR UPDATE SKIP LOCKED`
- **Architecture**: Multi-module, Layered Architecture (도메인별 독립 실행 API)
- **Testing**: JUnit 5, Kotest, Testcontainers, k6
- **Infra**: AWS EC2, RDS, Docker Compose, Prometheus, Grafana, Loki

## ⭐ Features

### Member (회원)

- 회원 등록 및 조회

### Account (계좌)

- 예치금 계좌 생성
- 잔액 충전 / 차감
- 거래 내역 조회
- 회원 간 송금
- 송금 취소
- 작업 식별자를 이용한 중복 입출금 방지

### Order (주문/재고)

- 상품 목록 및 핫딜 상품 조회
- `product_stock` 단위 재고 조회 및 선점
- `FOR UPDATE SKIP LOCKED`를 이용한 동시 주문 처리
- 주문 취소 및 만료 시 예약이 소유한 재고 복구
- 비관적 락 방식과의 부하 테스트 비교

### Payment (결제)

- 멱등성 키와 주문별 유니크 제약을 이용한 중복 결제 방지
- 결제 단계별 상태 저장 및 실패 지점부터 재시도
- 계좌 차감과 주문 확정 실패 시 보상 처리
- 결제 환불 및 결제 내역 조회

## 📊 Test

- Testcontainers PostgreSQL 기반 주문 동시성 및 계좌 입출금 멱등성 통합 테스트
- MockK 기반 결제 상태 전이, 재시도 및 보상 단위 테스트
- k6 기반 주문 Spike Traffic 및 결제 중복 요청 테스트
- 측정 환경과 원자료는 [sunday-config 부하 테스트 문서](https://github.com/fakerdeft/sunday-config/blob/main/load-test/README.md)에 정리했습니다.
