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
public class PaymentServiceImpl implements PaymentService {

    private final MoMoConfig momoConfig;
    private final ObjectMapper objectMapper;
    private final RentalOrderRepository rentalOrderRepository;
    private final RentalOrderDetailRepository rentalOrderDetailRepository;
    private final PaymentRepository paymentRepository;
    private final TransactionHistoryRepository transactionHistoryRepository;
    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final VehicleTimelineRepository vehicleTimelineRepository;

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

        Vehicle vehicle = getMainVehicle(order);
        BigDecimal total = order.getTotalPrice();

        String method = dto.getMethod();
        if (method == null || method.trim().isEmpty()) {
            throw new BadRequestException("Phương thức thanh toán là bắt buộc");
        }

        List<String> validMethods = List.of("captureWallet", "payWithMethod", "momo");
        if (!validMethods.contains(method)) {
            throw new BadRequestException("Phương thức thanh toán không hợp lệ: " + method);
        }

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
                } else {
                    amount = depositRemaining;
                }
            } else if (fullPaymentOpt.isPresent()) {
                Payment fullPayment = fullPaymentOpt.get();
                BigDecimal outstanding = fullPayment.getRemainingAmount();
                if (outstanding == null || outstanding.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BadRequestException("Không có khoản nào cần thanh toán (full payment)");
                }
                amount = outstanding;
            } else {
                throw new BadRequestException("Must pay deposit first or have outstanding full payment");
            }

            remainingAmount = BigDecimal.ZERO;

            Payment existingFinalPayment = paymentRepository.findByRentalOrder_OrderId(order.getOrderId())
                    .stream()
                    .filter(p -> p.getPaymentType() == 2 && p.getStatus() == PaymentStatus.PENDING)
                    .findFirst()
                    .orElse(null);

            if (existingFinalPayment != null) {
                existingFinalPayment.setAmount(amount);
                existingFinalPayment.setRemainingAmount(BigDecimal.ZERO);
                existingFinalPayment.setMethod(method);
                Payment payment = paymentRepository.save(existingFinalPayment);
                updateOrderStatus(order, type);
                return buildMoMoPaymentUrl(order, payment, amount);
            }
        } else if (type == 3) {
            amount = total;
            remainingAmount = BigDecimal.ZERO;
        } else if (type == 5) {
            Optional<Payment> depositPaymentOpt = paymentRepository.findByRentalOrder_OrderId(order.getOrderId())
                    .stream()
                    .filter(p -> p.getPaymentType() == 1 && p.getStatus() == PaymentStatus.SUCCESS)
                    .findFirst();

            Optional<Payment> fullPaymentOpt = paymentRepository.findByRentalOrder_OrderId(order.getOrderId())
                    .stream()
                    .filter(p -> p.getPaymentType() == 3 && p.getStatus() == PaymentStatus.SUCCESS)
                    .findFirst();

            if (fullPaymentOpt.isPresent()) {
                Payment fullPayment = fullPaymentOpt.get();
                BigDecimal outstanding = fullPayment.getRemainingAmount();
                if (outstanding == null || outstanding.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BadRequestException("Không có khoản dịch vụ nào cần thanh toán");
                }
                amount = outstanding;
            } else if (depositPaymentOpt.isPresent()) {
                Payment depositPayment = depositPaymentOpt.get();
                BigDecimal depositRemaining = depositPayment.getRemainingAmount();
                if (depositRemaining == null || depositRemaining.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BadRequestException("Không có khoản dịch vụ nào cần thanh toán");
                }
                amount = depositRemaining;
            } else {
                throw new BadRequestException("Không có khoản dịch vụ nào cần thanh toán");
            }

            remainingAmount = BigDecimal.ZERO;

            List<String> momoMethods = List.of("captureWallet", "payWithMethod", "momo");
            Payment existingMoMoServicePayment = paymentRepository.findByRentalOrder_OrderId(order.getOrderId())
                    .stream()
                    .filter(p -> p.getPaymentType() == 5 && p.getStatus() == PaymentStatus.PENDING)
                    .filter(p -> {
                        String pMethod = p.getMethod();
                        return pMethod != null && momoMethods.contains(pMethod);
                    })
                    .findFirst()
                    .orElse(null);

            if (existingMoMoServicePayment != null) {
                existingMoMoServicePayment.setAmount(amount);
                existingMoMoServicePayment.setRemainingAmount(BigDecimal.ZERO);
                existingMoMoServicePayment.setMethod(method);
                Payment payment = paymentRepository.save(existingMoMoServicePayment);
                updateOrderStatus(order, type);
                return buildMoMoPaymentUrl(order, payment, amount);
            }
        } else {
            amount = BigDecimal.ZERO;
            remainingAmount = BigDecimal.ZERO;
        }

        if (type == 2) {
            if (amount.compareTo(total) == 0) {
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

        updateOrderStatus(order, type);

        if (type != 2 && type != 5) {
            createOrUpdateDetail(order, vehicle, getTypeName(type), amount, getDescription(type), "PENDING");
        }

        if (type == 2) {
            List<RentalOrderDetail> allDetails = rentalOrderDetailRepository.findByOrder_OrderId(order.getOrderId());
            RentalOrderDetail rentalDetail = allDetails.stream()
                    .filter(d -> "RENTAL".equalsIgnoreCase(d.getType()))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("Missing RENTAL detail for order"));

            Optional<Payment> depositPaymentOpt = paymentRepository.findByRentalOrder_OrderId(order.getOrderId())
                    .stream()
                    .filter(p -> p.getPaymentType() == 1 && p.getStatus() == PaymentStatus.SUCCESS)
                    .findFirst();

            BigDecimal pickupPrice;
            if (depositPaymentOpt.isPresent()) {
                Payment depositPayment = depositPaymentOpt.get();
                BigDecimal depositRemaining = depositPayment.getRemainingAmount();
                
                if (depositRemaining != null && depositRemaining.compareTo(BigDecimal.ZERO) > 0) {
                    pickupPrice = depositRemaining;
                } else {
                    pickupPrice = amount;
                }
            } else {
                pickupPrice = amount;
            }

            RentalOrderDetail existingPickupDetail = allDetails.stream()
                    .filter(d -> "PICKUP".equalsIgnoreCase(d.getType()))
                    .findFirst()
                    .orElse(null);

            if (existingPickupDetail == null) {
                Vehicle pickupVehicle = rentalDetail.getVehicle();
                if (pickupVehicle == null) {
                    throw new BadRequestException("Missing vehicle in RENTAL detail");
                }

                RentalOrderDetail pickupDetail = RentalOrderDetail.builder()
                        .order(order)
                        .vehicle(pickupVehicle)
                        .type("PICKUP")
                        .startTime(rentalDetail.getStartTime())
                        .endTime(rentalDetail.getEndTime())
                        .price(pickupPrice)
                        .status("PENDING")
                        .description("Thanh toán thuê xe")
                        .build();

                rentalOrderDetailRepository.save(pickupDetail);
            }
        }

        return buildMoMoPaymentUrl(order, payment, amount);
    }

    @Override
    @Transactional
    public PaymentResponse handleMoMoCallback(Map<String, String> params) {
        String orderId = params.get("orderId");
        if (orderId == null)
            throw new BadRequestException("Missing orderId in MoMo callback");

        String raw = orderId.split("-")[0];
        String uuid = raw.replaceFirst(
                "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})",
                "$1-$2-$3-$4-$5"
        );

        Payment payment = paymentRepository.findById(UUID.fromString(uuid))
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        RentalOrder order = payment.getRentalOrder();

        String resultCode = params.get("resultCode");
        boolean ok = "0".equals(resultCode);

        if (!ok) {
            payment.setStatus(PaymentStatus.FAILED);
            order.setStatus("PAYMENT_FAILED");
            paymentRepository.save(payment);
            rentalOrderRepository.save(order);
            return buildCallbackResponse(order, payment, false);
        }

        payment.setStatus(PaymentStatus.SUCCESS);

        switch (payment.getPaymentType()) {
            case 1 -> {
                Vehicle v = getMainVehicle(order);
                depositSuccess(order, payment, v);
            }
            case 2 -> finalSuccess(order, payment);
            case 3 -> {
                Vehicle v = getMainVehicle(order);
                fullSuccess(order, payment, v);
            }
            case 5 -> servicePaymentSuccess(order, payment);
        }

        paymentRepository.save(payment);
        rentalOrderRepository.save(order);

        recordTransaction(order, payment, getTypeName(payment.getPaymentType()));

        return buildCallbackResponse(order, payment, true);
    }

    private void depositSuccess(RentalOrder order, Payment payment, Vehicle v) {
        order.setStatus("DEPOSITED");
        BigDecimal deposit = payment.getAmount();
        BigDecimal totalPrice = order.getTotalPrice();
        BigDecimal remainingAmount = totalPrice.subtract(deposit);
        
        payment.setRemainingAmount(remainingAmount);
        paymentRepository.save(payment);
        
        createOrUpdateDetail(order, v, "DEPOSIT", deposit, "Đặt cọc giữ xe", "SUCCESS");
    }

    private void finalSuccess(RentalOrder order, Payment payment) {
        payment.setRemainingAmount(BigDecimal.ZERO);

        UUID orderId = order.getOrderId();
        
        order = rentalOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        
        List<RentalOrderDetail> allDetails = rentalOrderDetailRepository.findByOrder_OrderId(orderId);
        RentalOrderDetail rentalDetail = allDetails.stream()
                .filter(d -> "RENTAL".equalsIgnoreCase(d.getType()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Missing RENTAL detail for order"));
        
        Optional<Payment> depositPaymentOpt = paymentRepository.findByRentalOrder_OrderId(orderId)
                .stream()
                .filter(p -> p.getPaymentType() == 1 && p.getStatus() == PaymentStatus.SUCCESS)
                .findFirst();

        if (depositPaymentOpt.isPresent()) {
            Payment depositPayment = depositPaymentOpt.get();
            BigDecimal depositRemaining = depositPayment.getRemainingAmount();
            BigDecimal depositAmount = depositPayment.getAmount();
            BigDecimal totalPrice = order.getTotalPrice();

            BigDecimal currentRemaining;
            if (depositRemaining != null && depositRemaining.compareTo(BigDecimal.ZERO) > 0) {
                currentRemaining = depositRemaining;
            } else {
                currentRemaining = totalPrice.subtract(depositAmount);
            }

            BigDecimal amountToPay = payment.getAmount();

            BigDecimal newRemaining = currentRemaining.subtract(amountToPay);
            if (newRemaining.compareTo(BigDecimal.ZERO) < 0) {
                newRemaining = BigDecimal.ZERO;
            }

            depositPayment.setRemainingAmount(newRemaining);
            paymentRepository.save(depositPayment);
            
            RentalOrderDetail pickupDetail = rentalOrderDetailRepository.findByOrder_OrderId(orderId)
                    .stream()
                    .filter(d -> "PICKUP".equalsIgnoreCase(d.getType()))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("PICKUP detail not found. Please create payment URL first."));

            pickupDetail.setStatus("SUCCESS");
            pickupDetail.setPrice(currentRemaining);
            pickupDetail.setDescription("Thanh toán thuê xe");
            rentalOrderDetailRepository.save(pickupDetail);
            rentalOrderDetailRepository.flush();

            List<RentalOrderDetail> allPickupDetails = rentalOrderDetailRepository.findByOrder_OrderId(orderId)
                    .stream()
                    .filter(d -> "PICKUP".equalsIgnoreCase(d.getType()))
                    .collect(java.util.stream.Collectors.toList());

            if (allPickupDetails.size() > 1 && pickupDetail != null) {
                List<RentalOrderDetail> duplicatesToDelete = new java.util.ArrayList<>();
                for (RentalOrderDetail d : allPickupDetails) {
                    if (!d.getDetailId().equals(pickupDetail.getDetailId())) {
                        duplicatesToDelete.add(d);
                    }
                }
                if (!duplicatesToDelete.isEmpty()) {
                    rentalOrderDetailRepository.deleteAll(duplicatesToDelete);
                }
            }

            if (newRemaining.compareTo(BigDecimal.ZERO) == 0) {
                markServiceDetailsAsSuccess(order);
                updateOrderStatusAfterPayment(order);
            } else {
                order.setStatus("PENDING_FINAL_PAYMENT");
            }
            return;
        }

        Optional<Payment> fullPaymentOpt = paymentRepository.findByRentalOrder_OrderId(order.getOrderId())
                .stream()
                .filter(p -> p.getPaymentType() == 3 && p.getStatus() == PaymentStatus.SUCCESS)
                .findFirst();

        if (fullPaymentOpt.isPresent()) {
            Payment fullPayment = fullPaymentOpt.get();
            BigDecimal outstanding = Optional.ofNullable(fullPayment.getRemainingAmount()).orElse(BigDecimal.ZERO);

            if (outstanding.compareTo(BigDecimal.ZERO) <= 0) {
                updateOrderStatusAfterPayment(order);
                return;
            }

            BigDecimal amountToPay = payment.getAmount();
            BigDecimal newRemaining = outstanding.subtract(amountToPay);
            if (newRemaining.compareTo(BigDecimal.ZERO) < 0) {
                newRemaining = BigDecimal.ZERO;
            }

            fullPayment.setRemainingAmount(newRemaining);
            paymentRepository.save(fullPayment);

            if (newRemaining.compareTo(BigDecimal.ZERO) == 0) {
                markServiceDetailsAsSuccess(order);
                updateOrderStatusAfterPayment(order);
            } else {
                order.setStatus("PENDING_FINAL_PAYMENT");
            }
        } else {
            RentalOrderDetail pickupDetail = rentalOrderDetailRepository.findByOrder_OrderId(orderId)
                    .stream()
                    .filter(d -> "PICKUP".equalsIgnoreCase(d.getType()))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("PICKUP detail not found. Please create payment URL first."));

            pickupDetail.setStatus("SUCCESS");
            pickupDetail.setPrice(payment.getAmount());
            pickupDetail.setDescription("Thanh toán thuê xe");
            rentalOrderDetailRepository.save(pickupDetail);
            rentalOrderDetailRepository.flush();

            updateOrderStatusAfterPayment(order);
        }
    }

    private void createOrUpdatePickupDetail(RentalOrder order, BigDecimal amount) {
        UUID orderId = order.getOrderId();
        
        order = rentalOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        Vehicle vehicle = getMainVehicle(order);
        if (vehicle == null) {
            return;
        }

        List<RentalOrderDetail> allPickupDetails = rentalOrderDetailRepository.findByOrder_OrderId(orderId)
                .stream()
                .filter(d -> "PICKUP".equalsIgnoreCase(d.getType()))
                .collect(java.util.stream.Collectors.toList());

        if (!allPickupDetails.isEmpty()) {
            RentalOrderDetail detailToUpdate = allPickupDetails.stream()
                    .sorted((d1, d2) -> {
                        boolean d1Pending = "PENDING".equalsIgnoreCase(d1.getStatus());
                        boolean d2Pending = "PENDING".equalsIgnoreCase(d2.getStatus());
                        if (d1Pending && !d2Pending) return -1;
                        if (!d1Pending && d2Pending) return 1;
                        return 0;
                    })
                    .findFirst()
                    .orElse(allPickupDetails.get(0));

            detailToUpdate.setStatus("SUCCESS");
            detailToUpdate.setPrice(amount);
            detailToUpdate.setDescription("Thanh toán thuê xe");
            rentalOrderDetailRepository.save(detailToUpdate);

            if (allPickupDetails.size() > 1) {
                List<RentalOrderDetail> duplicatesToDelete = new java.util.ArrayList<>();
                for (RentalOrderDetail d : allPickupDetails) {
                    if (!d.getDetailId().equals(detailToUpdate.getDetailId())) {
                        duplicatesToDelete.add(d);
                    }
                }
                
                if (!duplicatesToDelete.isEmpty()) {
                    rentalOrderDetailRepository.deleteAll(duplicatesToDelete);
                }
            }
        } else {
            List<RentalOrderDetail> doubleCheck = rentalOrderDetailRepository.findByOrder_OrderId(orderId)
                    .stream()
                    .filter(d -> "PICKUP".equalsIgnoreCase(d.getType()))
                    .collect(java.util.stream.Collectors.toList());
            
            if (doubleCheck.isEmpty()) {
                createDetail(order, vehicle, "PICKUP", amount, "Thanh toán thuê xe", "SUCCESS");
            } else {
                RentalOrderDetail existingDetail = doubleCheck.get(0);
                existingDetail.setStatus("SUCCESS");
                existingDetail.setPrice(amount);
                existingDetail.setDescription("Thanh toán thuê xe");
                rentalOrderDetailRepository.save(existingDetail);
                
                if (doubleCheck.size() > 1) {
                    List<RentalOrderDetail> duplicatesToDelete = new java.util.ArrayList<>();
                    for (RentalOrderDetail d : doubleCheck) {
                        if (!d.getDetailId().equals(existingDetail.getDetailId())) {
                            duplicatesToDelete.add(d);
                        }
                    }
                    if (!duplicatesToDelete.isEmpty()) {
                        rentalOrderDetailRepository.deleteAll(duplicatesToDelete);
                    }
                }
            }
        }
    }

    private void updateOrderStatusAfterPayment(RentalOrder order) {
        order = rentalOrderRepository.findById(order.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        String currentStatus = order.getStatus();

        boolean isReturned = currentStatus.equals("PENDING_FINAL_PAYMENT") ||
                currentStatus.equals("RETURNED");

        if (isReturned) {
        } else {
            boolean hasServiceDetails = Optional.ofNullable(order.getDetails())
                    .orElse(List.of()).stream()
                    .anyMatch(d -> "SERVICE".equalsIgnoreCase(d.getType()));
            
            if (hasServiceDetails) {
                order.setStatus("PAID");
            } else {
                order.setStatus("AWAITING");
            }
            rentalOrderRepository.save(order);
        }
    }

    private void fullSuccess(RentalOrder order, Payment payment, Vehicle v) {
        BigDecimal fullAmount = payment.getAmount();
        payment.setRemainingAmount(BigDecimal.ZERO);

        createOrUpdateDetail(order, v, "FULL_PAYMENT", fullAmount, "Thanh toán toàn bộ đơn", "SUCCESS");

        String currentStatus = order.getStatus();
        boolean isReturned = currentStatus.equals("PENDING_FINAL_PAYMENT") ||
                currentStatus.equals("RETURNED");

        if (isReturned) {
        } else {
            boolean hasServiceDetails = Optional.ofNullable(order.getDetails())
                    .orElse(List.of()).stream()
                    .anyMatch(d -> "SERVICE".equalsIgnoreCase(d.getType()));
            
            if (hasServiceDetails) {
                order.setStatus("PAID");
            } else {
                order.setStatus("AWAITING");
            }
        }
    }

    private void servicePaymentSuccess(RentalOrder order, Payment payment) {
        payment.setRemainingAmount(BigDecimal.ZERO);

        UUID orderId = order.getOrderId();

        Optional<Payment> fullPaymentOpt = paymentRepository.findByRentalOrder_OrderId(orderId)
                .stream()
                .filter(p -> p.getPaymentType() == 3 && p.getStatus() == PaymentStatus.SUCCESS)
                .findFirst();

        if (fullPaymentOpt.isPresent()) {
            Payment fullPayment = fullPaymentOpt.get();
            BigDecimal outstanding = Optional.ofNullable(fullPayment.getRemainingAmount()).orElse(BigDecimal.ZERO);

            if (outstanding.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal amountToPay = payment.getAmount();
                BigDecimal newRemaining = outstanding.subtract(amountToPay);
                if (newRemaining.compareTo(BigDecimal.ZERO) < 0) {
                    newRemaining = BigDecimal.ZERO;
                }

                fullPayment.setRemainingAmount(newRemaining);
                paymentRepository.save(fullPayment);

                if (newRemaining.compareTo(BigDecimal.ZERO) == 0) {
                    markServiceDetailsAsSuccess(order);
                    updateOrderStatusAfterPayment(order);
                } else {
                    order.setStatus("PENDING_FINAL_PAYMENT");
                }
                return;
            }
        }

        Optional<Payment> depositPaymentOpt = paymentRepository.findByRentalOrder_OrderId(orderId)
                .stream()
                .filter(p -> p.getPaymentType() == 1 && p.getStatus() == PaymentStatus.SUCCESS)
                .findFirst();

        if (depositPaymentOpt.isPresent()) {
            Payment depositPayment = depositPaymentOpt.get();
            BigDecimal remainingAmount = depositPayment.getRemainingAmount();

            if (remainingAmount != null && remainingAmount.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal amountToPay = payment.getAmount();
                BigDecimal newRemaining = remainingAmount.subtract(amountToPay);
                if (newRemaining.compareTo(BigDecimal.ZERO) < 0) {
                    newRemaining = BigDecimal.ZERO;
                }

                depositPayment.setRemainingAmount(newRemaining);
                paymentRepository.save(depositPayment);

                if (newRemaining.compareTo(BigDecimal.ZERO) == 0) {
                    markServiceDetailsAsSuccess(order);
                    updateOrderStatusAfterPayment(order);
                } else {
                    order.setStatus("PENDING_FINAL_PAYMENT");
                }
                return;
            }
        }

        markServiceDetailsAsSuccess(order);
        updateOrderStatusAfterPayment(order);
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
            rentalOrderDetailRepository.save(d);
        } else {
            createDetail(order, v, type, price, desc, status);
        }
    }

    private void createDetail(RentalOrder order, Vehicle v, String type, BigDecimal price, String desc, String status) {
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
    }

    private PaymentResponse buildMoMoPaymentUrl(RentalOrder order, Payment payment, BigDecimal amount) {
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

            String amountStr = String.valueOf(amount.longValue());
            String extraData = "";

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

            String signature = Utils.hmacSHA256(secretKey, rawSignature);

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

            String requestBody = objectMapper.writeValueAsString(momoRequest);

            URI uri = new URI(endpoint);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

            OutputStream os = conn.getOutputStream();
            os.write(requestBody.getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();

            int responseCode = conn.getResponseCode();

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

            MomoCreatePaymentResponse momoResponse = objectMapper.readValue(
                    responseStr,
                    MomoCreatePaymentResponse.class
            );

            Integer resultCode = momoResponse.getResultCode();
            Integer errorCode = momoResponse.getErrorCode();

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
            case 5 -> order.setStatus("PENDING_SERVICE_PAYMENT");
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
            case 1 -> "Đặt cọc giữ xe";
            case 2 -> "Thanh toán thuê xe";
            case 3 -> "Thanh toán toàn bộ đơn thuê";
            case 4 -> "Hoàn tiền";
            case 5 -> "Thanh toán dịch vụ";
            default -> "Không xác định";
        };
    }

    @Override
    @Transactional
    public PaymentResponse refund(UUID orderId, BigDecimal amount) {
        RentalOrder order = rentalOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        // Lấy payment SUCCESS đầu tiên để tính số tiền có thể hoàn
        Payment originalPayment = paymentRepository.findByRentalOrder_OrderId(orderId)
                .stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for order"));

        // Nếu không nhập amount, mặc định hoàn toàn bộ số tiền đã thanh toán
        BigDecimal refundAmount;
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            refundAmount = originalPayment.getAmount();
        } else {
            refundAmount = amount;
            // Validate: số tiền hoàn không được vượt quá số tiền đã thanh toán
            if (refundAmount.compareTo(originalPayment.getAmount()) > 0) {
                throw new BadRequestException("Số tiền hoàn không được vượt quá số tiền đã thanh toán: " + originalPayment.getAmount());
            }
        }

        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0)
            throw new BadRequestException("Không có số tiền nào để hoàn");

        // Tạo payment mới với số tiền âm (hoàn tiền)
        Payment refundPayment = Payment.builder()
                .rentalOrder(order)
                .amount(refundAmount.negate()) // Số tiền âm để thể hiện hoàn tiền
                .remainingAmount(BigDecimal.ZERO)
                .status(PaymentStatus.SUCCESS)
                .paymentType((short) 4) // Type 4 = REFUND
                .method("INTERNAL_REFUND")
                .build();

        paymentRepository.save(refundPayment);

        // Cập nhật remainingAmount của payment gốc (nếu hoàn một phần)
        if (refundAmount.compareTo(originalPayment.getAmount()) < 0) {
            BigDecimal newRemaining = originalPayment.getRemainingAmount() != null 
                    ? originalPayment.getRemainingAmount().add(refundAmount)
                    : refundAmount;
            originalPayment.setRemainingAmount(newRemaining);
            paymentRepository.save(originalPayment);
        }

        // Ghi transaction với số tiền âm
        recordTransaction(order, refundPayment, "REFUND");

        // Lấy rentalDetail để cập nhật vehicle status (không tạo detail mới)
        RentalOrderDetail rentalDetail = order.getDetails().stream()
                .filter(d -> "RENTAL".equalsIgnoreCase(d.getType()))
                .findFirst()
                .orElse(null);

        // Sau khi hoàn tiền (full hoặc partial) → coi như đơn đã được hoàn,
        // cập nhật trạng thái order & trả xe về trạng thái phù hợp (thường là AVAILABLE)
        order.setStatus("REFUNDED");
        rentalOrderRepository.save(order);

        if (rentalDetail != null && rentalDetail.getVehicle() != null) {
            Vehicle vehicle = rentalDetail.getVehicle();
            Long vehicleId = vehicle.getVehicleId();

            // Xóa timeline gắn với đơn này
            if (vehicleId != null) {
                var timelines = vehicleTimelineRepository.findByVehicle_VehicleId(vehicleId);
                var toDelete = timelines.stream()
                        .filter(t -> t.getOrder() != null && t.getOrder().getOrderId().equals(orderId))
                        .toList();
                if (!toDelete.isEmpty()) {
                    vehicleTimelineRepository.deleteAll(toDelete);
                }

                // Tính lại status xe dựa trên các timeline còn lại
                var remaining = vehicleTimelineRepository.findByVehicle_VehicleId(vehicleId);
                LocalDateTime now = LocalDateTime.now();

                boolean hasActiveRental = remaining.stream()
                        .anyMatch(t -> {
                            if (!"RENTAL".equalsIgnoreCase(t.getStatus())) return false;
                            LocalDateTime start = t.getStartTime();
                            LocalDateTime end = t.getEndTime();
                            return start != null && end != null &&
                                    !now.isBefore(start) && !now.isAfter(end);
                        });

                if (hasActiveRental) {
                    vehicle.setStatus("RENTAL");
                } else {
                    var nextBooked = remaining.stream()
                            .filter(t -> "BOOKED".equalsIgnoreCase(t.getStatus()))
                            .filter(t -> t.getStartTime() != null && t.getStartTime().isAfter(now))
                            .min(java.util.Comparator.comparing(VehicleTimeline::getStartTime));

                    if (nextBooked.isPresent()) {
                        vehicle.setStatus("BOOKED");
                    } else {
                        vehicle.setStatus("AVAILABLE");
                    }
                }

                vehicleRepository.save(vehicle);
            }
        }

        return PaymentResponse.builder()
                .paymentId(refundPayment.getPaymentId())
                .orderId(order.getOrderId())
                .amount(refundAmount)
                .remainingAmount(originalPayment.getRemainingAmount() != null 
                        ? originalPayment.getRemainingAmount() 
                        : BigDecimal.ZERO)
                .method("INTERNAL_REFUND")
                .status(PaymentStatus.SUCCESS)
                .paymentType((short) 4)
                .message("Hoàn tiền thành công: " + refundAmount)
                .build();
    }

    @Override
    @Transactional
    public PaymentResponse processCashPayment(PaymentDto dto, UUID userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        RentalOrder order = rentalOrderRepository.findById(dto.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        short type = dto.getPaymentType();
        if (type < 1 || type > 5)
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
                } else {
                    amount = depositRemaining;
                }
            } else if (fullPaymentOpt.isPresent()) {
                Payment fullPayment = fullPaymentOpt.get();
                BigDecimal outstanding = fullPayment.getRemainingAmount();

                if (outstanding == null || outstanding.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BadRequestException("Không có khoản nào cần thanh toán (full payment)");
                }

                amount = outstanding;
            } else {
                throw new BadRequestException("Must pay deposit first or have outstanding full payment");
            }

            remainingAmount = BigDecimal.ZERO;
        } else if (type == 3) {
            Optional<Payment> existingDeposit = paymentRepository.findByRentalOrder_OrderId(order.getOrderId())
                    .stream()
                    .filter(p -> p.getPaymentType() == 1 && p.getStatus() == PaymentStatus.SUCCESS)
                    .findFirst();

            if (existingDeposit.isPresent()) {
                Payment depositPayment = existingDeposit.get();
                BigDecimal depositRemaining = depositPayment.getRemainingAmount();

                if (depositRemaining == null || depositRemaining.compareTo(BigDecimal.ZERO) <= 0) {
                    amount = total.subtract(depositPayment.getAmount());
                } else {
                    amount = depositRemaining;
                }

                type = 2;
                remainingAmount = BigDecimal.ZERO;
            } else {
                amount = total;
                remainingAmount = BigDecimal.ZERO;
            }
        } else if (type == 5) {
            Optional<Payment> depositPaymentOpt = paymentRepository.findByRentalOrder_OrderId(order.getOrderId())
                    .stream()
                    .filter(p -> p.getPaymentType() == 1 && p.getStatus() == PaymentStatus.SUCCESS)
                    .findFirst();

            Optional<Payment> fullPaymentOpt = paymentRepository.findByRentalOrder_OrderId(order.getOrderId())
                    .stream()
                    .filter(p -> p.getPaymentType() == 3 && p.getStatus() == PaymentStatus.SUCCESS)
                    .findFirst();

            if (fullPaymentOpt.isPresent()) {
                Payment fullPayment = fullPaymentOpt.get();
                BigDecimal outstanding = fullPayment.getRemainingAmount();
                if (outstanding == null || outstanding.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BadRequestException("Không có khoản dịch vụ nào cần thanh toán");
                }
                amount = outstanding;
            } else if (depositPaymentOpt.isPresent()) {
                Payment depositPayment = depositPaymentOpt.get();
                BigDecimal depositRemaining = depositPayment.getRemainingAmount();
                if (depositRemaining == null || depositRemaining.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BadRequestException("Không có khoản dịch vụ nào cần thanh toán");
                }
                amount = depositRemaining;
            } else {
                throw new BadRequestException("Không có khoản dịch vụ nào cần thanh toán");
            }

            remainingAmount = BigDecimal.ZERO;
        } else {
            throw new BadRequestException("Unsupported cash payment type");
        }

        if (type == 2) {
            if (amount.compareTo(total) == 0) {
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
                }
            }
        }

        Payment payment;
        try {
            if (type == 2) {
                Payment existingFinalPayment = paymentRepository.findByRentalOrder_OrderId(order.getOrderId())
                        .stream()
                        .filter(p -> p.getPaymentType() == 2 && p.getStatus() == PaymentStatus.PENDING)
                        .filter(p -> "CASH".equalsIgnoreCase(p.getMethod()))
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
            } else if (type == 5) {
                Payment existingCashServicePayment = paymentRepository.findByRentalOrder_OrderId(order.getOrderId())
                        .stream()
                        .filter(p -> p.getPaymentType() == 5 && p.getStatus() == PaymentStatus.PENDING)
                        .filter(p -> "CASH".equalsIgnoreCase(p.getMethod()))
                        .findFirst()
                        .orElse(null);

                if (existingCashServicePayment != null) {
                    existingCashServicePayment.setAmount(amount);
                    existingCashServicePayment.setRemainingAmount(BigDecimal.ZERO);
                    existingCashServicePayment.setMethod("CASH");
                    payment = paymentRepository.save(existingCashServicePayment);
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
        } catch (Exception e) {
            throw new BadRequestException("Lỗi khi tạo payment: " + e.getMessage());
        }

        try {
            updateOrderStatus(order, type);
        } catch (Exception e) {
        }

        try {
            recordTransaction(order, payment, getTypeName(type) + "_PENDING");
        } catch (Exception e) {
        }

        try {
            if (type == 1) {
                Vehicle v = getMainVehicle(order);
                createOrUpdateDetail(order, v, "DEPOSIT", amount, "Đặt cọc giữ xe", "PENDING");
            } else if (type == 3) {
                Vehicle v = getMainVehicle(order);
                createOrUpdateDetail(order, v, "FULL_PAYMENT", amount, "Thanh toán toàn bộ đơn", "PENDING");
            } else if (type == 2) {
                // Tạo PICKUP detail cho type 2 (giống như MoMo)
                List<RentalOrderDetail> allDetails = rentalOrderDetailRepository.findByOrder_OrderId(order.getOrderId());
                RentalOrderDetail rentalDetail = allDetails.stream()
                        .filter(d -> "RENTAL".equalsIgnoreCase(d.getType()))
                        .findFirst()
                        .orElseThrow(() -> new BadRequestException("Missing RENTAL detail for order"));

                Optional<Payment> depositPaymentOpt = paymentRepository.findByRentalOrder_OrderId(order.getOrderId())
                        .stream()
                        .filter(p -> p.getPaymentType() == 1 && p.getStatus() == PaymentStatus.SUCCESS)
                        .findFirst();

                BigDecimal pickupPrice;
                if (depositPaymentOpt.isPresent()) {
                    Payment depositPayment = depositPaymentOpt.get();
                    BigDecimal depositRemaining = depositPayment.getRemainingAmount();
                    
                    if (depositRemaining != null && depositRemaining.compareTo(BigDecimal.ZERO) > 0) {
                        pickupPrice = depositRemaining;
                    } else {
                        pickupPrice = amount;
                    }
                } else {
                    pickupPrice = amount;
                }

                RentalOrderDetail existingPickupDetail = allDetails.stream()
                        .filter(d -> "PICKUP".equalsIgnoreCase(d.getType()))
                        .findFirst()
                        .orElse(null);

                if (existingPickupDetail == null) {
                    Vehicle pickupVehicle = rentalDetail.getVehicle();
                    if (pickupVehicle == null) {
                        throw new BadRequestException("Missing vehicle in RENTAL detail");
                    }

                    RentalOrderDetail pickupDetail = RentalOrderDetail.builder()
                            .order(order)
                            .vehicle(pickupVehicle)
                            .type("PICKUP")
                            .startTime(rentalDetail.getStartTime())
                            .endTime(rentalDetail.getEndTime())
                            .price(pickupPrice)
                            .status("PENDING")
                            .description("Thanh toán thuê xe")
                            .build();

                    rentalOrderDetailRepository.save(pickupDetail);
                }
            }
        } catch (Exception e) {
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
        RentalOrder order = rentalOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        List<Payment> allCashPending = paymentRepository.findByRentalOrder_OrderId(orderId).stream()
                .filter(p -> "CASH".equalsIgnoreCase(p.getMethod()))
                .filter(p -> p.getStatus() == PaymentStatus.PENDING)
                .toList();

        Payment payment = allCashPending.stream()
                .findFirst()
                .orElseThrow(() -> new BadRequestException("No pending CASH payment for this order"));

        short type = payment.getPaymentType();

        payment.setStatus(PaymentStatus.SUCCESS);
        payment = paymentRepository.save(payment);

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
            case 5 -> servicePaymentSuccess(order, payment);
            default -> throw new BadRequestException("Unknown payment type");
        }

        rentalOrderRepository.save(order);

        String currentStatus = order.getStatus();
        Vehicle vehicle = getMainVehicle(order);
        String vehicleStatus = vehicle != null ? vehicle.getStatus() : null;

        BigDecimal remainingAmount = calculateRemainingAmountForOrder(order);

        boolean isReturned = currentStatus.equals("PENDING_FINAL_PAYMENT") ||
                currentStatus.equals("RETURNED");

        if (isReturned && remainingAmount.compareTo(BigDecimal.ZERO) == 0) {
        } else if (remainingAmount.compareTo(BigDecimal.ZERO) == 0) {
            boolean hasServiceDetails = Optional.ofNullable(order.getDetails())
                    .orElse(List.of()).stream()
                    .anyMatch(d -> "SERVICE".equalsIgnoreCase(d.getType()));
            
            if (hasServiceDetails) {
                order.setStatus("PAID");
            } else {
                order.setStatus("AWAITING");
            }
            rentalOrderRepository.save(order);
        }
    }

    @Override
    @Transactional
    public void autoCompleteOrderIfReady(UUID orderId) {
        try {
            RentalOrder order = rentalOrderRepository.findById(orderId)
                    .orElse(null);

            if (order == null) {
                return;
            }

            String currentStatus = order.getStatus();
            if ("COMPLETED".equals(currentStatus) || "FAILED".equals(currentStatus) || "REFUNDED".equals(currentStatus)) {
                return;
            }

            Vehicle vehicle = getMainVehicle(order);
            String vehicleStatus = vehicle != null ? vehicle.getStatus() : null;

            boolean isReturned = "CHECKING".equalsIgnoreCase(vehicleStatus) ||
                    currentStatus.equals("PENDING_FINAL_PAYMENT") ||
                    currentStatus.equals("RETURNED");

            if (!isReturned) {
                return;
            }

            BigDecimal remainingAmount = calculateRemainingAmountForOrder(order);

            if (remainingAmount.compareTo(BigDecimal.ZERO) == 0) {
                order.setStatus("COMPLETED");
                rentalOrderRepository.save(order);
            }
        } catch (Exception e) {
        }
    }

    private BigDecimal calculateRemainingAmountForOrder(RentalOrder order) {
        List<Payment> payments = paymentRepository.findByRentalOrder_OrderId(order.getOrderId());

        if (payments == null || payments.isEmpty()) {
            BigDecimal totalPrice = order.getTotalPrice() != null ? order.getTotalPrice() : BigDecimal.ZERO;
            return totalPrice;
        }

        Optional<Payment> fullPayment = payments.stream()
                .filter(p -> p.getPaymentType() == 3 && p.getStatus() == PaymentStatus.SUCCESS)
                .findFirst();

        if (fullPayment.isPresent()) {
            BigDecimal remaining = fullPayment.get().getRemainingAmount();
            return remaining != null && remaining.compareTo(BigDecimal.ZERO) > 0 ? remaining : BigDecimal.ZERO;
        }

        boolean hasFinalPaymentSuccess = payments.stream()
                .anyMatch(p -> p.getPaymentType() == 2 && p.getStatus() == PaymentStatus.SUCCESS);
        if (hasFinalPaymentSuccess) {
            Optional<Payment> depositPayment = payments.stream()
                    .filter(p -> p.getPaymentType() == 1 && p.getStatus() == PaymentStatus.SUCCESS)
                    .findFirst();

            if (depositPayment.isPresent()) {
                BigDecimal remaining = depositPayment.get().getRemainingAmount();
                return remaining != null && remaining.compareTo(BigDecimal.ZERO) > 0 ? remaining : BigDecimal.ZERO;
            }
            return BigDecimal.ZERO;
        }

        Optional<Payment> depositPayment = payments.stream()
                .filter(p -> p.getPaymentType() == 1 && p.getStatus() == PaymentStatus.SUCCESS)
                .findFirst();

        if (depositPayment.isPresent()) {
            BigDecimal remaining = depositPayment.get().getRemainingAmount();
            return remaining != null && remaining.compareTo(BigDecimal.ZERO) > 0 ? remaining : BigDecimal.ZERO;
        }

        BigDecimal totalPrice = order.getTotalPrice() != null ? order.getTotalPrice() : BigDecimal.ZERO;
        return totalPrice;
    }

    @Override
    public List<PaymentResponse> getPaymentsByOrderId(UUID orderId) {
        RentalOrder order = rentalOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        List<Payment> payments = paymentRepository.findByRentalOrder_OrderId(orderId);

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

    @Override
    public BigDecimal getRefundedAmountByOrderId(UUID orderId) {
        // Kiểm tra order tồn tại
        rentalOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        // Lấy tất cả payment của order
        List<Payment> payments = paymentRepository.findByRentalOrder_OrderId(orderId);

        // Tính tổng số tiền đã hoàn (payment type = 4 REFUND, status = SUCCESS)
        // Lấy giá trị tuyệt đối vì amount là số âm
        BigDecimal totalRefunded = payments.stream()
                .filter(p -> p.getPaymentType() == 4) // Type 4 = REFUND
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                .map(p -> p.getAmount().abs()) // Lấy giá trị tuyệt đối (vì amount là số âm)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return totalRefunded;
    }

    @Override
    public String getRefundReasonByOrderId(UUID orderId) {
        // Kiểm tra order tồn tại
        RentalOrder order = rentalOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        // Kiểm tra order có status REFUNDED không
        if (!"REFUNDED".equalsIgnoreCase(order.getStatus())) {
            return null; // Đơn chưa được hoàn tiền
        }

        // Lấy payment REFUND đầu tiên (hoặc mới nhất) để lấy lý do
        // Hiện tại Payment entity chưa có field lưu lý do, nên trả về null
        // Có thể mở rộng sau bằng cách thêm field refundReason vào Payment entity
        Optional<Payment> refundPayment = paymentRepository.findByRentalOrder_OrderId(orderId)
                .stream()
                .filter(p -> p.getPaymentType() == 4) // Type 4 = REFUND
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                .findFirst();

        // Nếu có payment REFUND, có thể lấy lý do từ method hoặc description (nếu có)
        // Hiện tại trả về null vì chưa có field lưu lý do
        return refundPayment.isPresent() ? null : null;
    }
}
