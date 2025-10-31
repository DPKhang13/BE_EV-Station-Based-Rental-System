package com.group6.Rental_Car.repositories;

import com.group6.Rental_Car.entities.TransactionHistory;
import com.group6.Rental_Car.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionHistoryRepository extends JpaRepository<TransactionHistory, UUID> {
    List<TransactionHistory> findByUser_UserId(UUID userId);
    // Lấy tất cả transaction của user và sắp xếp theo created_at giảm dần (mới nhất trước)
    List<TransactionHistory> findByUser_UserIdOrderByCreatedAtDesc(UUID userId);
    // Lấy tất cả transaction của user và sắp xếp theo created_at giảm dần (mới nhất trước)
    List<TransactionHistory> findByUser_UserIdOrderByCreatedAtAsc(UUID userId);
    List<TransactionHistory> findByUser_PhoneOrderByCreatedAtDesc(String phone);
    List<TransactionHistory> findAllByOrderByCreatedAtDesc();
}
