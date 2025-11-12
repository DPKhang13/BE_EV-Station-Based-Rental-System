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
    //  TẠO LINK / GIAO DỊCH THANH TOÁN
    // ===============================
    @Override
    @Transactional
    public PaymentResponse createPaymentUrl(PaymentDto dto, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        RentalOrder order = rentalOrderRepository.findById(dto.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        short type = dto.getPaymentType(); // 1=cọc, 2=phần còn lại, 3=toàn bộ, 4=hoàn tiền
        if (type < 1 || type > 4) {
            throw new BadRequestException("paymentType phải là 1(cọc), 2(phần còn lại), 3(toàn bộ), 4(hoàn tiền)");
        }

        String method = Optional.ofNullable(dto.getMethod()).orElse("VNPAY");

        BigDecimal total = order.getTotalPrice().abs();
        BigDecimal amount;

        // Xác định số tiền cần thanh toán
        switch (type) {
            case 1 -> amount = total.multiply(BigDecimal.valueOf(0.5)); // Đặt cọc 50%
            case 2 -> {
                // phần còn lại = tổng - đã cọc
                BigDecimal depositPaid = paymentRepository.findByRentalOrder_OrderId(order.getOrderId())
                        .stream()
                        .filter(p -> p.getPaymentType() == 1 && p.getStatus() == PaymentStatus.SUCCESS)
                        .map(Payment::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                amount = total.subtract(depositPaid);
                if (amount.compareTo(BigDecimal.ZERO) <= 0)
                    throw new BadRequestException("Không có số tiền nào cần thanh toán thêm");
            }
            case 3 -> amount = total; // full payment
            case 4 -> amount = total.negate(); // hoàn tiền
            default -> throw new BadRequestException("Loại thanh toán không hợp lệ");
        }

        BigDecimal remainingAmount = total.subtract(amount.abs());
        if (remainingAmount.compareTo(BigDecimal.ZERO) < 0) remainingAmount = BigDecimal.ZERO;

        // Tạo record Payment
        Payment payment = Payment.builder()
                .rentalOrder(order)
                .amount(amount)
                .remainingAmount(remainingAmount)
                .method(method)
                .paymentType(type)
                .status(PaymentStatus.PENDING)
                .build();
        paymentRepository.save(payment);

        // Tạo order detail
        String detailType = switch (type) {
            case 1 -> "DEPOSITED";
            case 2 -> "FINAL";
            case 3 -> "FULL_PAYMENT";
            case 4 -> "REFUND";
            default -> "UNKNOWN";
        };

        RentalOrderDetail detail = RentalOrderDetail.builder()
                .order(order)
                .vehicle(order.getDetails().get(0).getVehicle())
                .type(detailType)
                .startTime(LocalDateTime.now())
                .endTime(LocalDateTime.now())
                .price(amount.abs())
                .status("PENDING")
                .description(getDescriptionByType(type))
                .build();
        rentalOrderDetailRepository.save(detail);

        //  Nếu là refund thì xử lý ngay — không tạo link VNPay
        if (type == 4) {
            payment.setStatus(PaymentStatus.SUCCESS);
            order.setStatus("REFUNDED");
            paymentRepository.save(payment);
            rentalOrderRepository.save(order);
            detail.setStatus("SUCCESS");
            rentalOrderDetailRepository.save(detail);

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

        //  Tạo URL VNPay
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

        // Cập nhật trạng thái đơn
        switch (type) {
            case 1 -> order.setStatus("PENDING_DEPOSIT");
            case 2 -> order.setStatus("PENDING_FINAL");
            case 3 -> order.setStatus("PENDING_FULL_PAYMENT");
        }
        rentalOrderRepository.save(order);

        return PaymentResponse.builder()
                .paymentId(payment.getPaymentId())
                .orderId(order.getOrderId())
                .amount(amount)
                .remainingAmount(remainingAmount)
                .method(method)
                .status(payment.getStatus())
                .paymentType(type)
                .paymentUrl(paymentUrl)
                .message("Tạo link thanh toán VNPay thành công")
                .build();
    }

    // ===============================
    //  XỬ LÝ CALLBACK VNPAY
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

        String expectedType = switch (payment.getPaymentType()) {
            case 1 -> "DEPOSITED";
            case 2 -> "FINAL";
            case 3 -> "FULL_PAYMENT";
            default -> "UNKNOWN";
        };

        RentalOrderDetail detail = rentalOrderDetailRepository.findByOrder_OrderId(order.getOrderId())
                .stream()
                .filter(d -> expectedType.equalsIgnoreCase(d.getType()))
                .reduce((first, second) -> second)
                .orElse(null);

        if (success) {
            payment.setStatus(PaymentStatus.SUCCESS);
            if (detail != null) detail.setStatus("SUCCESS");

            switch (payment.getPaymentType()) {
                case 1 -> order.setStatus("DEPOSITED");
                case 2, 3 -> order.setStatus("PAID");
            }

            recordTransaction(order, payment,
                    payment.getPaymentType() == 1 ? "DEPOSIT"
                            : payment.getPaymentType() == 2 ? "FINAL_PAYMENT"
                            : "FULL_PAYMENT");

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
                .paymentType((short) 4)
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
                .method("INTERNAL_REFUND")
                .status(PaymentStatus.SUCCESS)
                .paymentType((short) 4)
                .message("Hoàn tiền thành công")
                .build();
    }
}
