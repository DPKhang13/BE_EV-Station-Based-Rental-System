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
        if (type < 1 || type > 4)
            throw new BadRequestException("Invalid payment type");

        Vehicle vehicle = getMainVehicle(order);
        BigDecimal total = order.getTotalPrice();

        // Validate payment method - yêu cầu phải có method
        String method = dto.getMethod();
        if (method == null || method.trim().isEmpty()) {
            throw new BadRequestException("Phương thức thanh toán là bắt buộc");
        }

        // Hạn chế các method hợp lệ
        List<String> validMethods = List.of("captureWallet", "payWithMethod", "momo");

        if (!validMethods.contains(method)) {
            throw new BadRequestException("Phương thức thanh toán không hợp lệ: " + method);
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
            // Thanh toán phần còn lại cho DEPOSIT hoặc FULL PAYMENT (dịch vụ phát sinh)
            Optional<Payment> depositPaymentOpt = paymentRepository.findByRentalOrder_OrderId(order.getOrderId())
                    .stream()
                    .filter(p -> p.getPaymentType() == 1 && p.getStatus() == PaymentStatus.SUCCESS)
                    .findFirst();

            Optional<Payment> fullPaymentOpt = paymentRepository.findByRentalOrder_OrderId(order.getOrderId())
                    .stream()
                    .filter(p -> p.getPaymentType() == 3 && p.getStatus() == PaymentStatus.SUCCESS)
                    .findFirst();

            if (depositPaymentOpt.isPresent()) {
                Payment depositPayment = depositPaymentOpt.get();

                // Lấy số tiền còn lại từ deposit payment - ĐÂY LÀ SỐ TIỀN CẦN THANH TOÁN
                BigDecimal depositRemaining = depositPayment.getRemainingAmount();
                if (depositRemaining == null || depositRemaining.compareTo(BigDecimal.ZERO) <= 0) {
                    // Tính lại số tiền còn lại = tổng - số đã đặt cọc
                    amount = total.subtract(depositPayment.getAmount());
                    log.info("💰 Final payment: calculated remaining = total({}) - deposit({}) = {}", 
                            total, depositPayment.getAmount(), amount);
                } else {
                    amount = depositRemaining;
                    log.info("💰 Final payment: using remainingAmount from deposit = {}", amount);
                }
            } else if (fullPaymentOpt.isPresent()) {
                Payment fullPayment = fullPaymentOpt.get();
                BigDecimal outstanding = fullPayment.getRemainingAmount();

                if (outstanding == null || outstanding.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BadRequestException("Không có khoản nào cần thanh toán (full payment)");
                }

                amount = outstanding;
                log.info("💰 Final payment (service): using remainingAmount from FULL_PAYMENT = {}", amount);
            } else {
                throw new BadRequestException("Must pay deposit first or have outstanding full payment");
            }

            remainingAmount = BigDecimal.ZERO;
            
            // Tìm payment type 2 đã tồn tại (nếu có) hoặc tạo mới
            Payment existingFinalPayment = paymentRepository.findByRentalOrder_OrderId(order.getOrderId())
                    .stream()
                    .filter(p -> p.getPaymentType() == 2 && p.getStatus() == PaymentStatus.PENDING)
                    .findFirst()
                    .orElse(null);
            
            if (existingFinalPayment != null) {
                // Cập nhật amount của payment đã tồn tại
                existingFinalPayment.setAmount(amount);
                existingFinalPayment.setRemainingAmount(BigDecimal.ZERO);
                existingFinalPayment.setMethod(method);
                Payment payment = paymentRepository.save(existingFinalPayment);
                
                log.info("✅ Using existing final payment {} with amount={}", payment.getPaymentId(), amount);
                updateOrderStatus(order, type);
                return buildMoMoPaymentUrl(order, payment, amount);
            }
            // Nếu chưa có payment type 2, tiếp tục tạo mới ở dưới
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
        // Đảm bảo amount đúng - đặc biệt cho type == 2
        if (type == 2) {
            log.info("🔍 DEBUG type==2: amount={}, total={}, remainingAmount={}", amount, total, remainingAmount);
            // Double check: nếu amount == total, có thể đã bị sai
            if (amount.compareTo(total) == 0) {
                log.error("❌ ERROR: amount == total for type 2! This should not happen!");
                // Tìm lại deposit payment và tính lại
                Payment depositPayment = paymentRepository.findByRentalOrder_OrderId(order.getOrderId())
                        .stream()
                        .filter(p -> p.getPaymentType() == 1 && p.getStatus() == PaymentStatus.SUCCESS)
                        .findFirst()
                        .orElse(null);
                if (depositPayment != null) {
                    BigDecimal correctAmount = depositPayment.getRemainingAmount();
                    if (correctAmount == null || correctAmount.compareTo(BigDecimal.ZERO) <= 0) {
                        correctAmount = total.subtract(depositPayment.getAmount());
                    }
                    amount = correctAmount;
                    log.info("🔧 FIXED: corrected amount from {} to {}", total, amount);
                }
            }
        }
        
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

        log.info("✅ Created new payment {} with amount={}, remaining={}, type={}, total={}",
                payment.getPaymentId(), payment.getAmount(), payment.getRemainingAmount(), type, total);

        updateOrderStatus(order, type);

        // TYPE != 2 -> create DEPOSIT or FULL_PAYMENT detail with PENDING status
        if (type != 2) {
            createOrUpdateDetail(order, vehicle, getTypeName(type), amount, getDescription(type), "PENDING");
        }

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
    // RENTAL PAYMENT SUCCESS
    // ============================================================

    // TYPE 1 — Deposit Success
    private void depositSuccess(RentalOrder order, Payment payment, Vehicle v) {

        order.setStatus("DEPOSITED");

        // amount và remainingAmount đã được set khi tạo payment
        BigDecimal deposit = payment.getAmount();

        // Create deposit detail
        createOrUpdateDetail(order, v, "DEPOSIT", deposit, "Thanh toán đặt cọc", "SUCCESS");

        // Không tự động tạo PICKUP detail - chỉ tạo khi thanh toán phần còn lại
    }

    // TYPE 2 — Final Payment Success
    private void finalSuccess(RentalOrder order, Payment payment) {
        order.setStatus("PAID");
        payment.setRemainingAmount(BigDecimal.ZERO);

        // Ưu tiên xử lý phần còn lại của DEPOSIT (type 1)
        Optional<Payment> depositPaymentOpt = paymentRepository.findByRentalOrder_OrderId(order.getOrderId())
                .stream()
                .filter(p -> p.getPaymentType() == 1 && p.getStatus() == PaymentStatus.SUCCESS)
                .findFirst();

        if (depositPaymentOpt.isPresent()) {
            Payment depositPayment = depositPaymentOpt.get();
            BigDecimal remainingAmount = depositPayment.getRemainingAmount();

            if (remainingAmount != null && remainingAmount.compareTo(BigDecimal.ZERO) > 0) {
                // Lấy số tiền còn lại - nếu null hoặc 0, dùng amount của payment hiện tại (đã thanh toán)
                log.info("💰 Final payment: using remainingAmount {} from deposit for PICKUP detail", remainingAmount);

                depositPayment.setRemainingAmount(BigDecimal.ZERO);
                paymentRepository.save(depositPayment);

                // Tạo PICKUP detail với status SUCCESS khi thanh toán phần còn lại
                Vehicle vehicle = getMainVehicle(order);
                if (vehicle != null) {
                    createOrUpdateDetail(order, vehicle, "PICKUP", remainingAmount, "Thanh toán phần còn lại", "SUCCESS");
                    log.info("✅ Created PICKUP detail with amount={}", remainingAmount);
                } else {
                    log.warn("⚠️ Cannot create PICKUP detail: vehicle is null");
                }
                return;
            }
        }

        // Nếu không còn deposit outstanding, xử lý remainingAmount của FULL_PAYMENT (dịch vụ phát sinh)
        Payment fullPayment = paymentRepository.findByRentalOrder_OrderId(order.getOrderId())
                .stream()
                .filter(p -> p.getPaymentType() == 3 && p.getStatus() == PaymentStatus.SUCCESS)
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Không tìm thấy thanh toán full để cập nhật"));

        BigDecimal outstanding = Optional.ofNullable(fullPayment.getRemainingAmount()).orElse(BigDecimal.ZERO);
        if (outstanding.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("⚠️ Final payment success nhưng không có remainingAmount nào trên FULL_PAYMENT");
            return;
        }

        BigDecimal newRemaining = outstanding.subtract(payment.getAmount());
        if (newRemaining.compareTo(BigDecimal.ZERO) < 0) {
            log.warn("⚠️ Thanh toán vượt quá remainingAmount. outstanding={}, payment={}", outstanding, payment.getAmount());
            newRemaining = BigDecimal.ZERO;
        }

        fullPayment.setRemainingAmount(newRemaining);
        paymentRepository.save(fullPayment);
        log.info("✅ Updated FULL_PAYMENT remainingAmount: {} -> {}", outstanding, newRemaining);

        if (newRemaining.compareTo(BigDecimal.ZERO) == 0) {
            markServiceDetailsAsSuccess(order);
        }
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

    private void markServiceDetailsAsSuccess(RentalOrder order) {
        List<RentalOrderDetail> serviceDetails = Optional.ofNullable(order.getDetails())
                .orElse(List.of()).stream()
                .filter(d -> "SERVICE".equalsIgnoreCase(d.getType()))
                .filter(d -> !"SUCCESS".equalsIgnoreCase(d.getStatus()))
                .toList();

        if (serviceDetails.isEmpty()) return;

        serviceDetails.forEach(d -> d.setStatus("SUCCESS"));
        rentalOrderDetailRepository.saveAll(serviceDetails);
        log.info("✅ Updated {} service detail(s) to SUCCESS for order {}", serviceDetails.size(), order.getOrderId());
    }

    private PaymentResponse buildMoMoPaymentUrl(RentalOrder order, Payment payment, BigDecimal amount) {

        // Validate amount
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Payment amount must be greater than 0");
        }

        log.info("🔗 Building MoMo URL: paymentId={}, amount={}, payment.getAmount()={}", 
                payment.getPaymentId(), amount, payment.getAmount());

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
            default -> "UNKNOWN";
        };
    }

    private String getDescription(short type) {
        return switch (type) {
            case 1 -> "Thanh toán đặt cọc";
            case 2 -> "Thanh toán phần còn lại";
            case 3 -> "Thanh toán toàn bộ đơn thuê";
            case 4 -> "Hoàn tiền";
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
        if (type < 1 || type > 4)
            throw new BadRequestException("Invalid payment type");

        BigDecimal total = order.getTotalPrice();
        BigDecimal amount;
        BigDecimal remainingAmount;

        if (type == 1) {
            amount = total.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
            remainingAmount = total.subtract(amount);
        } else if (type == 2) {
            Optional<Payment> depositPaymentOpt = paymentRepository.findByRentalOrder_OrderId(order.getOrderId())
                    .stream()
                    .filter(p -> p.getPaymentType() == 1 && p.getStatus() == PaymentStatus.SUCCESS)
                    .findFirst();

            Optional<Payment> fullPaymentOpt = paymentRepository.findByRentalOrder_OrderId(order.getOrderId())
                    .stream()
                    .filter(p -> p.getPaymentType() == 3 && p.getStatus() == PaymentStatus.SUCCESS)
                    .findFirst();

            if (depositPaymentOpt.isPresent()) {
                Payment depositPayment = depositPaymentOpt.get();

                BigDecimal depositRemaining = depositPayment.getRemainingAmount();
                if (depositRemaining == null || depositRemaining.compareTo(BigDecimal.ZERO) <= 0) {
                    amount = total.subtract(depositPayment.getAmount());
                    log.info("💵 [cash/type2] calculated remaining = total({}) - deposit({}) = {}",
                            total, depositPayment.getAmount(), amount);
                } else {
                    amount = depositRemaining;
                    log.info("💵 [cash/type2] using remainingAmount from deposit = {}", amount);
                }
            } else if (fullPaymentOpt.isPresent()) {
                Payment fullPayment = fullPaymentOpt.get();
                BigDecimal outstanding = fullPayment.getRemainingAmount();

                if (outstanding == null || outstanding.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BadRequestException("Không có khoản nào cần thanh toán (full payment)");
                }

                amount = outstanding;
                log.info("💵 [cash/type2] using remainingAmount from FULL_PAYMENT = {}", amount);
            } else {
                throw new BadRequestException("Must pay deposit first or have outstanding full payment");
            }

            remainingAmount = BigDecimal.ZERO;
        } else if (type == 3) {
            amount = total;
            remainingAmount = BigDecimal.ZERO;
        } else {
            throw new BadRequestException("Unsupported cash payment type");
        }

        Payment payment;
        if (type == 2) {
            Payment existingFinalPayment = paymentRepository.findByRentalOrder_OrderId(order.getOrderId())
                    .stream()
                    .filter(p -> p.getPaymentType() == 2 && p.getStatus() == PaymentStatus.PENDING)
                    .findFirst()
                    .orElse(null);

            if (existingFinalPayment != null) {
                existingFinalPayment.setAmount(amount);
                existingFinalPayment.setRemainingAmount(BigDecimal.ZERO);
                existingFinalPayment.setMethod("CASH");
                payment = paymentRepository.save(existingFinalPayment);
            } else {
                payment = paymentRepository.save(
                        Payment.builder()
                                .rentalOrder(order)
                                .amount(amount)
                                .remainingAmount(remainingAmount)
                                .method("CASH")
                                .paymentType(type)
                                .status(PaymentStatus.PENDING)
                                .build()
                );
            }
        } else {
            payment = paymentRepository.save(
                    Payment.builder()
                            .rentalOrder(order)
                            .amount(amount)
                            .remainingAmount(remainingAmount)
                            .method("CASH")
                            .paymentType(type)
                            .status(PaymentStatus.PENDING)
                            .build()
            );
        }

        recordTransaction(order, payment, getTypeName(type) + "_PENDING");

        return PaymentResponse.builder()
                .paymentId(payment.getPaymentId())
                .orderId(order.getOrderId())
                .amount(payment.getAmount())
                .remainingAmount(payment.getRemainingAmount())
                .method(payment.getMethod())
                .paymentType(payment.getPaymentType())
                .status(payment.getStatus())
                .message("CASH_PAYMENT_CREATED")
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
            default -> throw new BadRequestException("Unknown payment type");
        }

        rentalOrderRepository.save(order);

        log.info("✅ CASH payment approved successfully for orderId={}", orderId);
    }

    @Override
    public List<PaymentResponse> getPaymentsByOrderId(UUID orderId) {
        log.info("📋 Getting payments for order: {}", orderId);
        
        // Verify order exists
        RentalOrder order = rentalOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        // Get all payments for this order
        List<Payment> payments = paymentRepository.findByRentalOrder_OrderId(orderId);
        
        // Convert to PaymentResponse list
        return payments.stream()
                .map(payment -> PaymentResponse.builder()
                        .paymentId(payment.getPaymentId())
                        .orderId(order.getOrderId())
                        .amount(payment.getAmount())
                        .remainingAmount(payment.getRemainingAmount())
                        .paymentType(payment.getPaymentType())
                        .method(payment.getMethod())
                        .status(payment.getStatus())
                        .build())
                .toList();
    }
}