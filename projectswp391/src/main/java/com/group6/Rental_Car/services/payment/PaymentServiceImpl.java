package com.group6.Rental_Car.services.payment;

import com.group6.Rental_Car.config.VNpayConfig;
import com.group6.Rental_Car.dtos.payment.PaymentDto;
import com.group6.Rental_Car.dtos.payment.PaymentResponse;
import com.group6.Rental_Car.entities.*;
import com.group6.Rental_Car.enums.PaymentStatus;
import com.group6.Rental_Car.exceptions.ResourceNotFoundException;
import com.group6.Rental_Car.repositories.*;
import com.group6.Rental_Car.utils.Utils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    @Value("${VNP_HASHSECRET}")
    private String VNP_SECRET;
    @Value("${VNP_URL}")
    private String VNP_URL;

    private final UserRepository userRepository;
    private final VNpayConfig vnpayConfig;
    private final RentalOrderRepository rentalOrderRepository;
    private final PaymentRepository paymentRepository;
    private final TransactionHistoryRepository transactionHistoryRepository;
    private final VehicleRepository vehicleRepository;

    @Override
    public PaymentResponse createPaymentUrl(PaymentDto paymentDto, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        RentalOrder order = rentalOrderRepository.findById(paymentDto.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        Short type = paymentDto.getPaymentType();
        if (type == null) {
            throw new IllegalArgumentException("Thiếu paymentType (1=cọc, 2=còn lại)");
        }


        String clientIp = "127.0.0.1";
        String method = (paymentDto.getMethod() != null)
                ? paymentDto.getMethod()
                : "VNPAY";

        BigDecimal totalAmount = BigDecimal.ZERO;


        if (type == 1) { // đặt cọc 50%
            totalAmount = order.getTotalPrice().multiply(BigDecimal.valueOf(0.5));

            if (order.getDepositAmount() == null || order.getDepositAmount().compareTo(BigDecimal.ZERO) == 0) {
                order.setDepositAmount(totalAmount);
                order.setRemainingAmount(order.getTotalPrice().subtract(totalAmount));
            }

            order.setStatus("PENDING_DEPOSIT");
        } else if (type == 2) {
            totalAmount = order.getRemainingAmount();


            if (totalAmount == null) {
                totalAmount = order.getTotalPrice().subtract(
                        order.getDepositAmount() != null ? order.getDepositAmount() : BigDecimal.ZERO
                );
                order.setRemainingAmount(totalAmount);
            }

            if (totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException("Không còn số tiền cần thanh toán cho đơn này");
            }


            order.setStatus("PENDING_FINAL");
        }


        Payment payment = Payment.builder()
                .rentalOrder(order)
                .amount(totalAmount)
                .method(method)
                .paymentType(type)
                .status(PaymentStatus.PENDING)
                .build();
        paymentRepository.save(payment);
        rentalOrderRepository.save(order);


        long amountVnp = totalAmount.multiply(BigDecimal.valueOf(100)).longValue();
        Map<String, String> vnpParamsMap = vnpayConfig.getVNPayConfig();
        vnpParamsMap.put("vnp_Amount", String.valueOf(amountVnp));
        vnpParamsMap.put("vnp_IpAddr", clientIp);
        vnpParamsMap.put("vnp_TxnRef", payment.getPaymentId().toString());
        vnpParamsMap.put("vnp_OrderInfo", "Payment for order " + order.getOrderId());

        String queryUrl = Utils.getPaymentURL(vnpParamsMap, true);
        String hashData = Utils.getPaymentURL(vnpParamsMap, false);
        String vnpSecureHash = Utils.hmacSHA512(VNP_SECRET, hashData);
        queryUrl += "&vnp_SecureHash=" + vnpSecureHash;
        String paymentUrl = VNP_URL + "?" + queryUrl;

        return PaymentResponse.builder()
                .paymentId(payment.getPaymentId())
                .orderId(order.getOrderId())
                .amount(totalAmount)
                .method(method)
                .status(payment.getStatus())
                .paymentType(type)
                .paymentUrl(paymentUrl)
                .message("Tạo link thanh toán VNPay thành công")
                .build();
    }

    @Override
    public PaymentResponse handleVNPayCallback(Map<String, String> vnpParams) {
        String responseCode = vnpParams.get("vnp_ResponseCode");
        String txnRef = vnpParams.get("vnp_TxnRef");
        BigDecimal amount = new BigDecimal(vnpParams.get("vnp_Amount"))
                .divide(BigDecimal.valueOf(100));

        UUID paymentId = UUID.fromString(txnRef);
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + txnRef));

        RentalOrder order = payment.getRentalOrder();
        Vehicle vehicle = order.getVehicle();

        if ("00".equals(responseCode)) {
            payment.setStatus(PaymentStatus.SUCCESS);
            handlePaymentSuccess(payment);

        } else {

            payment.setStatus(PaymentStatus.FAILED);
            order.setStatus("PAYMENT_FAILED");

            if (vehicle != null) {
                Vehicle freshVehicle = vehicleRepository.findById(vehicle.getVehicleId())
                        .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));
                freshVehicle.setStatus("AVAILABLE");
                vehicleRepository.save(freshVehicle);
            }

            rentalOrderRepository.save(order);
        }

        paymentRepository.save(payment);

        return PaymentResponse.builder()
                .paymentId(payment.getPaymentId())
                .orderId(order.getOrderId())
                .amount(amount)
                .method(payment.getMethod())
                .status(payment.getStatus())
                .paymentType(payment.getPaymentType())
                .message("00".equals(responseCode) ? "PAYMENT_SUCCESS" : "PAYMENT_FAILED")
                .build();
    }

    @Transactional
    public void handlePaymentSuccess(Payment payment) {
        RentalOrder order = payment.getRentalOrder();
        short type = payment.getPaymentType();

        switch (type) {
            case 1 -> {
                order.setStatus("DEPOSITED");
                order.setDepositAmount(payment.getAmount());
                order.setRemainingAmount(order.getTotalPrice().subtract(payment.getAmount()));
                rentalOrderRepository.save(order);
                recordTransaction(order, payment, "Deposit", "SUCCESS");
            }


            case 2 -> {
                order.setStatus("COMPLETED");
                order.setRemainingAmount(BigDecimal.ZERO);
                rentalOrderRepository.save(order);
                recordTransaction(order, payment, "Final", "SUCCESS");
            }

            default -> throw new IllegalArgumentException("Invalid payment type: " + type);
        }

        payment.setStatus(PaymentStatus.SUCCESS);
        paymentRepository.save(payment);
    }

    private void recordTransaction(RentalOrder order, Payment payment, String type, String status) {
        TransactionHistory history = new TransactionHistory();
        history.setUser(order.getCustomer());
        history.setAmount(payment.getAmount());
        history.setCreatedAt(LocalDateTime.now());
        history.setType(type);
        history.setStatus(status);
        transactionHistoryRepository.save(history);
    }

    @Transactional

    public PaymentResponse refund(UUID orderId) {
        RentalOrder order = rentalOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        BigDecimal refundAmount;

        if ("COMPLETED".equalsIgnoreCase(order.getStatus())) {
            refundAmount = order.getTotalPrice();
        } else if ("DEPOSITED".equalsIgnoreCase(order.getStatus())
                || "PENDING_DEPOSIT".equalsIgnoreCase(order.getStatus())) {
            refundAmount = order.getDepositAmount();
        } else if ("WAITING_FINAL_PAYMENT".equalsIgnoreCase(order.getStatus())) {
            refundAmount = order.getRemainingAmount();
        } else {
            refundAmount = order.getDepositAmount() != null
                    ? order.getDepositAmount()
                    : BigDecimal.ZERO;
        }

        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Không có số tiền nào để hoàn cho đơn này");
        }
        Payment refundPayment = Payment.builder()
                .rentalOrder(order)
                .amount(refundAmount)
                .method("INTERNAL_REFUND")
                .paymentType((short) 3)
                .status(PaymentStatus.SUCCESS)
                .build();
        paymentRepository.save(refundPayment);


        order.setStatus("REFUNDED");
        rentalOrderRepository.save(order);

        Vehicle vehicle = order.getVehicle();
        if (vehicle != null) {
            vehicle.setStatus("AVAILABLE");
            vehicleRepository.save(vehicle);
        }


        recordTransaction(order, refundPayment, "Refund", "SUCCESS");

        return PaymentResponse.builder()
                .paymentId(refundPayment.getPaymentId())
                .orderId(order.getOrderId())
                .amount(refundAmount)
                .method(refundPayment.getMethod())
                .status(refundPayment.getStatus())
                .paymentType(refundPayment.getPaymentType())
                .message("Hoàn tiền thành công (" + refundAmount + " VND) cho đơn " + order.getOrderId())
                .build();
    }

}
