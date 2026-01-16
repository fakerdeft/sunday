-- PostgreSQL 초기화 스크립트

-- Replication user 생성 (Master에서 Slave로의 복제를 위해)
CREATE USER replicator WITH REPLICATION ENCRYPTED PASSWORD 'replicator123';

-- Replication slot 생성
SELECT pg_create_physical_replication_slot('replication_slot');

-- 애플리케이션 스키마 생성
CREATE SCHEMA IF NOT EXISTS sunday;

-- 회원 테이블 (Member Domain)
CREATE TABLE IF NOT EXISTS sunday.member (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_member_name ON sunday.member(name);

-- 계정 테이블 (예치금 관리)
CREATE TABLE IF NOT EXISTS sunday.account (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL REFERENCES sunday.member(id),
    user_id VARCHAR(100) NOT NULL UNIQUE,
    balance DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT positive_balance CHECK (balance >= 0)
);

CREATE INDEX idx_account_member_id ON sunday.account(member_id);
CREATE INDEX idx_account_user_id ON sunday.account(user_id);

-- 계정 거래 이력 테이블
CREATE TABLE IF NOT EXISTS sunday.account_transaction (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES sunday.account(id),
    transaction_type VARCHAR(20) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    balance_after DECIMAL(19, 2) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_account_transaction_account_id ON sunday.account_transaction(account_id);
CREATE INDEX idx_account_transaction_created_at ON sunday.account_transaction(created_at DESC);

-- 결제 테이블 (멱등성 보장)
CREATE TABLE IF NOT EXISTS sunday.payment (
    id BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    member_id BIGINT NOT NULL REFERENCES sunday.member(id),
    user_id VARCHAR(100) NOT NULL,
    order_id VARCHAR(100) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    payment_method VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_payment_member_id ON sunday.payment(member_id);
CREATE INDEX idx_payment_user_id ON sunday.payment(user_id);
CREATE INDEX idx_payment_order_id ON sunday.payment(order_id);
CREATE INDEX idx_payment_idempotency_key ON sunday.payment(idempotency_key);

-- 주문 테이블
CREATE TABLE IF NOT EXISTS sunday.orders (
    id BIGSERIAL PRIMARY KEY,
    order_id VARCHAR(100) NOT NULL UNIQUE,
    member_id BIGINT NOT NULL REFERENCES sunday.member(id),
    user_id VARCHAR(100) NOT NULL,
    product_id VARCHAR(100) NOT NULL,
    quantity INT NOT NULL,
    total_amount DECIMAL(19, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- 1인당 1개 구매 제한 (선착순 핫딜 어뷰징 방지)
    CONSTRAINT unique_member_product UNIQUE (member_id, product_id)
);

CREATE INDEX idx_orders_member_id ON sunday.orders(member_id);
CREATE INDEX idx_orders_user_id ON sunday.orders(user_id);
CREATE INDEX idx_orders_product_id ON sunday.orders(product_id);
CREATE INDEX idx_orders_status ON sunday.orders(status);
CREATE INDEX idx_orders_created_at ON sunday.orders(created_at DESC);

-- 재고 테이블 (동시성 제어)
CREATE TABLE IF NOT EXISTS sunday.inventory (
    id BIGSERIAL PRIMARY KEY,
    product_id VARCHAR(100) NOT NULL UNIQUE,
    stock_quantity INT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT positive_stock CHECK (stock_quantity >= 0)
);

CREATE INDEX idx_inventory_product_id ON sunday.inventory(product_id);

-- 테스트 데이터 삽입

-- 1. Member 더미 데이터 생성 (10,000명)
INSERT INTO sunday.member (name)
SELECT 'User ' || generate_series(1, 10000);

-- 2. 각 Member에게 계좌 생성 (초기 잔액 100만원)
INSERT INTO sunday.account (member_id, user_id, balance, version)
SELECT
    id,
    'user' || LPAD(id::TEXT, 6, '0'),  -- user000001, user000002, ...
    1000000.00,
    0
FROM sunday.member;

-- 3. 재고 테이블에 핫딜 상품 등록
INSERT INTO sunday.inventory (product_id, stock_quantity, version) VALUES
    ('HOTDEAL-001', 100, 0),   -- 재고 100개
    ('HOTDEAL-002', 500, 0),   -- 재고 500개
    ('HOTDEAL-003', 1000, 0)   -- 재고 1000개
ON CONFLICT (product_id) DO NOTHING;

-- 권한 부여
GRANT ALL PRIVILEGES ON SCHEMA sunday TO sunday;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA sunday TO sunday;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA sunday TO sunday;

-- 함수: updated_at 자동 갱신 트리거
CREATE OR REPLACE FUNCTION sunday.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- updated_at 트리거 적용
CREATE TRIGGER update_account_updated_at BEFORE UPDATE ON sunday.account
    FOR EACH ROW EXECUTE FUNCTION sunday.update_updated_at_column();

CREATE TRIGGER update_payment_updated_at BEFORE UPDATE ON sunday.payment
    FOR EACH ROW EXECUTE FUNCTION sunday.update_updated_at_column();

CREATE TRIGGER update_orders_updated_at BEFORE UPDATE ON sunday.orders
    FOR EACH ROW EXECUTE FUNCTION sunday.update_updated_at_column();

CREATE TRIGGER update_inventory_updated_at BEFORE UPDATE ON sunday.inventory
    FOR EACH ROW EXECUTE FUNCTION sunday.update_updated_at_column();
