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
            if (paymentDto.getPaymentType() == 2 && paymentDto.getPaymentType() != null){
                totalPrice = rentalOrder.getPenaltyFee();
            }
            //set true la dat coc 50%
            else if (paymentDto.isDeposit() && (paymentDto.getPaymentType() == null || paymentDto.getPaymentType() == 1)) {
                totalPrice = totalPrice.multiply(BigDecimal.valueOf(0.5));
            }
            long amount = totalPrice.multiply(BigDecimal.valueOf(100)).longValue();
            if (paymentDto.getPaymentType() == 1 && paymentDto.getPaymentType() == null){
                rentalOrder.setStatus("PENDING");
                rentalOrderRepository.save(rentalOrder);
            }
            Payment payment = Payment.builder()
                    .rentalOrder(rentalOrder)
                    .amount(totalPrice)
                    .method(paymentDto.getMethod())
                    .paymentType(paymentDto.getPaymentType())
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

        return PaymentResponse.builder()
                .paymentId(payment.getPaymentId())
                .orderId(payment.getRentalOrder().getOrderId())
                .amount(amount)
                .method(payment.getMethod())
                .status(payment.getStatus())
                .message("00".equals(responseCode) ? "PAYMENT_SUCCESS" : "PAYMENT_FAILED")
                .paymentType(payment.getPaymentType())
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

                TransactionHistory transactionHistory = new TransactionHistory();
                transactionHistory.setUser(order.getCustomer());
                transactionHistory.setAmount(payment.getAmount());
                transactionHistory.setCreatedAt(LocalDateTime.now());
                transactionHistory.setType("Payment for order ");
                transactionHistory.setStatus("PAYMENT_SUCCESS");
                transactionHistoryRepository.save(transactionHistory);
            }
            case 2 -> {
                order.setPenaltyFee(BigDecimal.ZERO);
                rentalOrderRepository.save(order);

                TransactionHistory transactionHistory = new TransactionHistory();
                transactionHistory.setUser(order.getCustomer());
                transactionHistory.setAmount(payment.getAmount());
                transactionHistory.setCreatedAt(LocalDateTime.now());
                transactionHistory.setType("Payment for paymentFee ");
                transactionHistory.setStatus("PAYMENT_SUCCESS");
                transactionHistoryRepository.save(transactionHistory);
            }
            case 3 -> { //  Hoàn tiền
                TransactionHistory transactionHistory = new TransactionHistory();
                transactionHistory.setUser(order.getCustomer());
                transactionHistory.setAmount(payment.getAmount());
                transactionHistory.setCreatedAt(LocalDateTime.now());
                transactionHistory.setType("Refund for order ");
                transactionHistory.setStatus("REFUND");
                transactionHistoryRepository.save(transactionHistory);
            }
            default -> throw new IllegalArgumentException("Loại thanh toán không hợp lệ: " + type);
        }

        payment.setStatus(PaymentStatus.SUCCESS);
        paymentRepository.save(payment);
    }
}


