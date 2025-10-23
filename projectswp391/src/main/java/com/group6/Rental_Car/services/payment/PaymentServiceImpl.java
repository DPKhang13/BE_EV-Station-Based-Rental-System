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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

        @Value("${VNP_HASHSECRET}")
        private String VNP_SECRET;
        @Value("${VNP_URL}")
        private String VNP_URL;

    @Autowired
    private UserRepository userRepositorys;
    @Autowired
    private VNpayConfig  vnpayConfig;
    @Autowired
    private final RentalOrderRepository rentalOrderRepository;
    @Autowired
    private final PaymentRepository paymentRepository;
    @Autowired
    private final TransactionHistoryRepository transactionHistoryRepository;
    @Autowired
    private final NotificationRepository notificationRepository;


    public PaymentResponse createPaymentUrl(PaymentDto paymentDto, UUID userId){
            User account = userRepositorys.findById(userId)
                    .orElseThrow(()-> new ResourceNotFoundException("Account not found"));
            var rentalOrder = rentalOrderRepository.findById(paymentDto.getOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Rental order not found"));
            BigDecimal totalPrice = rentalOrder.getTotalPrice();
            //set true la dat coc 50%
            if (paymentDto.isDeposit()) {
                totalPrice = totalPrice.multiply(BigDecimal.valueOf(0.5));
            }
            long amount = totalPrice.multiply(BigDecimal.valueOf(100)).longValue();
            Payment payment = Payment.builder()
                    .rentalOrder(rentalOrder)
                    .amount(totalPrice)
                    .method(paymentDto.getMethod())
                    .paymentType((short) 1)
                    .status(PaymentStatus.PENDING)
                    .build();
            paymentRepository.save(payment);
            // Cấu hình VNPay params
            Map<String, String> vnpParamsMap = vnpayConfig.getVNPayConfig();
            vnpParamsMap.put("vnp_Amount", String.valueOf(amount));
            vnpParamsMap.put("vnp_IpAddr", paymentDto.getClientIp());
            // Gắn orderId làm mã tham chiếu VNPay
            vnpParamsMap.put("vnp_TxnRef", payment.getPaymentId().toString());
            vnpParamsMap.put("vnp_OrderInfo", "Payment for order " + rentalOrder.getOrderId());
            String queryUrl = Utils.getPaymentURL(vnpParamsMap, true);
            String hashData = Utils.getPaymentURL(vnpParamsMap, false);
            String vnpSecureHash = Utils.hmacSHA512(VNP_SECRET, hashData);
            queryUrl += "&vnp_SecureHash=" + vnpSecureHash;
            String paymentUrl = VNP_URL + "?" + queryUrl;
            return PaymentResponse.builder()
                    .paymentId(payment.getPaymentId())
                    .orderId(rentalOrder.getOrderId())
                    .amount(payment.getAmount())
                    .method(payment.getMethod())
                    .status(payment.getStatus())
                    .message("Create VNPay payment successfully!")
                    .paymentUrl(paymentUrl)
                    .paymentType(payment.getPaymentType())
                    .build();
        }
    @Override
    public PaymentResponse handleVNPayCallback(Map<String, String> vnpParams) {
        Map<String, String> params = new HashMap<>(vnpParams);

        String responseCode = params.get("vnp_ResponseCode");
        String txnRef = params.get("vnp_TxnRef");
        BigDecimal amount = new BigDecimal(params.get("vnp_Amount"))
                .divide(BigDecimal.valueOf(100));

        UUID paymentId = UUID.fromString(txnRef);
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id " + txnRef));
        RentalOrder order = payment.getRentalOrder();
        if ("00".equals(responseCode)) {
            payment.setStatus(PaymentStatus.SUCCESS);
            order.setStatus("PAYMENT_SUCCESS");
            handlePaymentSuccess(payment);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
        }
        paymentRepository.save(payment);

        TransactionHistory transaction = TransactionHistory.builder()
                .user(payment.getRentalOrder().getCustomer())
                .amount(amount)
                .type("00".equals(responseCode) ? "PAYMENT_SUCCESS" : "PAYMENT_FAILED")
                .createdAt(LocalDateTime.now())
                .build();
        transactionHistoryRepository.save(transaction);

        return PaymentResponse.builder()
                .paymentId(payment.getPaymentId())
                .orderId(payment.getRentalOrder().getOrderId())
                .amount(amount)
                .status(payment.getStatus())
                .message("00".equals(responseCode) ? "PAYMENT_SUCCESS" : "PAYMENT_FAILED")
                .build();
    }
    @Transactional
    public void handlePaymentSuccess(Payment payment) {
        RentalOrder order = payment.getRentalOrder();
        short type = payment.getPaymentType();

        switch (type) {
            case 1 -> { //  Thanh toán đơn thuê
                order.setStatus("PAYMENT_SUCCESS");
                rentalOrderRepository.save(order);

                Notification notify = new Notification();
                notify.setUser(order.getCustomer());
                notify.setMessage("Thanh toán thuê xe thành công cho đơn #" + order.getOrderId() + ".");
                notify.setCreatedAt(LocalDateTime.now());
                notificationRepository.save(notify);
            }
            case 2 -> {
                order.setPenaltyFee(BigDecimal.ZERO);
                rentalOrderRepository.save(order);

                Notification notify = new Notification();
                notify.setUser(order.getCustomer());
                notify.setMessage("Bạn đã thanh toán phí phạt "
                        + payment.getAmount() + "đ cho đơn #" + order.getOrderId());
                notify.setCreatedAt(LocalDateTime.now());
                notificationRepository.save(notify);
            }
            case 3 -> { //  Hoàn tiền
                Notification notify = new Notification();
                notify.setUser(order.getCustomer());
                notify.setMessage("Đã hoàn tiền " + payment.getAmount() + "đ cho đơn #" + order.getOrderId());
                notify.setCreatedAt(LocalDateTime.now());
                notificationRepository.save(notify);
            }
            default -> throw new IllegalArgumentException("Loại thanh toán không hợp lệ: " + type);
        }

        payment.setStatus(PaymentStatus.SUCCESS);
        paymentRepository.save(payment);
    }
}


