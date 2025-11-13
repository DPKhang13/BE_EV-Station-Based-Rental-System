package com.group6.Rental_Car.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * MoMo Payment Gateway Configuration
 * Hướng dẫn tích hợp: https://developers.momo.vn/
 */
@Configuration
@ConfigurationProperties(prefix = "momo")
@Data
public class MoMoConfig {

    /**
     * Partner Code do MoMo cấp
     */
    private String partnerCode;

    /**
     * Access Key do MoMo cấp
     */
    private String accessKey;

    /**
     * Secret Key để tạo chữ ký bảo mật
     */
    private String secretKey;

    /**
     * API endpoint của MoMo
     * - Test: https://test-payment.momo.vn/v2/gateway/api/create
     * - Production: https://payment.momo.vn/v2/gateway/api/create
     */
    private String endpoint;

    /**
     * URL để MoMo redirect sau khi thanh toán (người dùng thấy)
     */
    private String returnUrl;

    /**
     * URL để MoMo gọi IPN callback (server-to-server)
     */
    private String notifyUrl;

    /**
     * Loại request:
     * - captureWallet: Thanh toán qua ví MoMo
     * - payWithATM: Thanh toán qua ATM
     */
    private String requestType;
}

