package com.group6.Rental_Car.dtos.transactionhistory;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.group6.Rental_Car.entities.TransactionHistory;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionHistoryResponse {
    private UUID transactionId;
    private BigDecimal amount;
    private String status;
    private String type;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    public static TransactionHistoryResponse fromEntity(TransactionHistory entity) {
        return TransactionHistoryResponse.builder()
                .transactionId(entity.getTransactionId())
                .amount(entity.getAmount())
                .status(entity.getStatus())
                .type(entity.getType())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
