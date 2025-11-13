package com.group6.Rental_Car.dtos.payment;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MoMo Create Payment Response DTO
 * Response nhận được từ MoMo sau khi tạo payment
 * resultCode = 0: Success, khác 0: Failed
 * errorCode chỉ có khi có lỗi
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MomoCreatePaymentResponse {

    @JsonProperty("partnerCode")
    private String partnerCode;

    @JsonProperty("requestId")
    private String requestId;

    @JsonProperty("errorCode")
    private Integer errorCode;

    @JsonProperty("resultCode")
    private Integer resultCode;

    @JsonProperty("orderId")
    private String orderId;

    @JsonProperty("amount")
    private Long amount;

    @JsonProperty("responseTime")
    private Long responseTime;

    @JsonProperty("message")
    private String message;

    @JsonProperty("localMessage")
    private String localMessage;

    @JsonProperty("requestType")
    private String requestType;

    @JsonProperty("payUrl")
    private String payUrl;

    @JsonProperty("signature")
    private String signature;

    @JsonProperty("qrCodeUrl")
    private String qrCodeUrl;

    @JsonProperty("deeplink")
    private String deeplink;

    @JsonProperty("deeplinkWebInApp")
    private String deeplinkWebInApp;

    @JsonProperty("deeplinkMiniApp")
    private String deeplinkMiniApp;
}

