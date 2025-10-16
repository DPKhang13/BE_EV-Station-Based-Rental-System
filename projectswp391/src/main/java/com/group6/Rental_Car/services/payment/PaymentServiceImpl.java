package com.group6.Rental_Car.services.payment;

import com.group6.Rental_Car.config.VNpayConfig;
import com.group6.Rental_Car.dtos.payment.PaymentDto;
import com.group6.Rental_Car.dtos.payment.PaymentResponse;
import com.group6.Rental_Car.entities.Payment;
import com.group6.Rental_Car.entities.User;
import com.group6.Rental_Car.enums.PaymentStatus;
import com.group6.Rental_Car.exceptions.ResourceNotFoundException;
import com.group6.Rental_Car.repositories.PaymentRepository;
import com.group6.Rental_Car.repositories.RentalOrderRepository;
import com.group6.Rental_Car.repositories.UserRepository;
import com.group6.Rental_Car.utils.Utils;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
                    .status(PaymentStatus.PENDING)
                    .build();
            paymentRepository.save(payment);
            String bankCode = null;
            Map<String, String> vnpParamsMap = vnpayConfig.getVNPayConfig();
            vnpParamsMap.put("vnp_Amount", String.valueOf(amount));
            if (bankCode != null && !bankCode.isEmpty()) {
                vnpParamsMap.put("vnp_BankCode", bankCode);
            }
            vnpParamsMap.put("vnp_IpAddr", paymentDto.getClientIp());
            //build query url
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
                    .build();
        }
}


