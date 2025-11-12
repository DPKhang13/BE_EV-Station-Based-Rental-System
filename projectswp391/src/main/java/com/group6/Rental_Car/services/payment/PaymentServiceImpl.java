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
    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;

    // ===============================
    //  TẠO LINK THANH TOÁN
    // ===============================
    @Override
    @Transactional
    public PaymentResponse createPaymentUrl(PaymentDto dto, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        RentalOrder order = rentalOrderRepository.findById(dto.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        short type = dto.getPaymentType(); // 1=cọc, 2=thanh toán cuối
        if (type != 1 && type != 2) {
            throw new BadRequestException("Loại thanh toán không hợp lệ (1=cọc, 2=thanh toán cuối)");
        }

        String method = Optional.ofNullable(dto.getMethod()).orElse("VNPAY");

        BigDecimal amount;
        if (type == 1) {
            amount = order.getTotalPrice().multiply(BigDecimal.valueOf(0.5)); // đặt cọc 50%
        } else {
            BigDecimal depositPaid = paymentRepository
                    .findByRentalOrder_OrderId(order.getOrderId()).stream()
                    .filter(p -> p.getPaymentType() == 1 && p.getStatus() == PaymentStatus.SUCCESS)
                    .map(Payment::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            amount = order.getTotalPrice().subtract(depositPaid);
        }

        // đảm bảo không âm
        amount = amount.abs();
        BigDecimal remainingAmount = order.getTotalPrice().subtract(amount);
        if (remainingAmount.compareTo(BigDecimal.ZERO) < 0) remainingAmount = BigDecimal.ZERO;

        // Tạo payment pending
        Payment payment = Payment.builder()
                .rentalOrder(order)
                .amount(amount)
                .remainingAmount(remainingAmount)
                .method(method)
                .paymentType(type)
                .status(PaymentStatus.PENDING)
                .build();
        paymentRepository.save(payment);

        // Tạo order detail tương ứng
        String detailType = (type == 1) ? "DEPOSITED" : "FINAL";
        RentalOrderDetail detail = RentalOrderDetail.builder()
                .order(order)
                .vehicle(order.getDetails().get(0).getVehicle())
                .type(detailType)
                .startTime(LocalDateTime.now())
                .endTime(LocalDateTime.now())
                .price(amount)
                .status("PENDING")
                .description("Thanh toán " + (type == 1 ? "đặt cọc" : "phần còn lại"))
                .build();
        rentalOrderDetailRepository.save(detail);

        // Chuẩn bị URL VNPay
        long vnpAmount = amount.multiply(BigDecimal.valueOf(100)).longValue();
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

        // Cập nhật trạng thái đơn
        order.setStatus(type == 1 ? "PENDING_DEPOSIT" : "PENDING_FINAL");
        rentalOrderRepository.save(order);

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
    //  XỬ LÝ CALLBACK TỪ VNPAY
    // ===============================
    @Override
    @Transactional
    public PaymentResponse handleVNPayCallback(Map<String, String> params) {
        log.info("VNPay callback: {}", params);

        String txnRef = params.get("vnp_TxnRef");
        String responseCode = params.get("vnp_ResponseCode");
        BigDecimal amount = new BigDecimal(params.getOrDefault("vnp_Amount", "0"))
                .divide(BigDecimal.valueOf(100));

        if (txnRef == null || responseCode == null) {
            throw new BadRequestException("VNPay callback thiếu tham số cần thiết");
        }

        UUID paymentId = UUID.fromString(txnRef);
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + txnRef));

        RentalOrder order = payment.getRentalOrder();
        boolean success = "00".equals(responseCode);

        // Lấy order detail tương ứng
        String expectedType = (payment.getPaymentType() == 1) ? "DEPOSITED" : "FINAL";
        RentalOrderDetail detail = rentalOrderDetailRepository
                .findByOrder_OrderId(order.getOrderId()).stream()
                .filter(d -> expectedType.equalsIgnoreCase(d.getType()))
                .reduce((first, second) -> second)
                .orElse(null);

        if (success) {
            payment.setStatus(PaymentStatus.SUCCESS);
            BigDecimal remaining = order.getTotalPrice().subtract(payment.getAmount().abs());
            payment.setRemainingAmount(remaining.max(BigDecimal.ZERO));

            if (detail != null) detail.setStatus("SUCCESS");

            // Cập nhật order cha
            if (payment.getPaymentType() == 1) {
                order.setStatus("DEPOSITED");

                // Sau khi đặt cọc, tạo detail phần còn lại
                if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                    RentalOrderDetail remainingDetail = RentalOrderDetail.builder()
                            .order(order)
                            .vehicle(order.getDetails().get(0).getVehicle())
                            .type("PENDING_FINAL")
                            .startTime(LocalDateTime.now())
                            .endTime(LocalDateTime.now())
                            .price(remaining)
                            .status("PENDING")
                            .description("Phần còn lại chờ thanh toán")
                            .build();
                    rentalOrderDetailRepository.save(remainingDetail);
                }
            } else {
                order.setStatus("PAID");
            }

            recordTransaction(order, payment,
                    payment.getPaymentType() == 1 ? "DEPOSIT" : "FINAL_PAYMENT");

        } else {
            payment.setStatus(PaymentStatus.FAILED);
            order.setStatus("PAYMENT_FAILED");
            if (detail != null) detail.setStatus("FAILED");
        }

        if (detail != null) rentalOrderDetailRepository.save(detail);
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
    //  HOÀN TIỀN (REFUND)
    // ===============================
    @Override
    @Transactional
    public PaymentResponse refund(UUID orderId) {
        RentalOrder order = rentalOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        BigDecimal refundAmount = order.getTotalPrice().abs();
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Không có số tiền nào để hoàn");
        }

        Payment refund = Payment.builder()
                .rentalOrder(order)
                .amount(refundAmount)
                .remainingAmount(BigDecimal.ZERO)
                .method("INTERNAL_REFUND")
                .paymentType((short) 3)
                .status(PaymentStatus.SUCCESS)
                .build();
        paymentRepository.save(refund);

        order.setStatus("REFUNDED");
        rentalOrderRepository.save(order);

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

        recordTransaction(order, refund, "REFUND");

        return PaymentResponse.builder()
                .paymentId(refund.getPaymentId())
                .orderId(order.getOrderId())
                .amount(refundAmount)
                .remainingAmount(BigDecimal.ZERO)
                .method(refund.getMethod())
                .status(refund.getStatus())
                .paymentType(refund.getPaymentType())
                .message("Hoàn tiền thành công")
                .build();
    }

    // ===============================
    //  LƯU LỊCH SỬ GIAO DỊCH
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
}
