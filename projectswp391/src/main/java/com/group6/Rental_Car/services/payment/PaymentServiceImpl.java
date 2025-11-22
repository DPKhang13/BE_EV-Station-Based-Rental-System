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
        createOrUpdateDetail(order, v, "DEPOSIT", deposit, "Đặt cọc giữ xe", "SUCCESS");

        // Không tự động tạo PICKUP detail - chỉ tạo khi thanh toán phần còn lại
    }

    // TYPE 2 — Final Payment Success (thanh toán dịch vụ/phần còn lại)
    private void finalSuccess(RentalOrder order, Payment payment) {
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
                BigDecimal amountToPay = payment.getAmount();
                BigDecimal currentRemaining = remainingAmount;
                
                log.info("💰 [finalSuccess] Deposit remainingAmount: {}, payment amount: {}", currentRemaining, amountToPay);

                // Trừ amount đã thanh toán khỏi remainingAmount
                BigDecimal newRemaining = currentRemaining.subtract(amountToPay);
                if (newRemaining.compareTo(BigDecimal.ZERO) < 0) {
                    log.warn("⚠️ Thanh toán vượt quá remainingAmount. remaining={}, payment={}", currentRemaining, amountToPay);
                    newRemaining = BigDecimal.ZERO;
                }

                depositPayment.setRemainingAmount(newRemaining);
                paymentRepository.save(depositPayment);
                log.info("✅ [finalSuccess] Updated deposit remainingAmount: {} -> {}", currentRemaining, newRemaining);

                // Tạo PICKUP detail với status SUCCESS khi thanh toán phần còn lại
                Vehicle vehicle = getMainVehicle(order);
                if (vehicle != null) {
                    createOrUpdateDetail(order, vehicle, "PICKUP", amountToPay, "Thanh toán thuê xe", "SUCCESS");
                    log.info("✅ Created PICKUP detail with amount={}", amountToPay);
                } else {
                    log.warn("⚠️ Cannot create PICKUP detail: vehicle is null");
                }

                // Mark service details as SUCCESS nếu đã thanh toán hết
                if (newRemaining.compareTo(BigDecimal.ZERO) == 0) {
                    markServiceDetailsAsSuccess(order);
                    // Đã thanh toán hết → kiểm tra xem đã trả xe chưa
                    // Reload order để có status mới nhất
                    order = rentalOrderRepository.findById(order.getOrderId())
                            .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
                    Vehicle reloadedVehicle = getMainVehicle(order);
                    String currentStatus = order.getStatus();
                    String vehicleStatus = reloadedVehicle != null ? reloadedVehicle.getStatus() : null;
                    // Chỉ set COMPLETED khi vehicle status = CHECKING (đã trả xe) hoặc order status = PENDING_FINAL_PAYMENT/RETURNED (đã confirm return)
                    // KHÔNG kiểm tra "PAID" vì có thể đã PAID từ trước nhưng chưa trả xe
                    boolean isReturned = currentStatus.equals("PENDING_FINAL_PAYMENT") || 
                                        currentStatus.equals("RETURNED") ||
                                        "CHECKING".equalsIgnoreCase(vehicleStatus);
                    
                    if (isReturned) {
                        // Đã trả xe và thanh toán hết → COMPLETED
                        order.setStatus("COMPLETED");
                        log.info("✅ [finalSuccess] Đã trả xe và thanh toán hết → COMPLETED");
                    } else {
                        // Chưa trả xe nhưng đã thanh toán hết → PAID
                        order.setStatus("PAID");
                        log.info("✅ [finalSuccess] Chưa trả xe nhưng đã thanh toán hết → PAID");
                    }
                } else {
                    // Còn số tiền chưa thanh toán → chuyển thành PENDING_FINAL_PAYMENT
                    order.setStatus("PENDING_FINAL_PAYMENT");
                    log.info("ℹ️ [finalSuccess] Còn {} chưa thanh toán, order status: PENDING_FINAL_PAYMENT", newRemaining);
                }
                return;
            }
        }

        // Nếu không còn deposit, xử lý remainingAmount của FULL_PAYMENT (dịch vụ phát sinh)
        Optional<Payment> fullPaymentOpt = paymentRepository.findByRentalOrder_OrderId(order.getOrderId())
                .stream()
                .filter(p -> p.getPaymentType() == 3 && p.getStatus() == PaymentStatus.SUCCESS)
                .findFirst();

        if (fullPaymentOpt.isPresent()) {
            Payment fullPayment = fullPaymentOpt.get();
            BigDecimal outstanding = Optional.ofNullable(fullPayment.getRemainingAmount()).orElse(BigDecimal.ZERO);
            
            if (outstanding.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("⚠️ Final payment success nhưng không có remainingAmount nào trên FULL_PAYMENT");
                // Không có remainingAmount → đã thanh toán hết
                // Reload order để có status mới nhất
                order = rentalOrderRepository.findById(order.getOrderId())
                        .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
                Vehicle vehicle = getMainVehicle(order);
                String currentStatus = order.getStatus();
                String vehicleStatus = vehicle != null ? vehicle.getStatus() : null;
                // Chỉ set COMPLETED khi vehicle status = CHECKING (đã trả xe) hoặc order status = PENDING_FINAL_PAYMENT/RETURNED (đã confirm return)
                // KHÔNG kiểm tra "PAID" vì có thể đã PAID từ trước nhưng chưa trả xe
                boolean isReturned = currentStatus.equals("PENDING_FINAL_PAYMENT") || 
                                    currentStatus.equals("RETURNED") ||
                                    "CHECKING".equalsIgnoreCase(vehicleStatus);
                
                if (isReturned) {
                    // Đã trả xe và thanh toán hết → COMPLETED
                    order.setStatus("COMPLETED");
                    log.info("✅ [finalSuccess] Đã trả xe và thanh toán hết → COMPLETED");
                } else {
                    // Chưa trả xe nhưng đã thanh toán hết → PAID
                    order.setStatus("PAID");
                    log.info("✅ [finalSuccess] Chưa trả xe nhưng đã thanh toán hết → PAID");
                }
                return;
            }

            BigDecimal amountToPay = payment.getAmount();
            BigDecimal newRemaining = outstanding.subtract(amountToPay);
            if (newRemaining.compareTo(BigDecimal.ZERO) < 0) {
                log.warn("⚠️ Thanh toán vượt quá remainingAmount. outstanding={}, payment={}", outstanding, amountToPay);
                newRemaining = BigDecimal.ZERO;
            }

            fullPayment.setRemainingAmount(newRemaining);
            paymentRepository.save(fullPayment);
            log.info("✅ [finalSuccess] Updated FULL_PAYMENT remainingAmount: {} -> {}", outstanding, newRemaining);

            if (newRemaining.compareTo(BigDecimal.ZERO) == 0) {
                markServiceDetailsAsSuccess(order);
                // Đã thanh toán hết → kiểm tra xem đã trả xe chưa
                // Reload order để có status mới nhất
                order = rentalOrderRepository.findById(order.getOrderId())
                        .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
                Vehicle vehicle = getMainVehicle(order);
                String currentStatus = order.getStatus();
                String vehicleStatus = vehicle != null ? vehicle.getStatus() : null;
                // Chỉ set COMPLETED khi vehicle status = CHECKING (đã trả xe) hoặc order status = PENDING_FINAL_PAYMENT/RETURNED (đã confirm return)
                // KHÔNG kiểm tra "PAID" vì có thể đã PAID từ trước nhưng chưa trả xe
                boolean isReturned = currentStatus.equals("PENDING_FINAL_PAYMENT") || 
                                    currentStatus.equals("RETURNED") ||
                                    "CHECKING".equalsIgnoreCase(vehicleStatus);
                
                if (isReturned) {
                    // Đã trả xe và thanh toán hết → COMPLETED
                    order.setStatus("COMPLETED");
                    log.info("✅ [finalSuccess] Đã trả xe và thanh toán hết → COMPLETED");
                } else {
                    // Chưa trả xe nhưng đã thanh toán hết → PAID
                    order.setStatus("PAID");
                    log.info("✅ [finalSuccess] Chưa trả xe nhưng đã thanh toán hết → PAID");
                }
            } else {
                // Còn số tiền chưa thanh toán → chuyển thành PENDING_FINAL_PAYMENT
                order.setStatus("PENDING_FINAL_PAYMENT");
                log.info("ℹ️ [finalSuccess] Còn {} chưa thanh toán, order status: PENDING_FINAL_PAYMENT", newRemaining);
            }
        } else {
            // Không tìm thấy DEPOSIT hoặc FULL_PAYMENT
            log.warn("⚠️ [finalSuccess] Không tìm thấy DEPOSIT hoặc FULL_PAYMENT SUCCESS");
            // Không có payment nào → có thể đã thanh toán hết
            // Reload order để có status mới nhất
            order = rentalOrderRepository.findById(order.getOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
            Vehicle vehicle = getMainVehicle(order);
            String currentStatus = order.getStatus();
            String vehicleStatus = vehicle != null ? vehicle.getStatus() : null;
            // Chỉ set COMPLETED khi vehicle status = CHECKING (đã trả xe) hoặc order status = PENDING_FINAL_PAYMENT/RETURNED (đã confirm return)
            // KHÔNG kiểm tra "PAID" vì có thể đã PAID từ trước nhưng chưa trả xe
            boolean isReturned = currentStatus.equals("PENDING_FINAL_PAYMENT") || 
                                currentStatus.equals("RETURNED") ||
                                "CHECKING".equalsIgnoreCase(vehicleStatus);
            
            if (isReturned) {
                // Đã trả xe và thanh toán hết → COMPLETED
                order.setStatus("COMPLETED");
                log.info("✅ [finalSuccess] Đã trả xe và thanh toán hết → COMPLETED");
            } else {
                // Chưa trả xe nhưng đã thanh toán hết → PAID
                order.setStatus("PAID");
                log.info("✅ [finalSuccess] Chưa trả xe nhưng đã thanh toán hết → PAID");
            }
        }
    }

    // TYPE 3 — Full Payment Success
    private void fullSuccess(RentalOrder order, Payment payment, Vehicle v) {
        BigDecimal fullAmount = payment.getAmount();
        // Set remainingAmount = 0 vì đã thanh toán toàn bộ
        payment.setRemainingAmount(BigDecimal.ZERO);

        createOrUpdateDetail(order, v, "FULL_PAYMENT", fullAmount, "Thanh toán toàn bộ đơn", "SUCCESS");
        
        // Kiểm tra xem order đã được confirm return chưa (vehicle status = CHECKING hoặc order status = PENDING_FINAL_PAYMENT/RETURNED)
        String currentStatus = order.getStatus();
        String vehicleStatus = v.getStatus();
        boolean isReturned = currentStatus.equals("PENDING_FINAL_PAYMENT") || 
                            currentStatus.equals("RETURNED") ||
                            currentStatus.equals("PAID") ||
                            "CHECKING".equalsIgnoreCase(vehicleStatus);
        
        if (isReturned) {
            // Đã trả xe và thanh toán hết → COMPLETED
            order.setStatus("COMPLETED");
            log.info("✅ [fullSuccess] Đã trả xe và thanh toán toàn bộ → COMPLETED");
        } else {
            // Chưa trả xe nhưng đã thanh toán toàn bộ → PAID
            order.setStatus("PAID");
            log.info("✅ [fullSuccess] Chưa trả xe nhưng đã thanh toán toàn bộ → PAID");
        }
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
            case 1 -> "Đặt cọc giữ xe";
            case 2 -> "Thanh toán thuê xe";
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
            // Thanh toán phần còn lại cho DEPOSIT hoặc FULL PAYMENT (dịch vụ phát sinh)
            // Logic giống hệt MoMo
            Optional<Payment> depositPaymentOpt = paymentRepository.findByRentalOrder_OrderId(order.getOrderId())
                    .stream()
                    .filter(p -> p.getPaymentType() == 1 && p.getStatus() == PaymentStatus.SUCCESS)
                    .findFirst();

            Optional<Payment> fullPaymentOpt = paymentRepository.findByRentalOrder_OrderId(order.getOrderId())
                    .stream()
                    .filter(p -> p.getPaymentType() == 3 && p.getStatus() == PaymentStatus.SUCCESS)
                    .findFirst();

            log.info("💵 [cash/type2] Looking for deposit/full payment. Deposit found: {}, Full found: {}", 
                    depositPaymentOpt.isPresent(), fullPaymentOpt.isPresent());

            if (depositPaymentOpt.isPresent()) {
                Payment depositPayment = depositPaymentOpt.get();

                // Lấy số tiền còn lại từ deposit payment - Logic giống hệt createPaymentUrl
                BigDecimal depositRemaining = depositPayment.getRemainingAmount();
                log.info("💵 [cash/type2] Deposit payment: amount={}, remainingAmount={}, total={}", 
                        depositPayment.getAmount(), depositRemaining, total);
                
                if (depositRemaining == null || depositRemaining.compareTo(BigDecimal.ZERO) <= 0) {
                    // Tính lại số tiền còn lại = tổng - số đã đặt cọc
                    amount = total.subtract(depositPayment.getAmount());
                    log.info("💵 [cash/type2] calculated remaining = total({}) - deposit({}) = {}", 
                            total, depositPayment.getAmount(), amount);
                } else {
                    // Dựa vào remainingAmount của DEPOSIT (đã bao gồm phần còn lại + SERVICE)
                    amount = depositRemaining;
                    log.info("💵 [cash/type2] using remainingAmount from deposit = {}", amount);
                }
            } else if (fullPaymentOpt.isPresent()) {
                Payment fullPayment = fullPaymentOpt.get();
                BigDecimal outstanding = fullPayment.getRemainingAmount();

                log.info("💵 [cash/type2] Full payment: amount={}, remainingAmount={}", 
                        fullPayment.getAmount(), outstanding);

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
            // Full payment - tự động chuyển sang type 2 nếu đã có deposit SUCCESS
            Optional<Payment> existingDeposit = paymentRepository.findByRentalOrder_OrderId(order.getOrderId())
                    .stream()
                    .filter(p -> p.getPaymentType() == 1 && p.getStatus() == PaymentStatus.SUCCESS)
                    .findFirst();
            
            if (existingDeposit.isPresent()) {
                // Đã có deposit SUCCESS → tự động chuyển sang type 2 (thanh toán phần còn lại)
                log.info("💵 [cash/type3] Đã có deposit SUCCESS, tự động chuyển sang type 2");
                Payment depositPayment = existingDeposit.get();
                
                BigDecimal depositRemaining = depositPayment.getRemainingAmount();
                log.info("💵 [cash/type3→type2] Deposit payment: amount={}, remainingAmount={}, total={}", 
                        depositPayment.getAmount(), depositRemaining, total);
                
                if (depositRemaining == null || depositRemaining.compareTo(BigDecimal.ZERO) <= 0) {
                    // Tính lại số tiền còn lại = tổng - số đã đặt cọc
                    amount = total.subtract(depositPayment.getAmount());
                    log.info("💵 [cash/type3→type2] calculated remaining = total({}) - deposit({}) = {}", 
                            total, depositPayment.getAmount(), amount);
                } else {
                    amount = depositRemaining;
                    log.info("💵 [cash/type3→type2] using remainingAmount from deposit = {}", amount);
                }
                
                // Đổi type từ 3 → 2
                type = 2;
                remainingAmount = BigDecimal.ZERO;
            } else {
                // Chưa có deposit → thanh toán toàn bộ (type 3)
                amount = total;
                remainingAmount = BigDecimal.ZERO;
            }
        } else {
            throw new BadRequestException("Unsupported cash payment type");
        }

        // ============================
        // DOUBLE CHECK cho type 2 (giống logic MoMo)
        // ============================
        if (type == 2) {
            log.info("🔍 [cash/type2] DEBUG: amount={}, total={}, remainingAmount={}", amount, total, remainingAmount);
            // Double check: nếu amount == total, có thể đã bị sai
            if (amount.compareTo(total) == 0) {
                log.error("❌ [cash/type2] ERROR: amount == total for type 2! This should not happen!");
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
                    log.info("🔧 [cash/type2] FIXED: corrected amount from {} to {}", total, amount);
                }
            }
        }

        // ============================
        // TẠO PAYMENT (giống logic MoMo)
        // ============================
        log.info("💵 [cash] Before creating payment: type={}, amount={}, remainingAmount={}, total={}", 
                type, amount, remainingAmount, total);
        
         Payment payment;
         try {
             if (type == 2) {
                 // Tìm payment type 2 CASH đã tồn tại (chỉ tìm CASH, không tìm MoMo)
                 Payment existingFinalPayment = paymentRepository.findByRentalOrder_OrderId(order.getOrderId())
                         .stream()
                         .filter(p -> p.getPaymentType() == 2 && p.getStatus() == PaymentStatus.PENDING)
                         .filter(p -> "CASH".equalsIgnoreCase(p.getMethod()))
                         .findFirst()
                         .orElse(null);

                 if (existingFinalPayment != null) {
                     // Cập nhật amount của payment CASH đã tồn tại
                     existingFinalPayment.setAmount(amount);
                     existingFinalPayment.setRemainingAmount(BigDecimal.ZERO);
                     existingFinalPayment.setMethod("CASH");
                     payment = paymentRepository.save(existingFinalPayment);
                     log.info("✅ [cash/type2] Using existing CASH final payment {} with amount={}", payment.getPaymentId(), amount);
                 } else {
                     // Tạo payment CASH mới (không update payment MoMo)
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
                     log.info("✅ [cash/type2] Created new CASH payment {} with amount={}, remaining={}, type={}, total={}",
                             payment.getPaymentId(), payment.getAmount(), payment.getRemainingAmount(), type, total);
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
                log.info("✅ [cash] Created new payment {} with amount={}, remaining={}, type={}, total={}",
                        payment.getPaymentId(), payment.getAmount(), payment.getRemainingAmount(), type, total);
            }
            
            log.info("✅ [cash] Payment created successfully: paymentId={}, type={}, status={}", 
                    payment.getPaymentId(), payment.getPaymentType(), payment.getStatus());
        } catch (Exception e) {
            log.error("❌ [cash] Error creating payment: {}", e.getMessage(), e);
            throw new BadRequestException("Lỗi khi tạo payment: " + e.getMessage());
        }

        // Cập nhật order status (giống logic MoMo)
        try {
            updateOrderStatus(order, type);
            log.info("✅ [cash] Updated order status to {}", order.getStatus());
        } catch (Exception e) {
            log.warn("⚠️ [cash] Error updating order status (non-critical): {}", e.getMessage());
        }

        try {
            recordTransaction(order, payment, getTypeName(type) + "_PENDING");
            log.info("✅ [cash] Transaction recorded successfully");
        } catch (Exception e) {
            log.warn("⚠️ [cash] Error recording transaction (non-critical): {}", e.getMessage());
        }

        // Tạo / cập nhật detail PENDING cho thanh toán CASH
        // để FE thấy phương thức CASH trong phần chi tiết đơn hàng
        // NHƯNG: Nếu type = 2 và đã có FULL_PAYMENT SUCCESS, không tạo PICKUP detail (sẽ được tạo trong finalSuccess khi payment SUCCESS)
        try {
            // Kiểm tra nếu type = 2 và đã có FULL_PAYMENT SUCCESS, thì không tạo PICKUP detail
            if (type == 2) {
                boolean hasFullPaymentSuccess = paymentRepository.findByRentalOrder_OrderId(order.getOrderId())
                        .stream()
                        .anyMatch(p -> p.getPaymentType() == 3 && p.getStatus() == PaymentStatus.SUCCESS);
                
                if (hasFullPaymentSuccess) {
                    log.info("ℹ️ [cash/type2] Đã có FULL_PAYMENT SUCCESS, không tạo PICKUP detail (sẽ được xử lý khi payment SUCCESS)");
                    // Không tạo detail, vì sẽ được xử lý trong finalSuccess khi payment chuyển sang SUCCESS
                } else {
                    // Chưa có FULL_PAYMENT SUCCESS, tạo PICKUP detail như bình thường
                    Vehicle v = getMainVehicle(order);
                    String detailType = getTypeName(type);   // PICKUP
                    String desc = getDescription(type);
                    createOrUpdateDetail(order, v, detailType, amount, desc, "PENDING");
                    log.info("✅ [cash/type2] Created/updated {} detail with PENDING status for order {}", detailType, order.getOrderId());
                }
            } else {
                // Type 1 (DEPOSIT) hoặc type 3 (FULL_PAYMENT), tạo detail như bình thường
                Vehicle v = getMainVehicle(order);
                String detailType = getTypeName(type);   // DEPOSIT | FULL_PAYMENT
                String desc = getDescription(type);
                createOrUpdateDetail(order, v, detailType, amount, desc, "PENDING");
                log.info("✅ [cash] Created/updated {} detail with PENDING status for order {}", detailType, order.getOrderId());
            }
        } catch (Exception e) {
            log.warn("⚠️ [cash] Error creating pending detail for CASH payment (non-critical): {}", e.getMessage());
        }

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
        log.info("💵 [approveCash] Starting approval for orderId={}", orderId);

        RentalOrder order = rentalOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        // Lấy tất cả payment CASH PENDING để debug
        List<Payment> allCashPending = paymentRepository.findByRentalOrder_OrderId(orderId).stream()
                .filter(p -> "CASH".equalsIgnoreCase(p.getMethod()))
                .filter(p -> p.getStatus() == PaymentStatus.PENDING)
                .toList();
        
        log.info("💵 [approveCash] Found {} CASH PENDING payment(s) for order {}", allCashPending.size(), orderId);
        allCashPending.forEach(p -> log.info("💵 [approveCash] Payment: id={}, type={}, amount={}, status={}", 
                p.getPaymentId(), p.getPaymentType(), p.getAmount(), p.getStatus()));

        Payment payment = allCashPending.stream()
                .findFirst()
                .orElseThrow(() -> {
                    log.error("❌ [approveCash] No pending CASH payment found for order {}", orderId);
                    return new BadRequestException("No pending CASH payment for this order");
                });

        short type = payment.getPaymentType();
        log.info("💵 [approveCash] Approving payment: id={}, type={}, amount={}", 
                payment.getPaymentId(), type, payment.getAmount());

        // UPDATE PAYMENT STATUS
        payment.setStatus(PaymentStatus.SUCCESS);
        payment = paymentRepository.save(payment);
        log.info("💵 [approveCash] Payment status updated to SUCCESS: id={}", payment.getPaymentId());

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
        
        // Reload order để có dữ liệu mới nhất
        order = rentalOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        
        // Kiểm tra xem đã thanh toán hết chưa và order đã trả xe chưa
        // Nếu đã trả xe (PENDING_FINAL_PAYMENT hoặc RETURNED hoặc vehicle = CHECKING) và đã thanh toán hết, chuyển thành COMPLETED
        String currentStatus = order.getStatus();
        Vehicle vehicle = getMainVehicle(order);
        String vehicleStatus = vehicle != null ? vehicle.getStatus() : null;
        // Chỉ set COMPLETED khi vehicle status = CHECKING (đã trả xe) hoặc order status = PENDING_FINAL_PAYMENT/RETURNED (đã confirm return)
        // KHÔNG kiểm tra "PAID" vì có thể đã PAID từ trước nhưng chưa trả xe
        boolean isReturned = currentStatus.equals("PENDING_FINAL_PAYMENT") || 
                            currentStatus.equals("RETURNED") ||
                            "CHECKING".equalsIgnoreCase(vehicleStatus);
        
        if (isReturned) {
            // Tính remainingAmount sau khi approve
            BigDecimal remainingAmount = calculateRemainingAmountForOrder(order);
            log.info("💰 [approveCash] Order status: {}, vehicle status: {}, remainingAmount: {}", currentStatus, vehicleStatus, remainingAmount);
            
            if (remainingAmount.compareTo(BigDecimal.ZERO) == 0) {
                order.setStatus("COMPLETED");
                rentalOrderRepository.save(order);
                log.info("✅ [approveCash] Đã thanh toán hết và đã trả xe → chuyển thành COMPLETED");
            }
        } else {
            log.info("ℹ️ [approveCash] Chưa trả xe (vehicle status: {}, order status: {}), giữ nguyên status", vehicleStatus, currentStatus);
        }

        log.info("✅ CASH payment approved successfully for orderId={}", orderId);
    }

    // Helper method để tính remainingAmount cho order
    // Logic mới: remainingAmount đã bao gồm cả dịch vụ (không cần cộng thêm SERVICE PENDING)
    private BigDecimal calculateRemainingAmountForOrder(RentalOrder order) {
        List<Payment> payments = paymentRepository.findByRentalOrder_OrderId(order.getOrderId());
        
        if (payments == null || payments.isEmpty()) {
            BigDecimal totalPrice = order.getTotalPrice() != null ? order.getTotalPrice() : BigDecimal.ZERO;
            return totalPrice;
        }
        
        // Kiểm tra FULL_PAYMENT (type 3) SUCCESS
        Optional<Payment> fullPayment = payments.stream()
                .filter(p -> p.getPaymentType() == 3 && p.getStatus() == PaymentStatus.SUCCESS)
                .findFirst();
        
        if (fullPayment.isPresent()) {
            BigDecimal remaining = fullPayment.get().getRemainingAmount();
            return remaining != null && remaining.compareTo(BigDecimal.ZERO) > 0 ? remaining : BigDecimal.ZERO;
        }
        
        // Kiểm tra FINAL_PAYMENT (type 2) SUCCESS
        boolean hasFinalPaymentSuccess = payments.stream()
                .anyMatch(p -> p.getPaymentType() == 2 && p.getStatus() == PaymentStatus.SUCCESS);
        if (hasFinalPaymentSuccess) {
            // Đã thanh toán PICKUP, kiểm tra xem DEPOSIT còn remainingAmount không (dịch vụ mới)
            Optional<Payment> depositPayment = payments.stream()
                    .filter(p -> p.getPaymentType() == 1 && p.getStatus() == PaymentStatus.SUCCESS)
                    .findFirst();
            
            if (depositPayment.isPresent()) {
                BigDecimal remaining = depositPayment.get().getRemainingAmount();
                return remaining != null && remaining.compareTo(BigDecimal.ZERO) > 0 ? remaining : BigDecimal.ZERO;
            }
            return BigDecimal.ZERO;
        }
        
        // Kiểm tra DEPOSIT (type 1) SUCCESS
        Optional<Payment> depositPayment = payments.stream()
                .filter(p -> p.getPaymentType() == 1 && p.getStatus() == PaymentStatus.SUCCESS)
                .findFirst();
        
        if (depositPayment.isPresent()) {
            BigDecimal remaining = depositPayment.get().getRemainingAmount();
            return remaining != null && remaining.compareTo(BigDecimal.ZERO) > 0 ? remaining : BigDecimal.ZERO;
        }
        
        // Chưa thanh toán gì
        BigDecimal totalPrice = order.getTotalPrice() != null ? order.getTotalPrice() : BigDecimal.ZERO;
        return totalPrice;
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