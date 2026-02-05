-- PostgreSQL 초기화 스크립트 (Sunday Server)

-- Replication user 생성 (Master에서 Slave로의 복제를 위해)
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'replicator') THEN
        CREATE USER replicator WITH REPLICATION ENCRYPTED PASSWORD 'replicator123';
    END IF;
END
$$;

-- Replication slot 생성 (이미 존재하면 무시)
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_replication_slots WHERE slot_name = 'replication_slot') THEN
        PERFORM pg_create_physical_replication_slot('replication_slot');
    END IF;
EXCEPTION WHEN OTHERS THEN
    NULL;
END
$$;

-- 애플리케이션 스키마 생성
CREATE SCHEMA IF NOT EXISTS sunday;

-- =====================================================
-- 1. Member 테이블
-- =====================================================
CREATE TABLE IF NOT EXISTS sunday.member (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================
-- 2. Account 테이블 (예치금 계좌)
-- =====================================================
CREATE TABLE IF NOT EXISTS sunday.account (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL,
    user_id VARCHAR(100) NOT NULL UNIQUE,
    balance DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT positive_balance CHECK (balance >= 0)
);

CREATE INDEX IF NOT EXISTS idx_account_member_id ON sunday.account(member_id);
CREATE INDEX IF NOT EXISTS idx_account_user_id ON sunday.account(user_id);

-- =====================================================
-- 3. Account Transaction 테이블 (거래 이력)
-- =====================================================
CREATE TABLE IF NOT EXISTS sunday.account_transaction (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    balance_after DECIMAL(19, 2) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_account_tx_account_id ON sunday.account_transaction(account_id);

-- =====================================================
-- 4. Transfer 테이블 (송금)
-- =====================================================
CREATE TABLE IF NOT EXISTS sunday.transfer (
    id BIGSERIAL PRIMARY KEY,
    sender_account_id BIGINT NOT NULL,
    sender_member_id BIGINT NOT NULL,
    receiver_account_id BIGINT NOT NULL,
    receiver_member_id BIGINT NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500),
    failure_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_transfer_sender ON sunday.transfer(sender_member_id);
CREATE INDEX IF NOT EXISTS idx_transfer_receiver ON sunday.transfer(receiver_member_id);
CREATE INDEX IF NOT EXISTS idx_transfer_idempotency ON sunday.transfer(idempotency_key);

-- =====================================================
-- 5. Product 테이블 (상품)
-- =====================================================
CREATE TABLE IF NOT EXISTS sunday.product (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    price DECIMAL(19, 2) NOT NULL,
    stock INT NOT NULL DEFAULT 0,
    total_quantity INT NOT NULL DEFAULT 0,
    is_hot_deal BOOLEAN NOT NULL DEFAULT FALSE,
    hot_deal_start_time TIMESTAMP,
    hot_deal_end_time TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT positive_stock CHECK (stock >= 0)
);

-- =====================================================
-- 6. Orders 테이블 (주문)
-- =====================================================
CREATE TABLE IF NOT EXISTS sunday.orders (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(19, 2) NOT NULL,
    total_amount DECIMAL(19, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    reservation_key VARCHAR(100) NOT NULL,
    expire_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_orders_member_id ON sunday.orders(member_id);
CREATE INDEX IF NOT EXISTS idx_orders_product_id ON sunday.orders(product_id);
CREATE INDEX IF NOT EXISTS idx_orders_status ON sunday.orders(status);

-- =====================================================
-- 7. Payment 테이블 (결제)
-- =====================================================
CREATE TABLE IF NOT EXISTS sunday.payment (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    failure_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_payment_order_id ON sunday.payment(order_id);
CREATE INDEX IF NOT EXISTS idx_payment_member_id ON sunday.payment(member_id);
CREATE INDEX IF NOT EXISTS idx_payment_idempotency ON sunday.payment(idempotency_key);

-- =====================================================
-- 테스트 데이터
-- =====================================================

-- 회원 3명 생성
INSERT INTO sunday.member (id, name) VALUES
    (1, '김철수'),
    (2, '이영희'),
    (3, '박민수')
ON CONFLICT (id) DO NOTHING;

-- 시퀀스 조정
SELECT setval('sunday.member_id_seq', 3, true);

-- 각 회원에게 계좌 생성 (초기 잔액 100만원)
INSERT INTO sunday.account (id, member_id, user_id, balance, version) VALUES
    (1, 1, '1', 1000000.00, 0),
    (2, 2, '2', 1000000.00, 0),
    (3, 3, '3', 1000000.00, 0)
ON CONFLICT (user_id) DO NOTHING;

SELECT setval('sunday.account_id_seq', 3, true);

-- 핫딜 상품 등록
INSERT INTO sunday.product (id, name, price, stock, total_quantity, is_hot_deal, hot_deal_start_time, hot_deal_end_time) VALUES
    (1, '🔥 [한정판] 에어팟 프로 2', 199000.00, 100, 100, TRUE, NOW(), NOW() + INTERVAL '7 days'),
    (2, '🔥 [특가] 맥북 에어 M3', 1290000.00, 50, 50, TRUE, NOW(), NOW() + INTERVAL '7 days'),
    (3, '🔥 [초특가] 아이패드 미니', 590000.00, 30, 30, TRUE, NOW(), NOW() + INTERVAL '7 days'),
    (4, '일반 상품 - 무선 마우스', 29000.00, 500, 500, FALSE, NULL, NULL),
    (5, '일반 상품 - USB 허브', 19000.00, 300, 300, FALSE, NULL, NULL)
ON CONFLICT (id) DO NOTHING;

SELECT setval('sunday.product_id_seq', 5, true);

-- =====================================================
-- 권한 부여
-- =====================================================
ALTER USER sunday WITH REPLICATION;
GRANT ALL PRIVILEGES ON SCHEMA sunday TO sunday;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA sunday TO sunday;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA sunday TO sunday;

-- =====================================================
-- updated_at 자동 갱신 트리거
-- =====================================================
CREATE OR REPLACE FUNCTION sunday.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

DROP TRIGGER IF EXISTS update_account_updated_at ON sunday.account;
CREATE TRIGGER update_account_updated_at BEFORE UPDATE ON sunday.account
    FOR EACH ROW EXECUTE FUNCTION sunday.update_updated_at_column();

DROP TRIGGER IF EXISTS update_transfer_updated_at ON sunday.transfer;
CREATE TRIGGER update_transfer_updated_at BEFORE UPDATE ON sunday.transfer
    FOR EACH ROW EXECUTE FUNCTION sunday.update_updated_at_column();

DROP TRIGGER IF EXISTS update_product_updated_at ON sunday.product;
CREATE TRIGGER update_product_updated_at BEFORE UPDATE ON sunday.product
    FOR EACH ROW EXECUTE FUNCTION sunday.update_updated_at_column();

DROP TRIGGER IF EXISTS update_orders_updated_at ON sunday.orders;
CREATE TRIGGER update_orders_updated_at BEFORE UPDATE ON sunday.orders
    FOR EACH ROW EXECUTE FUNCTION sunday.update_updated_at_column();

DROP TRIGGER IF EXISTS update_payment_updated_at ON sunday.payment;
CREATE TRIGGER update_payment_updated_at BEFORE UPDATE ON sunday.payment
    FOR EACH ROW EXECUTE FUNCTION sunday.update_updated_at_column();

-- =====================================================
-- 8. Outbox 테이블 (이벤트 발행 보장)
-- =====================================================
CREATE TABLE IF NOT EXISTS sunday.outbox (
    id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    max_retries INT NOT NULL DEFAULT 3,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP,
    next_retry_at TIMESTAMP,
    error_message TEXT
);

CREATE INDEX IF NOT EXISTS idx_outbox_pending
    ON sunday.outbox(status, next_retry_at) WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_outbox_aggregate
    ON sunday.outbox(aggregate_type, aggregate_id);

DROP TRIGGER IF EXISTS update_outbox_updated_at ON sunday.outbox;
