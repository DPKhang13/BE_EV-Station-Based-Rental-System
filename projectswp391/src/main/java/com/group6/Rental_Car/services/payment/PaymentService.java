package com.group6.Rental_Car.services.payment;

import com.group6.Rental_Car.dtos.payment.PaymentDto;
import com.group6.Rental_Car.dtos.payment.PaymentResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface PaymentService {


    PaymentResponse createPaymentUrl(PaymentDto paymentDto, UUID userId);


    PaymentResponse handleMoMoCallback(Map<String, String> momoParams);


    PaymentResponse refund(UUID orderId, BigDecimal amount);

    PaymentResponse processCashPayment(PaymentDto paymentDto, UUID userId);
    public void approveCashPaymentByOrder(UUID orderId);
    
    List<PaymentResponse> getPaymentsByOrderId(UUID orderId);
    
    /**
     * Lấy tổng số tiền đã hoàn của một đơn hàng
     * @param orderId ID của đơn hàng
     * @return Tổng số tiền đã hoàn (số dương)
     */
    BigDecimal getRefundedAmountByOrderId(UUID orderId);
    
    /**
     * Lấy lý do hoàn đơn của một đơn hàng
     * @param orderId ID của đơn hàng
     * @return Lý do hoàn đơn (có thể null nếu chưa có)
     */
    String getRefundReasonByOrderId(UUID orderId);
    
    /**
     * Tự động kiểm tra và chuyển order sang COMPLETED nếu:
     * - Xe đang ở trạng thái CHECKING (đã trả xe)
     * - Đã thanh toán hết (remainingAmount = 0)
     */
    void autoCompleteOrderIfReady(UUID orderId);
}
