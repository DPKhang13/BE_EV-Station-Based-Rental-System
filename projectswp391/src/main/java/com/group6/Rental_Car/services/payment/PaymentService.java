package com.group6.Rental_Car.services.payment;

import com.group6.Rental_Car.dtos.payment.PaymentDto;
import com.group6.Rental_Car.dtos.payment.PaymentResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface PaymentService {


    PaymentResponse createPaymentUrl(PaymentDto paymentDto, UUID userId);


    PaymentResponse handleMoMoCallback(Map<String, String> momoParams);


    PaymentResponse refund(UUID orderId);

    PaymentResponse processCashPayment(PaymentDto paymentDto, UUID userId);
    public void approveCashPaymentByOrder(UUID orderId);
    
    List<PaymentResponse> getPaymentsByOrderId(UUID orderId);
    
    /**
     * Tự động kiểm tra và chuyển order sang COMPLETED nếu:
     * - Xe đang ở trạng thái CHECKING (đã trả xe)
     * - Đã thanh toán hết (remainingAmount = 0)
     */
    void autoCompleteOrderIfReady(UUID orderId);
}
