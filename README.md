# 대규모 트래픽을 고려한 선착순 핫딜 & 결제 시스템

## 🚀 Objective

1. **대용량 트래픽 대응**: 선착순 핫딜 상황에서의 순간적인 Spike Traffic을 견디는 아키텍처 설계.
2. **데이터 정합성 보장**: 재고 초과 판매 및 중복 결제 방지.
3. **유연한 확장성**: 비즈니스 도메인별 독립적인 성장을 위한 멀티 모듈 & 헥사고날 아키텍처 도입.

## 🗽 Architecture

### 멀티 모듈 & 헥사고날 아키텍처

도메인 간의 결합도를 낮추고, 추후 MSA 전환을 용이하게 하기 위해 모듈과 계층 분리를 적용했습니다.

```
Sunday-Server
├── app                 # 실행 가능한 Spring Boot 애플리케이션
├── common              # 공통 유틸리티, 예외 처리
├── member              # [Domain] 회원 관리
│   ├── member-core         # 순수 비즈니스 로직 (도메인, 포트)
│   ├── member-api          # REST 컨트롤러, DTO (Inbound Adapter)
│   └── member-infra        # JPA, Redis 어댑터 (Outbound Adapter)
├── account             # [Domain] 계좌, 송금, 정산
│   ├── account-core
│   ├── account-api
│   └── account-infra
├── payment             # [Domain] 결제
│   ├── payment-core
│   ├── payment-api
│   └── payment-infra
└── order               # [Domain] 상품, 주문, 재고
    ├── order-core
    ├── order-api
    ├── order-infra
    └── order-batch     # 스케줄러
```

* **Core 모듈**: 순수 비즈니스 로직 (도메인 모델, 유스케이스, 포트 인터페이스)
* **API 모듈**: REST 컨트롤러, 요청/응답 DTO (Inbound Adapter)
* **Infra 모듈**: DB, Redis 등 기술적 구현체 (Outbound Adapter)

## 🛠 Tech Stack

- **Language**: Kotlin 2.3, JDK 21
- **Framework**: Spring Boot 4.0.1
- **Database**: PostgreSQL 17
- **Cache & Lock**: Redis 7
- **Architecture**: Multi-module Monolith, Hexagonal Architecture
- **Testing**: JMeter, JUnit5, Kotest
- **Infra**: AWS EC2, RDS, Docker Compose

## ⭐ Features

### Member (회원)

- 회원 등록 및 조회

### Account (계좌)

- 예치금 계좌 생성
- 잔액 충전 / 차감
- 거래 내역 조회
- 회원 간 송금
- 송금 취소

### Order (주문/재고)

- 상품 목록 조회
- 핫딜 상품 조회
- 실시간 재고 조회
- 주문 생성 시 재고 선점
- 주문 취소 시 재고 복구
- 만료 주문 자동 처리
- Redis ↔ DB 재고 동기화

### Payment (결제)

- 주문 결제
- 결제 환불
- 결제 내역 조회
