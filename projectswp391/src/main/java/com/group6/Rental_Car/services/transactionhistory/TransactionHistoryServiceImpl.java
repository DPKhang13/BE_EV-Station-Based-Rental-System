package com.group6.Rental_Car.services.transactionhistory;


import com.group6.Rental_Car.dtos.transactionhistory.TransactionHistoryResponse;
import com.group6.Rental_Car.dtos.transactionhistory.TransactionResponse;
import com.group6.Rental_Car.entities.RentalOrder;
import com.group6.Rental_Car.entities.RentalOrderDetail;
import com.group6.Rental_Car.entities.TransactionHistory;
import com.group6.Rental_Car.entities.User;
import com.group6.Rental_Car.exceptions.ResourceNotFoundException;
import com.group6.Rental_Car.repositories.RentalOrderRepository;
import com.group6.Rental_Car.repositories.TransactionHistoryRepository;
import com.group6.Rental_Car.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionHistoryServiceImpl implements TransactionHistoryService {

    private final TransactionHistoryRepository transactionHistoryRepository;

    @Override
    public List<TransactionHistoryResponse> getTransactionsByUser(UUID userId, String sortDirection) {

        List<TransactionHistory> transactions =
                sortDirection.equalsIgnoreCase("asc")
                        ? transactionHistoryRepository.findByUser_UserIdOrderByCreatedAtAsc(userId)
                        : transactionHistoryRepository.findByUser_UserIdOrderByCreatedAtDesc(userId);

        return transactions.stream()
                .map(TransactionHistoryResponse::fromEntity)
                .toList();
    }

    @Override
    public List<TransactionHistoryResponse> getTransactionsByUserId(UUID userId) {
        return transactionHistoryRepository.findByUser_UserId(userId)
                .stream()
                .map(TransactionHistoryResponse::fromEntity)
                .toList();
    }

    // ==========================================================
    // 🔥 API lấy transaction theo phone → trả TransactionResponse
    // ==========================================================
    @Override
    public List<TransactionResponse> getAllTransactions(String phone) {

        List<TransactionHistory> histories =
                transactionHistoryRepository.findByUser_PhoneOrderByCreatedAtDesc(phone);

        return histories.stream()
                .map(TransactionResponse::fromUser)  // DONE ✔
                .toList();
    }


    @Override
    public List<TransactionResponse> getAllTransactionCreatedAtDesc() {

        List<TransactionHistory> histories =
                transactionHistoryRepository.findAllByOrderByCreatedAtDesc();

        return histories.stream()
                .map(TransactionResponse::fromUser)  // dùng luôn từ helper trong DTO
                .toList();
    }
}

