package com.group6.Rental_Car.services.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group6.Rental_Car.config.MoMoConfig;
import com.group6.Rental_Car.dtos.payment.MomoCreatePaymentRequest;
import com.group6.Rental_Car.dtos.payment.MomoCreatePaymentResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;


@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final MoMoConfig momoConfig;
    private final ObjectMapper objectMapper;
    private final RentalOrderRepository rentalOrderRepository;
    private final RentalOrderDetailRepository rentalOrderDetailRepository;
    private final PaymentRepository paymentRepository;
    private final TransactionHistoryRepository transactionHistoryRepository;
    private final UserRepository userRepository;
    private final OrderServiceRepository orderServiceRepository;
    private final VehicleRepository vehicleRepository;
    private final VehicleTimelineRepository vehicleTimelineRepository;

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

        // Validate payment method
        String method = Optional.ofNullable(dto.getMethod()).orElse("momo");

        // Hạn chế các method hợp lệ
        List<String> validMethods = List.of("captureWallet", "payWithMethod", "momo");

        if (!validMethods.contains(method)) {
            throw new BadRequestException("Invalid MOMO method: " + method);
        }

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

        // TYPE != 2 -> create DEPOSIT or FULL_PAYMENT detail with PENDING status
        if (type != 2) {
            createOrUpdateDetail(order, vehicle, getTypeName(type), amount, getDescription(type), "PENDING");
        }

        return buildMoMoPaymentUrl(order, payment, amount);
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
                        .method("MOMO")
                        .status(PaymentStatus.PENDING)
                        .build()
        );

        order.setStatus("PENDING_SERVICE_PAYMENT");
        rentalOrderRepository.save(order);

        return buildMoMoPaymentUrl(order, payment, amount);
    }

    // ============================================================
    // CALLBACK — MoMo
    // ============================================================
    @Override
    @Transactional
    public PaymentResponse handleMoMoCallback(Map<String, String> params) {

        String orderId = params.get("orderId");
        if (orderId == null)
            throw new BadRequestException("Missing orderId in MoMo callback");

        log.info("📥 MoMo Callback received - orderId: {}", orderId);

        // Extract paymentId from orderId (format: {paymentId}-{timestamp})
        String raw = orderId.split("-")[0];
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

        // Verify payment method is MOMO
        RentalOrder order = payment.getRentalOrder();

        // MoMo resultCode: 0 = success
        String resultCode = params.get("resultCode");
        boolean ok = "0".equals(resultCode);

        if (!ok) {
            payment.setStatus(PaymentStatus.FAILED);
            order.setStatus("PAYMENT_FAILED");
            paymentRepository.save(payment);
            rentalOrderRepository.save(order);
            log.error("❌ MoMo payment failed - resultCode: {}, message: {}",
                    resultCode, params.get("message"));
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

        // 1) Cập nhật OrderService → SUCCESS
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

        // 🔥 2) Cập nhật RentalOrderDetail type SERVICE → SUCCESS
        rentalOrderDetailRepository.findByOrder_OrderId(order.getOrderId())
                .stream()
                .filter(d -> "SERVICE_SERVICE".equalsIgnoreCase(d.getType()))
                .forEach(d -> {
                    d.setStatus("SUCCESS");
                    rentalOrderDetailRepository.save(d);
                });

        // 3) Cập nhật trạng thái Order
        if ("PENDING_FINAL_PAYMENT".equalsIgnoreCase(order.getStatus())) {
            order.setStatus("COMPLETED");

            Vehicle vehicle = getMainVehicle(order);
            if (vehicle != null) {
                deleteTimelineForOrder(order.getOrderId(), vehicle.getVehicleId());
            }
        } else {
            order.setStatus("SERVICE_PAID");
        }

        paymentRepository.save(payment);
        rentalOrderRepository.save(order);

        // 4) Lưu lịch sử giao dịch
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
        createOrUpdateDetail(order, v, "DEPOSIT", deposit, "Thanh toán đặt cọc", "SUCCESS");

        // AUTO CREATE PICKUP ONCE - nếu có remainingAmount
        if (payment.getRemainingAmount() != null && payment.getRemainingAmount().compareTo(BigDecimal.ZERO) > 0) {
            createOrUpdateDetail(order, v, "PICKUP", payment.getRemainingAmount(), "Thanh toán phần còn lại", "PENDING");
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

        createOrUpdateDetail(order, v, "FULL_PAYMENT", fullAmount, "Thanh toán toàn bộ đơn", "SUCCESS");
    }

    private Vehicle getMainVehicle(RentalOrder order) {
        return order.getDetails().stream()
                .filter(d -> d.getType().equals("RENTAL"))
                .map(RentalOrderDetail::getVehicle)
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Missing RENTAL detail"));
    }


    private void createOrUpdateDetail(RentalOrder order, Vehicle v, String type, BigDecimal price, String desc, String status) {

        Optional<RentalOrderDetail> opt = rentalOrderDetailRepository
                .findByOrder_OrderId(order.getOrderId())
                .stream()
                .filter(d -> d.getType().equals(type))
                .findFirst();

        if (opt.isPresent()) {
            RentalOrderDetail d = opt.get();
            d.setPrice(price);
            d.setStatus(status);
            d.setDescription(desc);
            // Don't update startTime/endTime for payment details
            rentalOrderDetailRepository.save(d);
        } else {
            createDetail(order, v, type, price, desc, status);
        }
    }


    private void createDetail(RentalOrder order, Vehicle v, String type, BigDecimal price, String desc, String status) {
        // Lấy startTime và endTime từ detail RENTAL
        RentalOrderDetail rentalDetail = order.getDetails().stream()
                .filter(d -> "RENTAL".equalsIgnoreCase(d.getType()))
                .findFirst()
                .orElse(null);

        LocalDateTime startTime = rentalDetail != null ? rentalDetail.getStartTime() : LocalDateTime.now();
        LocalDateTime endTime = rentalDetail != null ? rentalDetail.getEndTime() : LocalDateTime.now();

        RentalOrderDetail detail = RentalOrderDetail.builder()
                .order(order)
                .vehicle(v)
                .type(type)
                .startTime(startTime)
                .endTime(endTime)
                .price(price)
                .status(status)
                .description(desc)
                .build();

        rentalOrderDetailRepository.save(detail);
    }

    private PaymentResponse buildMoMoPaymentUrl(RentalOrder order, Payment payment, BigDecimal amount) {

        // Validate amount
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Payment amount must be greater than 0");
        }

        try {
            String partnerCode = momoConfig.getPartnerCode();
            String accessKey = momoConfig.getAccessKey();
            String secretKey = momoConfig.getSecretKey();
            String returnUrl = momoConfig.getReturnUrl();
            String notifyUrl = momoConfig.getNotifyUrl();
            String endpoint = momoConfig.getEndpoint();
            String requestType = momoConfig.getRequestType();

            String encoded = payment.getPaymentId().toString().replace("-", "");
            String orderId = encoded + "-" + System.currentTimeMillis();
            String orderInfo = "Order " + order.getOrderId();

            // MoMo amount is in VND (no need to multiply by 100)
            String amountStr = String.valueOf(amount.longValue());
            String extraData = "";

            // Create raw signature THEO THỨ TỰ ALPHABET
            String rawSignature = "accessKey=" + accessKey +
                    "&amount=" + amountStr +
                    "&extraData=" + extraData +
                    "&ipnUrl=" + notifyUrl +
                    "&orderId=" + orderId +
                    "&orderInfo=" + orderInfo +
                    "&partnerCode=" + partnerCode +
                    "&redirectUrl=" + returnUrl +
                    "&requestId=" + orderId +
                    "&requestType=" + requestType;

            log.info("🔐 MoMo Raw Signature: {}", rawSignature);

            String signature = Utils.hmacSHA256(secretKey, rawSignature);
            log.info("🔑 MoMo Signature: {}", signature);

            // Build request using DTO
            MomoCreatePaymentRequest momoRequest = MomoCreatePaymentRequest.builder()
                    .partnerCode(partnerCode)
                    .accessKey(accessKey)
                    .requestId(orderId)
                    .amount(amountStr)
                    .orderId(orderId)
                    .orderInfo(orderInfo)
                    .redirectUrl(returnUrl)
                    .ipnUrl(notifyUrl)
                    .requestType(requestType)
                    .extraData(extraData)
                    .lang("vi")
                    .signature(signature)
                    .build();

            // Serialize to JSON using ObjectMapper
            String requestBody = objectMapper.writeValueAsString(momoRequest);
            log.info("📤 MoMo Request Body: {}", requestBody);

            // Call MoMo API
            URI uri = new URI(endpoint);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

            OutputStream os = conn.getOutputStream();
            os.write(requestBody.getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();

            // Read response
            int responseCode = conn.getResponseCode();
            log.info("📨 MoMo Response Code: {}", responseCode);

            BufferedReader br = new BufferedReader(
                    new InputStreamReader(
                            responseCode == 200 ? conn.getInputStream() : conn.getErrorStream(),
                            StandardCharsets.UTF_8
                    )
            );

            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
            br.close();
            conn.disconnect();

            String responseStr = response.toString();
            log.info("📨 MoMo Response: {}", responseStr);

            // Parse response using DTO
            MomoCreatePaymentResponse momoResponse = objectMapper.readValue(
                    responseStr,
                    MomoCreatePaymentResponse.class
            );

            // Log parsed response
            log.info("📦 Parsed MoMo Response - resultCode: {}, errorCode: {}, message: {}",
                    momoResponse.getResultCode(), momoResponse.getErrorCode(), momoResponse.getMessage());

            // Check if payment URL creation failed
            // resultCode = 0 means success, other values mean error
            Integer resultCode = momoResponse.getResultCode();
            Integer errorCode = momoResponse.getErrorCode();

            // Check error conditions
            if (resultCode != null && resultCode != 0) {
                String errorMsg = momoResponse.getMessage() != null ? momoResponse.getMessage() : "Unknown error";
                throw new BadRequestException("MoMo Error: " + errorMsg + " (ResultCode: " + resultCode + ")");
            }

            if (errorCode != null && errorCode != 0) {
                String errorMsg = momoResponse.getMessage() != null ? momoResponse.getMessage() : "Unknown error";
                throw new BadRequestException("MoMo Error: " + errorMsg + " (ErrorCode: " + errorCode + ")");
            }

            if (momoResponse.getPayUrl() == null || momoResponse.getPayUrl().isEmpty()) {
                throw new BadRequestException("MoMo Error: Payment URL is empty");
            }

            log.info("✅ MoMo payment URL created successfully");

            return PaymentResponse.builder()
                    .paymentId(payment.getPaymentId())
                    .orderId(order.getOrderId())
                    .amount(amount)
                    .remainingAmount(payment.getRemainingAmount())
                    .paymentType(payment.getPaymentType())
                    .method(payment.getMethod())
                    .status(payment.getStatus())
                    .paymentUrl(momoResponse.getPayUrl())
                    .qrCodeUrl(momoResponse.getQrCodeUrl())
                    .deeplink(momoResponse.getDeeplink())
                    .build();

        } catch (Exception e) {
            log.error("❌ Error creating MoMo payment: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create MoMo payment: " + e.getMessage(), e);
        }
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

        RentalOrderDetail rentalDetail = order.getDetails().stream()
                .filter(d -> "RENTAL".equalsIgnoreCase(d.getType()))
                .findFirst()
                .orElse(null);

        LocalDateTime startTime = rentalDetail != null ? rentalDetail.getStartTime() : LocalDateTime.now();
        LocalDateTime endTime = rentalDetail != null ? rentalDetail.getEndTime() : LocalDateTime.now();

        RentalOrderDetail refundDetail = RentalOrderDetail.builder()
                .order(order)
                .vehicle(order.getDetails().getFirst().getVehicle())
                .type("REFUND")
                .startTime(startTime)
                .endTime(endTime)
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

    /**
     * Xóa timeline khi order hoàn thành
     * Timeline chỉ dùng để track xe đang được book, xe đã trả thì không cần nữa
     */
    private void deleteTimelineForOrder(UUID orderId, Long vehicleId) {
        if (vehicleId == null) return;

        List<VehicleTimeline> timelines = vehicleTimelineRepository.findByVehicle_VehicleId(vehicleId);
        List<VehicleTimeline> toDelete = timelines.stream()
                .filter(t -> t.getOrder() != null && t.getOrder().getOrderId().equals(orderId))
                .toList();

        if (!toDelete.isEmpty()) {
            vehicleTimelineRepository.deleteAll(toDelete);
            log.info("🗑️ Deleted {} timeline(s) for completed order {}", toDelete.size(), orderId);
        }
    }

    // ============================================================
    // CASH PAYMENT PROCESSING
    // ============================================================
    @Override
    @Transactional
    public PaymentResponse processCashPayment(PaymentDto dto, UUID userId) {
        log.info("💵 Processing CASH payment for order: {}, type: {}", dto.getOrderId(), dto.getPaymentType());

        // Verify user
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Get order
        RentalOrder order = rentalOrderRepository.findById(dto.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        short type = dto.getPaymentType();
        if (type < 1 || type > 5)
            throw new BadRequestException("Invalid payment type");

        // ============================
        // TYPE 5 - SERVICE PAYMENT
        // ============================
        if (type == 5) {
            return processCashServicePayment(order);
        }

        BigDecimal total = order.getTotalPrice();
        BigDecimal amount;
        BigDecimal remainingAmount;

        // ============================
        // TYPE 1 — DEPOSIT (50%)
        // ============================
        if (type == 1) {
            amount = total.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
            remainingAmount = total.subtract(amount);

            // Create deposit payment (PENDING)
            Payment depositPayment = paymentRepository.save(
                    Payment.builder()
                            .rentalOrder(order)
                            .amount(amount)
                            .remainingAmount(remainingAmount)
                            .method("CASH")
                            .paymentType((short) 1)
                            .status(PaymentStatus.PENDING)
                            .build()
            );

            // Auto create final payment (PENDING)
            Payment finalPayment = paymentRepository.save(
                    Payment.builder()
                            .rentalOrder(order)
                            .amount(remainingAmount)
                            .remainingAmount(BigDecimal.ZERO)
                            .method("CASH")
                            .paymentType((short) 2)
                            .status(PaymentStatus.PENDING)
                            .build()
            );

            log.info("🕒 Created CASH DEPOSIT & auto FINAL payments (both PENDING)");

            recordTransaction(order, depositPayment, "DEPOSIT_PENDING");
            recordTransaction(order, finalPayment, "FINAL_PENDING");

            return PaymentResponse.builder()
                    .paymentId(depositPayment.getPaymentId())
                    .orderId(order.getOrderId())
                    .amount(amount)
                    .remainingAmount(remainingAmount)
                    .method("CASH")
                    .paymentType((short) 1)
                    .status(PaymentStatus.PENDING)
                    .message("CASH_DEPOSIT_PENDING_AND_FINAL_CREATED")
                    .build();
        }

        // ============================
        // TYPE 2 — Final payment (Generated only by system)
        // ============================
        if (type == 2) {
            throw new BadRequestException("Final payment is auto-created. Manual payment type=2 is not allowed.");
        }

        // ============================
        // TYPE 3 — FULL PAYMENT
        // ============================
        if (type == 3) {
            amount = total;
            remainingAmount = BigDecimal.ZERO;

            Payment fullPayment = paymentRepository.save(
                    Payment.builder()
                            .rentalOrder(order)
                            .amount(amount)
                            .remainingAmount(BigDecimal.ZERO)
                            .method("CASH")
                            .paymentType((short) 3)
                            .status(PaymentStatus.PENDING)
                            .build()
            );

            recordTransaction(order, fullPayment, "FULL_PAYMENT_PENDING");

            return PaymentResponse.builder()
                    .paymentId(fullPayment.getPaymentId())
                    .orderId(order.getOrderId())
                    .amount(amount)
                    .remainingAmount(BigDecimal.ZERO)
                    .method("CASH")
                    .paymentType((short) 3)
                    .status(PaymentStatus.PENDING)
                    .message("CASH_FULL_PAYMENT_PENDING")
                    .build();
        }

        throw new BadRequestException("Unsupported cash payment type");
    }


    // ============================================================
    // CASH SERVICE PAYMENT
    // ============================================================
    private PaymentResponse processCashServicePayment(RentalOrder order) {
        log.info(" Processing CASH service payment for order: {}", order.getOrderId());

        // Lấy tất cả service chưa thanh toán
        List<OrderService> pending = orderServiceRepository
                .findByOrder_OrderId(order.getOrderId())
                .stream()
                .filter(s -> !"SUCCESS".equalsIgnoreCase(s.getStatus()))
                .toList();

        if (pending.isEmpty())
            throw new BadRequestException("No unpaid services found");

        // Tổng tiền service
        BigDecimal amount = pending.stream()
                .map(s -> Optional.ofNullable(s.getCost()).orElse(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Tạo payment nhưng để PENDING
        Payment payment = paymentRepository.save(
                Payment.builder()
                        .rentalOrder(order)
                        .amount(amount)
                        .remainingAmount(BigDecimal.ZERO)
                        .paymentType((short) 5)   // SERVICE PAYMENT
                        .method("CASH")
                        .status(PaymentStatus.PENDING)    //  ĐỂ DUYỆT SAU
                        .build()
        );

        log.info("🕒 CASH service payment created PENDING for order: {}", order.getOrderId());

        //  KHÔNG xử lý service success tại đây
        //  KHÔNG update order
        // Việc này staff sẽ xác nhận ở API approve

        recordTransaction(order, payment, "SERVICE_PAYMENT_PENDING");

        return PaymentResponse.builder()
                .paymentId(payment.getPaymentId())
                .orderId(order.getOrderId())
                .amount(amount)
                .remainingAmount(BigDecimal.ZERO)
                .paymentType((short) 5)
                .method("CASH")
                .status(PaymentStatus.PENDING)
                .message("SERVICE_PAYMENT_PENDING_STAFF_CONFIRM")
                .build();
    }

    @Override
    @Transactional
    public void approveCashPaymentByOrder(UUID orderId) {

        RentalOrder order = rentalOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        Payment payment = paymentRepository.findByRentalOrder_OrderId(orderId).stream()
                .filter(p -> "CASH".equalsIgnoreCase(p.getMethod()))
                .filter(p -> p.getStatus() == PaymentStatus.PENDING)
                .findFirst()
                .orElseThrow(() -> new BadRequestException("No pending CASH payment for this order"));

        short type = payment.getPaymentType();

        // UPDATE PAYMENT STATUS
        payment.setStatus(PaymentStatus.SUCCESS);
        paymentRepository.save(payment);

        log.info("💵 Approving CASH payment for orderId={}, paymentType={}", orderId, type);

        switch (type) {
            case 1 -> {
                Vehicle v = getMainVehicle(order);
                depositSuccess(order, payment, v);
            }
            case 2 -> finalSuccess(order, payment);
            case 3 -> {
                Vehicle v = getMainVehicle(order);
                fullSuccess(order, payment, v);
            }
            case 5 -> handleServiceSuccess(order, payment);
            default -> throw new BadRequestException("Unknown payment type");
        }

        rentalOrderRepository.save(order);

        log.info("✅ CASH payment approved successfully for orderId={}", orderId);
    }
}