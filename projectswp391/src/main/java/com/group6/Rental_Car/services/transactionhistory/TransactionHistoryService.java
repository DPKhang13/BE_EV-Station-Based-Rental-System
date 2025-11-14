package com.group6.Rental_Car.services.transactionhistory;

import com.group6.Rental_Car.dtos.transactionhistory.TransactionHistoryResponse;
import com.group6.Rental_Car.dtos.transactionhistory.TransactionResponse;

import java.util.List;
import java.util.UUID;

public interface TransactionHistoryService {
    List<TransactionHistoryResponse> getTransactionsByUser(UUID userId, String sortDirection);
    List<TransactionHistoryResponse> getTransactionsByUserId(UUID userId);
    List<TransactionResponse> getAllTransactions(String Phone);
    List<TransactionResponse> getAllTransactionCreatedAtDesc();
}
