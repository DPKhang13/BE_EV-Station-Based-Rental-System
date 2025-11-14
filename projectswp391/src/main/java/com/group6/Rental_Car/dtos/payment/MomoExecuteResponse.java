package com.group6.Rental_Car.dtos.payment;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MoMo IPN Callback Response DTO
 * Response nhận được từ MoMo callback (IPN)
 * resultCode = 0: Success, khác 0: Failed
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MomoExecuteResponse {

    @JsonProperty("partnerCode")
    private String partnerCode;

    @JsonProperty("orderId")
    private String orderId;

    @JsonProperty("requestId")
    private String requestId;

    @JsonProperty("amount")
    private Long amount;

    @JsonProperty("orderInfo")
    private String orderInfo;

    @JsonProperty("orderType")
    private String orderType;

    @JsonProperty("transId")
    private Long transId;

    @JsonProperty("resultCode")
    private Integer resultCode;

    @JsonProperty("message")
    private String message;

    @JsonProperty("payType")
    private String payType;

    @JsonProperty("responseTime")
    private Long responseTime;

    @JsonProperty("extraData")
    private String extraData;

    @JsonProperty("signature")
    private String signature;
}

