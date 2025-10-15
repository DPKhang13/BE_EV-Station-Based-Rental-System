package com.group6.Rental_Car.config;

import com.group6.Rental_Car.utils.Utils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.text.SimpleDateFormat;
import java.util.*;

@Configuration
public class VNpayConfig {

    @Value("${VNP_TMNCODE}")
    private String VNP_TMNCODE;

    public Map<String, String> getVNPayConfig() {
        Map<String, String> vnpParamsMap = new HashMap<>();

        vnpParamsMap.put("vnp_Version", "2.1.0");
        vnpParamsMap.put("vnp_Command", "pay");
        vnpParamsMap.put("vnp_TmnCode", VNP_TMNCODE);
        vnpParamsMap.put("vnp_CurrCode", "VND");
        vnpParamsMap.put("vnp_TxnRef",  Utils.randomNumber(8));
        vnpParamsMap.put("vnp_OrderInfo", "Thanh toan don hang:" +  Utils.randomNumber(8));
        vnpParamsMap.put("vnp_OrderType", "other");
        vnpParamsMap.put("vnp_Locale", "vn");
        vnpParamsMap.put("vnp_ReturnUrl", "https://localhost:8080/api/payment/vnpay-callback");

        // ====== Thời gian tạo & hết hạn ======
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        String vnpCreateDate = formatter.format(calendar.getTime());
        vnpParamsMap.put("vnp_CreateDate", vnpCreateDate);

        calendar.add(Calendar.MINUTE, 15);
        String vnpExpireDate = formatter.format(calendar.getTime());
        vnpParamsMap.put("vnp_ExpireDate", vnpExpireDate);

        // Trả về map đầy đủ tham số gốc (chưa có vnp_TxnRef, vnp_Amount,...)
        return vnpParamsMap;
    }
}
