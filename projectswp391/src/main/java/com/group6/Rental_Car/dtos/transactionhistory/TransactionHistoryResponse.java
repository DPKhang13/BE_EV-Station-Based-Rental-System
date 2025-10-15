package com.group6.Rental_Car.dtos.transactionhistory;
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
    private String type;
    private LocalDateTime createdAt;

    public static TransactionHistoryResponse fromEntity(TransactionHistory entity) {
        return TransactionHistoryResponse.builder()
                .transactionId(entity.getTransactionId())
                .amount(entity.getAmount())
                .type(entity.getType())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
