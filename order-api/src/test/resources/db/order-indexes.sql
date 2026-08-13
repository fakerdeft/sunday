CREATE UNIQUE INDEX IF NOT EXISTS uq_reservations_pending_member_product
    ON order_service.order_reservations(member_id, product_id)
    WHERE status = 'PENDING';
