package com.group6.Rental_Car.dtos.payment;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MoMo Create Payment Request DTO
 * Dùng để gửi request tạo payment URL đến MoMo
 * Docs: https://developers.momo.vn/#/docs/en/aiov2/?id=payment-method
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MomoCreatePaymentRequest {

    @JsonProperty("partnerCode")
    private String partnerCode;

    @JsonProperty("accessKey")
    private String accessKey;

    @JsonProperty("requestId")
    private String requestId;

    @JsonProperty("amount")
    private String amount;

    @JsonProperty("orderId")
    private String orderId;

    @JsonProperty("orderInfo")
    private String orderInfo;

    @JsonProperty("redirectUrl")
    private String redirectUrl;

    @JsonProperty("ipnUrl")
    private String ipnUrl;

    @JsonProperty("requestType")
    private String requestType;

    @JsonProperty("extraData")
    private String extraData;

    @JsonProperty("lang")
    private String lang;

    @JsonProperty("signature")
    private String signature;
}

