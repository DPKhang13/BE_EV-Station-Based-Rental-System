package com.group6.Rental_Car.config;

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

        // Các thông tin bắt buộc theo tài liệu VNPay 2025
        vnpParamsMap.put("vnp_Version", "2.1.0");
        vnpParamsMap.put("vnp_Command", "pay");
        vnpParamsMap.put("vnp_TmnCode", VNP_TMNCODE);
        vnpParamsMap.put("vnp_CurrCode", "VND");
        vnpParamsMap.put("vnp_Locale", "vn"); // hoặc "en" nếu muốn giao diện tiếng Anh
        vnpParamsMap.put("vnp_OrderType", "other");

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
