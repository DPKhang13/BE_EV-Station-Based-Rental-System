package com.group6.Rental_Car.services.payment;

import com.group6.Rental_Car.config.VNpayConfig;
import com.group6.Rental_Car.dtos.payment.PaymentDto;
import com.group6.Rental_Car.dtos.payment.PaymentResponse;
import com.group6.Rental_Car.entities.User;
import com.group6.Rental_Car.exceptions.ResourceNotFoundException;
import com.group6.Rental_Car.repositories.RentalOrderRepository;
import com.group6.Rental_Car.repositories.UserRepository;
import com.group6.Rental_Car.utils.Utils;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
        public PaymentResponse createPaymentUrl(PaymentDto paymentDto, UUID userId){
            User account = userRepositorys.findById(userId)
                    .orElseThrow(()-> new ResourceNotFoundException("Account not found"));

            long amount = Integer.parseInt(paymentDto.getAmount()) * 100L;
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
            PaymentResponse response = new PaymentResponse();
            response.setPaymentUrl(paymentUrl);
            response.setMessage("Create VNPay payment successfully!");
            return response;
        }

}
