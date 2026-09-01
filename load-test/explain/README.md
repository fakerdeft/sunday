# 실행 계획 기록

재고 선점 쿼리(`ProductStockRepository.claimWithSkipLocked`)의 부분 인덱스 적용 전후입니다.

## 재현

```sql
-- 상품 3개 × 100만 행. 상품 1은 앞쪽 90만 개가 판매된 진행 중인 핫딜.
INSERT INTO order_service.product_stock (product_id, status)
SELECT 1, CASE WHEN g <= 900000 THEN 'SOLD' ELSE 'AVAILABLE' END FROM generate_series(1,1000000) g;

-- 적용한 인덱스
CREATE INDEX idx_product_stock_claim
    ON order_service.product_stock(product_id, id)
    WHERE status = 'AVAILABLE';
```

`claim-before.txt` / `claim-after.txt` 가 `EXPLAIN (ANALYZE, BUFFERS)` 원문입니다.
