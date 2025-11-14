package com.group6.Rental_Car.services.transactionhistory;

import com.group6.Rental_Car.dtos.transactionhistory.TransactionHistoryResponse;

import java.util.List;
import java.util.UUID;

public interface TransactionHistoryService {
    List<TransactionHistoryResponse> getTransactionsByUser(UUID userId, String sortDirection);
    List<TransactionHistoryResponse> getTransactionsByUserId(UUID userId);
    List<TransactionHistoryResponse> getAllTransactions(String Phone);
    List<TransactionHistoryResponse> getAllTransactionCreatedAtDesc();
}
