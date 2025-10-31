package com.group6.Rental_Car.services.transactionhistory;


import com.group6.Rental_Car.dtos.transactionhistory.TransactionHistoryResponse;
import com.group6.Rental_Car.entities.TransactionHistory;
import com.group6.Rental_Car.entities.User;
import com.group6.Rental_Car.exceptions.ResourceNotFoundException;
import com.group6.Rental_Car.repositories.TransactionHistoryRepository;
import com.group6.Rental_Car.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionHistoryServiceImpl implements TransactionHistoryService {

    private final TransactionHistoryRepository transactionHistoryRepository;
    private final UserRepository userRepository;

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

    @Override
    public List<TransactionHistoryResponse> getAllTransactions(String phone) {
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với số điện thoại: " + phone));

        List<TransactionHistory> histories = transactionHistoryRepository
                .findByUser_PhoneOrderByCreatedAtDesc(phone);

        return histories.stream()
                .map(history -> TransactionHistoryResponse.builder()
                        .transactionId(history.getTransactionId())
                        .amount(history.getAmount())
                        .type(history.getType())
                        .status(history.getStatus())
                        .createdAt(history.getCreatedAt())
                        .build())
                .toList();
    }
}