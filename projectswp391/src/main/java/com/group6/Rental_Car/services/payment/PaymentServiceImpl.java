package com.group6.Rental_Car.services.payment;

import com.group6.Rental_Car.config.VNpayConfig;
import com.group6.Rental_Car.dtos.payment.PaymentDto;
import com.group6.Rental_Car.dtos.payment.PaymentResponse;
import com.group6.Rental_Car.dtos.payment.VNPayDto;
import com.group6.Rental_Car.entities.Payment;
import com.group6.Rental_Car.entities.RentalOrder;
import com.group6.Rental_Car.enums.PaymentStatus;
import com.group6.Rental_Car.exceptions.ResourceNotFoundException;
import com.group6.Rental_Car.repositories.PaymentRepository;
import com.group6.Rental_Car.repositories.RentalOrderRepository;
import com.group6.Rental_Car.utils.Utils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final VNpayConfig vnpayConfig;
    private final RentalOrderRepository rentalOrderRepository;

    @Value("${VNP_HASHSECRET}")
    private String VNP_SECRET;

    @Value("${VNP_URL}")
    private String VNP_URL;

    @Value("${VNP_RETURNURL:http://localhost:8080/api/payment/vnpay-callback}")
    private String RETURN_URL;

    @Override
    public PaymentResponse createPayment(PaymentDto paymentDto, UUID orderId) {

        RentalOrder rentalOrder = rentalOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng ID: " + orderId));

        BigDecimal total = rentalOrder.getTotalPrice();
        BigDecimal payAmount = paymentDto.isDeposit()
                ? total.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP)
                : total;

        Payment payment = Payment.builder()
                .rentalOrder(rentalOrder)
                .amount(payAmount)
                .method(paymentDto.getMethod())
                .status(PaymentStatus.PENDING)
                .build();
        paymentRepository.save(payment);

        if (!"VNPay".equalsIgnoreCase(paymentDto.getMethod())) {
            payment.setStatus(paymentDto.isDeposit() ? PaymentStatus.DEPOSIT : PaymentStatus.FULL);
            paymentRepository.save(payment);
            return PaymentResponse.builder()
                    .paymentId(payment.getPaymentId())
                    .orderId(orderId)
                    .amount(payAmount)
                    .method(payment.getMethod())
                    .status(payment.getStatus())
                    .message("Thanh toán nội bộ thành công")
                    .build();
        }

        long amountForVNPay = payAmount.multiply(BigDecimal.valueOf(100)).longValue();
        Map<String, String> vnpParamsMap = vnpayConfig.getVNPayConfig();

        String txnRef = payment.getPaymentId().toString();
        vnpParamsMap.put("vnp_TxnRef", txnRef);
        vnpParamsMap.put("vnp_Amount", String.valueOf(amountForVNPay));
        vnpParamsMap.put("vnp_IpAddr", paymentDto.getClientIp());
        vnpParamsMap.put("vnp_OrderInfo", paymentDto.getOrderInfo());
        vnpParamsMap.put("vnp_ReturnUrl", paymentDto.getReturnUrl() != null ? paymentDto.getReturnUrl() : RETURN_URL);

        String queryUrl = Utils.getPaymentURL(vnpParamsMap, true);
        String hashData = Utils.getPaymentURL(vnpParamsMap, false);
        String vnpSecureHash = Utils.hmacSHA512(VNP_SECRET, hashData);
        queryUrl += "&vnp_SecureHash=" + vnpSecureHash;
        String paymentUrl = VNP_URL + "?" + queryUrl;

        payment.setTxnRef(txnRef);
        paymentRepository.save(payment);

        return PaymentResponse.builder()
                .paymentId(payment.getPaymentId())
                .orderId(orderId)
                .amount(payment.getAmount())
                .method(payment.getMethod())
                .status(payment.getStatus())
                .paymentUrl(paymentUrl)
                .message("Tạo link thanh toán VNPay thành công")
                .build();
    }

    @Override
    public PaymentResponse handleVNPayCallback(VNPayDto vnPayDto) {
        Optional<Payment> paymentOpt = paymentRepository.findByTxnRef(vnPayDto.getVnp_TxnRef());
        if (paymentOpt.isEmpty()) {
            return PaymentResponse.builder()
                    .message("Không tìm thấy giao dịch tương ứng")
                    .status(PaymentStatus.FAILED)
                    .build();
        }

        Payment payment = paymentOpt.get();
        String statusCode = vnPayDto.getVnp_TransactionStatus();

        if ("00".equals(statusCode)) {
            payment.setStatus(PaymentStatus.FULL);
            payment.setAmount(BigDecimal.valueOf((double) vnPayDto.getVnp_Amount() / 100.0));
        } else {
            payment.setStatus(PaymentStatus.FAILED);
        }

        paymentRepository.save(payment);

        return PaymentResponse.builder()
                .paymentId(payment.getPaymentId())
                .orderId(payment.getRentalOrder().getOrderId())
                .amount(payment.getAmount())
                .method(payment.getMethod())
                .status(payment.getStatus())
                .message(payment.getStatus() == PaymentStatus.FULL
                        ? "Thanh toán VNPay thành công"
                        : "Thanh toán VNPay thất bại")
                .build();
    }
}
