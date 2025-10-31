package com.group6.Rental_Car.services.transactionhistory;


import com.group6.Rental_Car.dtos.transactionhistory.TransactionHistoryResponse;
import com.group6.Rental_Car.entities.TransactionHistory;
import com.group6.Rental_Car.repositories.TransactionHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionHistoryServiceImpl implements TransactionHistoryService {

    private final TransactionHistoryRepository transactionHistoryRepository;

    @Override
    public List<TransactionHistoryResponse> getTransactionsByUser(UUID userId, String sortDirection) {
        List<TransactionHistory> transactions;

        if (sortDirection.equalsIgnoreCase("asc")) {
            transactions = transactionHistoryRepository.findByUser_UserIdOrderByCreatedAtAsc(userId);
        } else {
            transactions = transactionHistoryRepository.findByUser_UserIdOrderByCreatedAtDesc(userId);
        }

        return transactions.stream()
                .map(TransactionHistoryResponse::fromEntity)
                .toList();
    }

    //   lấy danh sách transaction của userId (không cần sort)
    @Override
    public List<TransactionHistoryResponse> getTransactionsByUserId(UUID userId) {
        List<TransactionHistory> transactions = transactionHistoryRepository.findByUser_UserId(userId);

        return transactions.stream()
                .map(TransactionHistoryResponse::fromEntity)
                .toList();
    }
}