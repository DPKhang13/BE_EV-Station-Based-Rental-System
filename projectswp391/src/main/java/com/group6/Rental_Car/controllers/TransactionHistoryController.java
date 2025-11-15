package com.group6.Rental_Car.controllers;

import com.group6.Rental_Car.dtos.transactionhistory.TransactionHistoryResponse;
import com.group6.Rental_Car.dtos.transactionhistory.TransactionResponse;
import com.group6.Rental_Car.services.transactionhistory.TransactionHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionHistoryController {

    private final TransactionHistoryService transactionHistoryService;

    @GetMapping("/sort/{userId}")
    public List<TransactionHistoryResponse> getTransactionsByUser(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "desc") String sort
    ) {
        return transactionHistoryService.getTransactionsByUser(userId, sort);
    }

    @GetMapping("/search/list/{userId}")
    public List<TransactionHistoryResponse> getTransactionListByUserId(@PathVariable UUID userId) {
        return transactionHistoryService.getTransactionsByUserId(userId);
    }
    @GetMapping("/user/{phone}")
    public ResponseEntity<List<TransactionResponse>> getAllTransactionsByPhone(@PathVariable String phone) {
        return ResponseEntity.ok(transactionHistoryService.getAllTransactions(phone));
    }
    @GetMapping("/all")
    public ResponseEntity<List<TransactionResponse>> getAllTransactionsSorted() {
        return ResponseEntity.ok(transactionHistoryService.getAllTransactionCreatedAtDesc());
    }
}