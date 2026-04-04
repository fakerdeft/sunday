rootProject.name = "Sunday-Server"

// 실행 모듈 (레거시 - 마이그레이션 완료 후 제거 예정)
include("app")

// 공통 모듈
include("common")
include("support-infra")
// outbox 모듈은 payment-api repository/outbox 패키지로 흡수됨
// include("outbox")

// ================================
// 분산 서버 모듈 (레이어드 아키텍처)
// ================================
include("member-api")
include("account-api")
include("order-api")
include("payment-api")

// ================================
// 레거시 도메인 모듈 (헥사고날 아키텍처 - 마이그레이션 진행 중)
// ================================

// Member domain (마이그레이션 완료 → member-api로 대체됨)
// include("member:member-core")
// include("member:member-api")
// include("member:member-infra")

// Account domain (마이그레이션 완료 → account-api 독립 서버로 분리됨)
// include("account:account-core")
// include("account:account-api")
// include("account:account-infra")

// Payment domain (마이그레이션 완료 → payment-api 독립 서버로 분리됨)
// include("payment:payment-core")
// include("payment:payment-api")
// include("payment:payment-infra")

// Order domain (마이그레이션 완료 → order-api 독립 서버로 분리됨)
// include("order:order-core")
// include("order:order-api")
// include("order:order-infra")
// include("order:order-batch")
