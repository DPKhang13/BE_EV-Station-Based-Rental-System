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
import java.util.*;

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
    private final OrderServiceRepository orderServiceRepository;

    // ============================================================
    // CREATE PAYMENT URL
    // ============================================================
    @Override
    @Transactional
    public PaymentResponse createPaymentUrl(PaymentDto dto, UUID userId) {

        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        RentalOrder order = rentalOrderRepository.findById(dto.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        short type = dto.getPaymentType();
        if (type < 1 || type > 5)
            throw new BadRequestException("Invalid payment type");

        // ============================
        // TYPE 5 - SERVICE PAYMENT
        // ============================
        if (type == 5)
            return createServicePayment(order);

        Vehicle vehicle = getMainVehicle(order);
        BigDecimal total = order.getTotalPrice();
        String method = Optional.ofNullable(dto.getMethod()).orElse("VNPAY");

        // ============================
        // CALC AMOUNT dựa vào type
        // ============================
        BigDecimal amount;
        BigDecimal remainingAmount;

        if (type == 1) {
            // Deposit 50%
            amount = total.divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP);
            remainingAmount = total.subtract(amount);
        } else if (type == 2) {
            // Thanh toán còn lại - lấy từ payment deposit
            Payment depositPayment = paymentRepository.findByRentalOrder_OrderId(order.getOrderId())
                    .stream()
                    .filter(p -> p.getPaymentType() == 1 && p.getStatus() == PaymentStatus.SUCCESS)
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("Must pay deposit first"));

            amount = depositPayment.getRemainingAmount();
            remainingAmount = BigDecimal.ZERO;
        } else if (type == 3) {
            // Full payment
            amount = total;
            remainingAmount = BigDecimal.ZERO;
        } else {
            amount = BigDecimal.ZERO;
            remainingAmount = BigDecimal.ZERO;
        }

        // ============================
        // TẠO PAYMENT MỚI cho mỗi giao dịch
        // ============================
        Payment payment = paymentRepository.save(
                Payment.builder()
                        .rentalOrder(order)
                        .amount(amount)
                        .remainingAmount(remainingAmount)
                        .method(method)
                        .paymentType(type)
                        .status(PaymentStatus.PENDING)
                        .build()
        );

        log.info("✅ Created new payment {} with amount={}, remaining={}, type={}",
                payment.getPaymentId(), payment.getAmount(), payment.getRemainingAmount(), type);

        updateOrderStatus(order, type);

        // TYPE != 2 -> create DEPOSIT or FULL_PAYMENT detail
        if (type != 2) {
            createOrUpdateDetail(order, vehicle, getTypeName(type), amount, getDescription(type));
        }

        return buildVNPayReturn(order, payment, amount);
    }

    // ============================================================
    // TYPE 5 — SERVICE PAYMENT
    // ============================================================
    private PaymentResponse createServicePayment(RentalOrder order) {

        List<OrderService> pending = orderServiceRepository
                .findByOrder_OrderId(order.getOrderId())
                .stream()
                .filter(s -> !"SUCCESS".equalsIgnoreCase(s.getStatus()))
                .toList();

        if (pending.isEmpty())
            throw new BadRequestException("No unpaid services found");

        BigDecimal amount = pending.stream()
                .map(s -> Optional.ofNullable(s.getCost()).orElse(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Payment payment = paymentRepository.save(
                Payment.builder()
                        .rentalOrder(order)
                        .amount(amount)
                        .remainingAmount(BigDecimal.ZERO)
                        .paymentType((short) 5)
                        .method("VNPAY")
                        .status(PaymentStatus.PENDING)
                        .build()
        );

        order.setStatus("PENDING_SERVICE_PAYMENT");
        rentalOrderRepository.save(order);

        return buildVNPayReturn(order, payment, amount);
    }

    // ============================================================
    // CALLBACK — VNPay
    // ============================================================
    @Override
    @Transactional
    public PaymentResponse handleVNPayCallback(Map<String, String> params) {

        String txnRef = params.get("vnp_TxnRef");
        if (txnRef == null)
            throw new BadRequestException("Missing txnRef");

        log.info("📥 Callback received - txnRef: {}", txnRef);

        String raw = txnRef.split("-")[0];
        String uuid = raw.replaceFirst(
                "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})",
                "$1-$2-$3-$4-$5"
        );

        log.info("🔍 Parsed paymentId: {}", uuid);

        Payment payment = paymentRepository.findById(UUID.fromString(uuid))
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        log.info("💳 Found payment: id={}, type={}, amount={}, remainingAmount={}",
                payment.getPaymentId(), payment.getPaymentType(),
                payment.getAmount(), payment.getRemainingAmount());

        RentalOrder order = payment.getRentalOrder();
        boolean ok = "00".equals(params.get("vnp_ResponseCode"));

        if (!ok) {
            payment.setStatus(PaymentStatus.FAILED);
            order.setStatus("PAYMENT_FAILED");
            paymentRepository.save(payment);
            rentalOrderRepository.save(order);
            return buildCallbackResponse(order, payment, false);
        }

        // Success
        payment.setStatus(PaymentStatus.SUCCESS);

        // SERVICE PAYMENT
        if (payment.getPaymentType() == 5) {
            handleServiceSuccess(order, payment);
            return buildCallbackResponse(order, payment, true);
        }

        Vehicle v = getMainVehicle(order);

        switch (payment.getPaymentType()) {
            case 1 -> depositSuccess(order, payment, v);
            case 2 -> finalSuccess(order, payment);
            case 3 -> fullSuccess(order, payment, v);
        }

        paymentRepository.save(payment);
        rentalOrderRepository.save(order);

        recordTransaction(order, payment, getTypeName(payment.getPaymentType()));

        return buildCallbackResponse(order, payment, true);
    }

    // ============================================================
    // SERVICE SUCCESS
    // ============================================================
    private void handleServiceSuccess(RentalOrder order, Payment payment) {

        List<OrderService> pending = orderServiceRepository
                .findByOrder_OrderId(order.getOrderId())
                .stream()
                .filter(s -> !"SUCCESS".equalsIgnoreCase(s.getStatus()))
                .toList();

        pending.forEach(s -> {
            s.setStatus("SUCCESS");
            s.setResolvedAt(LocalDateTime.now());
            orderServiceRepository.save(s);
        });


        order.setStatus("SERVICE_PAID");
        paymentRepository.save(payment);
        rentalOrderRepository.save(order);

        recordTransaction(order, payment, "SERVICE");
    }

    // ============================================================
    // RENTAL PAYMENT SUCCESS
    // ============================================================

    // TYPE 1 — Deposit Success
    private void depositSuccess(RentalOrder order, Payment payment, Vehicle v) {

        order.setStatus("DEPOSITED");

        // amount và remainingAmount đã được set khi tạo payment
        BigDecimal deposit = payment.getAmount();

        // Create deposit detail
        createOrUpdateDetail(order, v, "DEPOSIT", deposit, "Thanh toán đặt cọc");

        // AUTO CREATE PICKUP ONCE - nếu có remainingAmount
        if (payment.getRemainingAmount() != null && payment.getRemainingAmount().compareTo(BigDecimal.ZERO) > 0) {
            createDetail(order, v, "PICKUP", payment.getRemainingAmount(), "Thanh toán phần còn lại");
        }
    }

    // TYPE 2 — Final Payment Success
    private void finalSuccess(RentalOrder order, Payment payment) {

        RentalOrderDetail pickup = rentalOrderDetailRepository
                .findByOrder_OrderId(order.getOrderId())
                .stream()
                .filter(d -> d.getType().equals("PICKUP"))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Missing PICKUP detail"));

        if ("SUCCESS".equalsIgnoreCase(pickup.getStatus()))
            throw new BadRequestException("Pickup already paid");

        BigDecimal finalAmount = payment.getAmount();

        order.setStatus("PAID");
        payment.setRemainingAmount(BigDecimal.ZERO);

        // CẬP NHẬT remainingAmount của payment deposit về 0
        Payment depositPayment = paymentRepository.findByRentalOrder_OrderId(order.getOrderId())
                .stream()
                .filter(p -> p.getPaymentType() == 1 && p.getStatus() == PaymentStatus.SUCCESS)
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Deposit payment not found"));

        depositPayment.setRemainingAmount(BigDecimal.ZERO);
        paymentRepository.save(depositPayment);

        pickup.setStatus("SUCCESS");
        pickup.setPrice(finalAmount);
        rentalOrderDetailRepository.save(pickup);
    }

    // TYPE 3 — Full Payment Success
    private void fullSuccess(RentalOrder order, Payment payment, Vehicle v) {

        order.setStatus("PAID");

        BigDecimal fullAmount = payment.getAmount();
        payment.setRemainingAmount(BigDecimal.ZERO);

        createOrUpdateDetail(order, v, "FULL_PAYMENT", fullAmount, "Thanh toán toàn bộ đơn");
    }

    private Vehicle getMainVehicle(RentalOrder order) {
        return order.getDetails().stream()
                .filter(d -> d.getType().equals("RENTAL"))
                .map(RentalOrderDetail::getVehicle)
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Missing RENTAL detail"));
    }

    private void createOrUpdateDetail(RentalOrder order, Vehicle v, String type, BigDecimal price, String desc) {

        Optional<RentalOrderDetail> opt = rentalOrderDetailRepository
                .findByOrder_OrderId(order.getOrderId())
                .stream()
                .filter(d -> d.getType().equals(type))
                .findFirst();

        if (opt.isPresent()) {
            RentalOrderDetail d = opt.get();
            d.setPrice(price);
            d.setStatus("SUCCESS");
            d.setDescription(desc);
            d.setStartTime(LocalDateTime.now());
            d.setEndTime(LocalDateTime.now());
            rentalOrderDetailRepository.save(d);
        } else {
            createDetail(order, v, type, price, desc);
        }
    }

    private void createDetail(RentalOrder order, Vehicle v, String type, BigDecimal price, String desc) {

        RentalOrderDetail detail = RentalOrderDetail.builder()
                .order(order)
                .vehicle(v)
                .type(type)
                .startTime(LocalDateTime.now())
                .endTime(LocalDateTime.now())
                .price(price)
                .status("PENDING")
                .description(desc)
                .build();

        rentalOrderDetailRepository.save(detail);
    }

    private PaymentResponse buildVNPayReturn(RentalOrder order, Payment payment, BigDecimal amount) {

        // Validate amount
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Payment amount must be greater than 0");
        }

        Map<String, String> params = vnpayConfig.getVNPayConfig();

        // Convert to integer (VNPay requires integer, no decimals)
        long amountInVND = amount.multiply(BigDecimal.valueOf(100)).longValue();
        params.put("vnp_Amount", String.valueOf(amountInVND));

        String encoded = payment.getPaymentId().toString().replace("-", "");
        String txnRef = encoded + "-" + System.currentTimeMillis();

        params.put("vnp_TxnRef", txnRef);
        // Remove diacritics and special chars from OrderInfo
        params.put("vnp_OrderInfo", "Order " + order.getOrderId());
        params.put("vnp_IpAddr", "127.0.0.1");

        log.info(" VNPay Params before signing:");
        params.forEach((k, v) -> log.info("  {} = {}", k, v));

        String query = Utils.getPaymentURL(params, true);
        String hashData = Utils.getPaymentURL(params, false);
        String secureHash = Utils.hmacSHA512(VNP_SECRET, hashData);

        String paymentUrl = VNP_URL + "?" + query + "&vnp_SecureHash=" + secureHash;

        log.info(" Payment URL: {}", paymentUrl);
        log.info(" Hash Data: {}", hashData);
        log.info(" Secure Hash: {}", secureHash);

        return PaymentResponse.builder()
                .paymentId(payment.getPaymentId())
                .orderId(order.getOrderId())
                .amount(amount)
                .remainingAmount(payment.getRemainingAmount())
                .paymentType(payment.getPaymentType())
                .method(payment.getMethod())
                .status(payment.getStatus())
                .paymentUrl(paymentUrl)
                .build();
    }

    private PaymentResponse buildCallbackResponse(RentalOrder order, Payment payment, boolean success) {

        return PaymentResponse.builder()
                .paymentId(payment.getPaymentId())
                .orderId(order.getOrderId())
                .amount(payment.getAmount())
                .remainingAmount(payment.getRemainingAmount())
                .method(payment.getMethod())
                .paymentType(payment.getPaymentType())
                .status(payment.getStatus())
                .message(success ? "PAYMENT_SUCCESS" : "PAYMENT_FAILED")
                .build();
    }

    private void recordTransaction(RentalOrder order, Payment payment, String type) {

        TransactionHistory h = new TransactionHistory();
        h.setUser(order.getCustomer());
        h.setAmount(payment.getAmount());
        h.setType(type);
        h.setStatus("SUCCESS");
        h.setCreatedAt(LocalDateTime.now());

        transactionHistoryRepository.save(h);
    }

    private void updateOrderStatus(RentalOrder order, short type) {
        switch (type) {
            case 1 -> order.setStatus("PENDING_DEPOSIT");
            case 2 -> order.setStatus("PENDING_FINAL");
            case 3 -> order.setStatus("PENDING_FULL_PAYMENT");
        }
        rentalOrderRepository.save(order);
    }

    private String getTypeName(short type) {
        return switch (type) {
            case 1 -> "DEPOSIT";
            case 2 -> "PICKUP";
            case 3 -> "FULL_PAYMENT";
            case 4 -> "REFUND";
            case 5 -> "SERVICE";
            default -> "UNKNOWN";
        };
    }

    private String getDescription(short type) {
        return switch (type) {
            case 1 -> "Thanh toán đặt cọc";
            case 2 -> "Thanh toán phần còn lại";
            case 3 -> "Thanh toán toàn bộ đơn thuê";
            case 4 -> "Hoàn tiền";
            case 5 -> "Thanh toán dịch vụ phát sinh";
            default -> "Không xác định";
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

        BigDecimal refundAmount = payment.getAmount();
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0)
            throw new BadRequestException("Không có số tiền nào để hoàn");

        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaymentType((short) 4);
        order.setStatus("REFUNDED");

        paymentRepository.save(payment);
        rentalOrderRepository.save(order);

        RentalOrderDetail refundDetail = RentalOrderDetail.builder()
                .order(order)
                .vehicle(order.getDetails().getFirst().getVehicle())
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