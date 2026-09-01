CREATE UNIQUE INDEX IF NOT EXISTS uq_reservations_pending_member_product
    ON order_service.order_reservations(member_id, product_id)
    WHERE status = 'PENDING';

CREATE UNIQUE INDEX IF NOT EXISTS uq_reservations_reservation_key
    ON order_service.order_reservations(reservation_key);

CREATE INDEX IF NOT EXISTS idx_product_stock_claim
    ON order_service.product_stock(product_id, id)
    WHERE status = 'AVAILABLE';
