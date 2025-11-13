package com.group6.Rental_Car.services.payment;

import com.group6.Rental_Car.dtos.payment.PaymentDto;
import com.group6.Rental_Car.dtos.payment.PaymentResponse;

import java.util.Map;
import java.util.UUID;

/**
 * Payment Service Interface
 * Xử lý thanh toán qua MoMo Gateway
 */
public interface PaymentService {

    /**
     * Tạo URL thanh toán MoMo
     * @param paymentDto Thông tin thanh toán (orderId, paymentType, method)
     * @param userId ID của user thực hiện thanh toán
     * @return PaymentResponse chứa paymentUrl, qrCode, deeplink
     */
    PaymentResponse createPaymentUrl(PaymentDto paymentDto, UUID userId);

    /**
     * Xử lý MoMo IPN callback
     * @param momoParams Parameters từ MoMo callback
     * @return PaymentResponse với trạng thái thanh toán
     */
    PaymentResponse handleMoMoCallback(Map<String, String> momoParams);

    /**
     * Hoàn tiền cho đơn hàng
     * @param orderId ID của đơn hàng cần hoàn tiền
     * @return PaymentResponse với thông tin hoàn tiền
     */
    PaymentResponse refund(UUID orderId);
}
