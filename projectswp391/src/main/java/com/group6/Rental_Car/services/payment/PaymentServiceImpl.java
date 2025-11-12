package com.group6.Rental_Car.services.payment;

import com.group6.Rental_Car.config.VNpayConfig;
import com.group6.Rental_Car.dtos.payment.PaymentDto;
import com.group6.Rental_Car.dtos.payment.PaymentResponse;
import com.group6.Rental_Car.entities.*;
import com.group6.Rental_Car.enums.PaymentStatus;
import com.group6.Rental_Car.exceptions.BadRequestException;
import com.group6.Rental_Car.exceptions.ResourceNotFoundException;
import com.group6.Rental_Car.repositories.*;
import com.group6.Rental_Car.utils.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    @Value("${VNP_HASHSECRET}")
    private String VNP_SECRET;
    @Value("${VNP_URL}")
    private String VNP_URL;

    private final VNpayConfig vnpayConfig;
    private final RentalOrderRepository rentalOrderRepository;
    private final RentalOrderDetailRepository rentalOrderDetailRepository;
    private final PaymentRepository paymentRepository;
    private final TransactionHistoryRepository transactionHistoryRepository;
    private final UserRepository userRepository;

    // ===============================
    // 🔹 TẠO LINK THANH TOÁN / CẬP NHẬT PAYMENT
    // ===============================
    @Override
    @Transactional
    public PaymentResponse createPaymentUrl(PaymentDto dto, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        RentalOrder order = rentalOrderRepository.findById(dto.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        short type = dto.getPaymentType(); // 1=cọc, 2=phần còn lại, 3=toàn bộ, 4=hoàn tiền
        if (type < 1 || type > 4)
            throw new BadRequestException("paymentType phải là 1(cọc), 2(phần còn lại), 3(toàn bộ), 4(hoàn tiền)");

        String method = Optional.ofNullable(dto.getMethod()).orElse("VNPAY");
        BigDecimal total = order.getTotalPrice().abs();

        // Lấy payment hiện có của order
        Payment payment = paymentRepository.findByRentalOrder_OrderId(order.getOrderId())
                .stream()
                .findFirst()
                .orElseGet(() -> {
                    Payment p = Payment.builder()
                            .rentalOrder(order)
                            .amount(BigDecimal.ZERO)
                            .remainingAmount(total)
                            .method(method)
                            .paymentType((short) 1)
                            .status(PaymentStatus.PENDING)
                            .build();
                    return paymentRepository.save(p);
                });

        // Xác định số tiền thanh toán
        BigDecimal amount;
        switch (type) {
            case 1 -> amount = total.multiply(BigDecimal.valueOf(0.5)); // đặt cọc
            case 2 -> amount = total.subtract(payment.getAmount());     // phần còn lại
            case 3 -> amount = total;                                   // toàn bộ
            case 4 -> amount = total.negate();                          // hoàn tiền
            default -> throw new BadRequestException("Loại thanh toán không hợp lệ");
        }

        // Nếu là hoàn tiền
        if (type == 4) return handleRefund(order, payment, amount);

        // Cập nhật payment hiện tại
        BigDecimal newAmount = (type == 3) ? total : payment.getAmount().add(amount);
        BigDecimal newRemaining = total.subtract(newAmount).max(BigDecimal.ZERO);
        payment.setAmount(newAmount);
        payment.setRemainingAmount(newRemaining);
        payment.setPaymentType(type);
        payment.setMethod(method);
        payment.setStatus(PaymentStatus.PENDING);
        paymentRepository.save(payment);

        // Cập nhật trạng thái order
        switch (type) {
            case 1 -> order.setStatus("PENDING_DEPOSIT");
            case 2 -> order.setStatus("PENDING_FINAL");
            case 3 -> order.setStatus("PENDING_FULL_PAYMENT");
        }
        rentalOrderRepository.save(order);

        // Ghi chi tiết transaction
        if (type == 2) {
            boolean hasPickup = order.getDetails().stream()
                    .anyMatch(d -> "PICKUP".equalsIgnoreCase(d.getType()));
            if (!hasPickup) {
                RentalOrderDetail pickup = RentalOrderDetail.builder()
                        .order(order)
                        .vehicle(order.getDetails().get(0).getVehicle())
                        .type("PICKUP")
                        .startTime(LocalDateTime.now())
                        .endTime(LocalDateTime.now())
                        .price(amount.abs())
                        .status("PENDING")
                        .description("Thanh toán phần còn lại khi nhận xe")
                        .build();
                rentalOrderDetailRepository.save(pickup);
            }
        }

        // Tạo link VNPay
        long vnpAmount = amount.abs().multiply(BigDecimal.valueOf(100)).longValue();
        Map<String, String> vnpParams = vnpayConfig.getVNPayConfig();
        vnpParams.put("vnp_Amount", String.valueOf(vnpAmount));
        vnpParams.put("vnp_TxnRef", payment.getPaymentId().toString());
        vnpParams.put("vnp_OrderInfo", "Thanh toán đơn " + order.getOrderId());
        vnpParams.put("vnp_IpAddr", "127.0.0.1");

        String queryUrl = Utils.getPaymentURL(vnpParams, true);
        String hashData = Utils.getPaymentURL(vnpParams, false);
        String vnpSecureHash = Utils.hmacSHA512(VNP_SECRET, hashData);
        queryUrl += "&vnp_SecureHash=" + vnpSecureHash;
        String paymentUrl = VNP_URL + "?" + queryUrl;

        return PaymentResponse.builder()
                .paymentId(payment.getPaymentId())
                .orderId(order.getOrderId())
                .amount(amount)
                .remainingAmount(payment.getRemainingAmount())
                .method(method)
                .status(payment.getStatus())
                .paymentType(type)
                .paymentUrl(paymentUrl)
                .message("Tạo link thanh toán VNPay thành công")
                .build();
    }

    // ===============================
    // 🔹 CALLBACK VNPAY
    // ===============================
    @Override
    @Transactional
    public PaymentResponse handleVNPayCallback(Map<String, String> params) {
        log.info("VNPay callback: {}", params);

        String txnRef = params.get("vnp_TxnRef");
        String responseCode = params.get("vnp_ResponseCode");
        if (txnRef == null || responseCode == null)
            throw new BadRequestException("VNPay callback thiếu tham số cần thiết");

        UUID paymentId = UUID.fromString(txnRef);
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        RentalOrder order = payment.getRentalOrder();

        boolean success = "00".equals(responseCode);
        if (success) {
            payment.setStatus(PaymentStatus.SUCCESS);

            switch (payment.getPaymentType()) {
                case 1 -> {
                    order.setStatus("DEPOSITED");

                    BigDecimal remaining = order.getTotalPrice().subtract(payment.getAmount());
                    payment.setRemainingAmount(remaining.max(BigDecimal.ZERO));

                    //  Cập nhật DEPOSITED → SUCCESS
                    rentalOrderDetailRepository.findByOrder_OrderId(order.getOrderId())
                            .stream()
                            .filter(d -> "DEPOSITED".equalsIgnoreCase(d.getType()))
                            .reduce((first, second) -> second)
                            .ifPresent(detail -> {
                                detail.setStatus("SUCCESS");
                                rentalOrderDetailRepository.save(detail);
                            });

                    //  Tạo PICKUP nếu còn tiền
                    boolean hasPickup = order.getDetails().stream()
                            .anyMatch(d -> "PICKUP".equalsIgnoreCase(d.getType()));

                    if (!hasPickup && remaining.compareTo(BigDecimal.ZERO) > 0) {
                        RentalOrderDetail pickup = RentalOrderDetail.builder()
                                .order(order)
                                .vehicle(order.getDetails().get(0).getVehicle())
                                .type("PICKUP")
                                .startTime(LocalDateTime.now())
                                .endTime(LocalDateTime.now())
                                .price(remaining)
                                .status("PENDING")
                                .description("Thanh toán phần còn lại khi nhận xe")
                                .build();
                        rentalOrderDetailRepository.save(pickup);
                    }
                }

                case 2 -> {
                    order.setStatus("PAID");
                    payment.setRemainingAmount(BigDecimal.ZERO);

                    //  Cập nhật PICKUP → SUCCESS
                    rentalOrderDetailRepository.findByOrder_OrderId(order.getOrderId())
                            .stream()
                            .filter(d -> "PICKUP".equalsIgnoreCase(d.getType()))
                            .reduce((first, second) -> second)
                            .ifPresent(detail -> {
                                detail.setStatus("SUCCESS");
                                rentalOrderDetailRepository.save(detail);
                            });
                }

                case 3 -> {
                    order.setStatus("PAID");
                    payment.setRemainingAmount(BigDecimal.ZERO);

                    // ✅ Cập nhật FULL_PAYMENT → SUCCESS
                    rentalOrderDetailRepository.findByOrder_OrderId(order.getOrderId())
                            .stream()
                            .filter(d -> "FULL_PAYMENT".equalsIgnoreCase(d.getType()))
                            .reduce((first, second) -> second)
                            .ifPresent(detail -> {
                                detail.setStatus("SUCCESS");
                                rentalOrderDetailRepository.save(detail);
                            });
                }
            }

            recordTransaction(order, payment, getTypeNameByPayment(payment.getPaymentType()));
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            order.setStatus("PAYMENT_FAILED");
        }

        paymentRepository.save(payment);
        rentalOrderRepository.save(order);

        return PaymentResponse.builder()
                .paymentId(payment.getPaymentId())
                .orderId(order.getOrderId())
                .amount(payment.getAmount())
                .remainingAmount(payment.getRemainingAmount())
                .method(payment.getMethod())
                .status(payment.getStatus())
                .paymentType(payment.getPaymentType())
                .message(success ? "PAYMENT_SUCCESS" : "PAYMENT_FAILED")
                .build();
    }

    // ===============================
    // 🔹 HOÀN TIỀN
    // ===============================
    private PaymentResponse handleRefund(RentalOrder order, Payment payment, BigDecimal amount) {
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaymentType((short) 4);
        order.setStatus("REFUNDED");
        paymentRepository.save(payment);
        rentalOrderRepository.save(order);

        RentalOrderDetail refundDetail = RentalOrderDetail.builder()
                .order(order)
                .vehicle(order.getDetails().get(0).getVehicle())
                .type("REFUND")
                .startTime(LocalDateTime.now())
                .endTime(LocalDateTime.now())
                .price(amount.abs())
                .status("SUCCESS")
                .description("Hoàn tiền đơn thuê #" + order.getOrderId())
                .build();
        rentalOrderDetailRepository.save(refundDetail);

        recordTransaction(order, payment, "REFUND");

        return PaymentResponse.builder()
                .paymentId(payment.getPaymentId())
                .orderId(order.getOrderId())
                .amount(amount.abs())
                .remainingAmount(BigDecimal.ZERO)
                .method("INTERNAL_REFUND")
                .status(PaymentStatus.SUCCESS)
                .paymentType((short) 4)
                .message("Hoàn tiền thành công")
                .build();
    }

    // ===============================
    // 🔹 TIỆN ÍCH
    // ===============================
    private void recordTransaction(RentalOrder order, Payment payment, String type) {
        TransactionHistory history = new TransactionHistory();
        history.setUser(order.getCustomer());
        history.setAmount(payment.getAmount().abs());
        history.setType(type);
        history.setStatus("SUCCESS");
        history.setCreatedAt(LocalDateTime.now());
        transactionHistoryRepository.save(history);
    }

    private String getDescriptionByType(short type) {
        return switch (type) {
            case 1 -> "Thanh toán đặt cọc";
            case 2 -> "Thanh toán phần còn lại";
            case 3 -> "Thanh toán toàn bộ đơn thuê";
            case 4 -> "Hoàn tiền cho khách hàng";
            default -> "Giao dịch không xác định";
        };
    }

    private String getTypeNameByPayment(short type) {
        return switch (type) {
            case 1 -> "DEPOSITED";
            case 2 -> "FINAL";
            case 3 -> "FULL_PAYMENT";
            case 4 -> "REFUND";
            default -> "UNKNOWN";
        };
    }

@Override
    @Transactional
    public PaymentResponse refund(UUID orderId) {
        RentalOrder order = rentalOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        Payment payment = paymentRepository.findByRentalOrder_OrderId(orderId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for order"));

        BigDecimal refundAmount = payment.getAmount().abs();
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Không có số tiền nào để hoàn");
        }

        // Cập nhật lại payment và order
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaymentType((short) 4);
        order.setStatus("REFUNDED");
        paymentRepository.save(payment);
        rentalOrderRepository.save(order);

        // Tạo order detail hoàn tiền
        RentalOrderDetail refundDetail = RentalOrderDetail.builder()
                .order(order)
                .vehicle(order.getDetails().get(0).getVehicle())
                .type("REFUND")
                .startTime(LocalDateTime.now())
                .endTime(LocalDateTime.now())
                .price(refundAmount)
                .status("SUCCESS")
                .description("Hoàn tiền đơn thuê #" + order.getOrderId())
                .build();
        rentalOrderDetailRepository.save(refundDetail);

        recordTransaction(order, payment, "REFUND");

        return PaymentResponse.builder()
                .paymentId(payment.getPaymentId())
                .orderId(order.getOrderId())
                .amount(refundAmount)
                .remainingAmount(BigDecimal.ZERO)
                .method("INTERNAL_REFUND")
                .status(PaymentStatus.SUCCESS)
                .paymentType((short) 4)
                .message("Hoàn tiền thành công")
                .build();
    }
}
