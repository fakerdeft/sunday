package com.sunday.order.domain

import com.sunday.common.exception.AlreadyExistsException
import com.sunday.common.exception.DuplicateRequestException
import com.sunday.common.exception.HotDealNotActiveException
import com.sunday.common.exception.InvalidOrderStatusException
import com.sunday.common.exception.NotFoundException
import com.sunday.common.exception.OutOfStockException
import java.math.BigDecimal

sealed class OrderException(message: String) : RuntimeException(message)

class ProductNotFoundException(productId: Long) :
    OrderException("상품을 찾을 수 없습니다: $productId"), NotFoundException

class ReservationNotFoundException(reservationId: Long) :
    OrderException("선점을 찾을 수 없습니다: $reservationId"), NotFoundException

class OrderNotFoundException(reservationId: Long) :
    OrderException("주문을 찾을 수 없습니다: $reservationId"), NotFoundException

class OutOfStockException(productId: Long, requested: Int, available: Int) :
    OrderException("상품 $productId 의 재고가 부족합니다. 요청: $requested, 재고: $available"), OutOfStockException

class HotDealNotActiveException(productId: Long) :
    OrderException("상품 $productId 에 대한 핫딜이 진행 중이 아닙니다."), HotDealNotActiveException

class ReservationExpiredException(reservationId: Long) :
    OrderException("만료된 선점입니다: $reservationId")

class AlreadyPurchasedException(memberId: Long, productId: Long) :
    OrderException("회원 $memberId 님은 상품 $productId 를 이미 구매했습니다."), AlreadyExistsException

class InvalidOrderStatusException(id: Long, currentStatus: String, expectedStatus: String) :
    OrderException("주문/선점 $id 의 상태가 유효하지 않습니다. 현재: $currentStatus, 기대: $expectedStatus"),
    InvalidOrderStatusException

class DuplicatePendingOrderException(memberId: Long, productId: Long) :
    OrderException("회원 $memberId 님은 상품 $productId 에 대해 이미 대기 중인 선점이 있습니다."),
    DuplicateRequestException

class StockReservationMismatchException(reservationId: Long, expected: Int, actual: Int) :
    OrderException("예약 $reservationId 의 재고 귀속 수량이 일치하지 않습니다. 기대: $expected, 실제: $actual")

class InvalidOrderQuantityException(quantity: Int) :
    OrderException("주문 수량은 양수여야 합니다. 입력값: $quantity")

class InvalidProductPriceException(price: BigDecimal) :
    OrderException("상품 가격은 양수여야 합니다. 입력값: $price")

class InvalidProductNameException :
    OrderException("상품 이름은 비어있을 수 없습니다.")

class InvalidProductStockException(stock: Int) :
    OrderException("재고는 음수가 될 수 없습니다. 입력값: $stock")
