# 대규모 트래픽을 고려한 선착순 핫딜 & 결제 시스템

## 🚀 Objective

1. **대용량 트래픽 대응**: 선착순 핫딜 상황에서의 순간적인 Spike Traffic을 견디는 아키텍처 설계.
2. **데이터 정합성 보장**: 재고 초과 판매 및 중복 결제 방지.
3. **유연한 확장성**: 비즈니스 도메인별 독립적인 성장을 위한 멀티 모듈 & 헥사고날 아키텍처 도입.

## 🗽 Architecture

### 독립 분산 서버 구조 (레이어드 아키텍처)

도메인별 독립 서버로 분리하여 추후 MSA 전환을 용이하게 하고, 각 서버는 Presentation → Application → Domain → Repository 레이어드 아키텍처로 구성했습니다.

```
Sunday-Server
├── common              # 공통 예외 인터페이스, 에러 응답 DTO
├── support-infra       # 분산 락 AOP (Redisson 기반)
├── member-api          # 회원 서비스 (port: 8081)
│   ├── presentation        # REST 컨트롤러, DTO
│   ├── application         # 비즈니스 로직
│   ├── domain              # 도메인 모델, 예외
│   └── repository          # JPA Entity, Repository
├── account-api         # 계좌/송금 서비스 (port: 8082)
│   ├── presentation
│   ├── application
│   ├── domain
│   └── repository
├── order-api           # 주문/재고 서비스 (port: 8083)
│   ├── presentation
│   ├── application
│   ├── domain
│   ├── repository          # JPA + Redis 재고 저장소
│   └── config/scheduler    # 만료 주문 처리, 재고 동기화
└── payment-api         # 결제 서비스 (port: 8084)
    ├── presentation
    ├── application
    ├── domain
    ├── repository          # JPA + Outbox + Redis 멱등성
    └── client              # Account/Order API 호출
```

## 🛠 Tech Stack

- **Language**: Kotlin 2.3, JDK 21
- **Framework**: Spring Boot 4.0.1
- **Database**: PostgreSQL 17
- **Cache & Lock**: Redis 7
- **Architecture**: Multi-module, Layered Architecture (독립 분산 서버)
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
