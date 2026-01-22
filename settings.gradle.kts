rootProject.name = "Sunday-Server"

// 실행 모듈
include("app")

// 공통 모듈
include("common")
include("support-infra")

// Member domain
include("member:member-core")
include("member:member-api")
include("member:member-infra")

// Account domain
include("account:account-core")
include("account:account-api")
include("account:account-infra")

// Payment domain
include("payment:payment-core")
include("payment:payment-api")
include("payment:payment-infra")

// Order domain
include("order:order-core")
include("order:order-api")
include("order:order-infra")
include("order:order-batch")
