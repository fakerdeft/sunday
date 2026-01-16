rootProject.name = "Sunday-Server"

// 실행 모듈
include("app")

// 공통 모듈
include("common")

// Member domain
include("member:member-core")
include("member:member-infra")

// Account domain
include("account:account-core")
include("account:account-infra")

// Payment domain
include("payment:payment-core")
include("payment:payment-infra")

// Order domain
include("order:order-core")
include("order:order-infra")
