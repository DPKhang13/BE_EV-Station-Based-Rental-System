package com.group6.Rental_Car.services.transactionhistory;


import com.group6.Rental_Car.dtos.transactionhistory.TransactionHistoryResponse;
import com.group6.Rental_Car.entities.RentalOrder;
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
    private final UserRepository userRepository;
    private final RentalOrderRepository rentalOrderRepository;

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

    @Override
    public List<TransactionHistoryResponse> getAllTransactionCreatedAtDesc() {
        List<TransactionHistory> transactions = transactionHistoryRepository.findAllByOrderByCreatedAtDesc();

        return transactions.stream()
                .map(transaction -> {
                    TransactionHistoryResponse response = TransactionHistoryResponse.fromEntity(transaction);

                    // Thêm thông tin khách hàng
                    if (transaction.getUser() != null) {
                        User user = transaction.getUser();
                        response.setCustomerName(user.getFullName());
                        response.setCustomerPhone(user.getPhone());

                        // Lấy tất cả đơn hàng của user để tìm thông tin xe, trạm
                        List<RentalOrder> userOrders = rentalOrderRepository.findByCustomer_UserId(user.getUserId());

                        // Tìm order gần nhất với transaction (based on time)
                        userOrders.stream()
                                .filter(order -> order.getCreatedAt().isBefore(transaction.getCreatedAt())
                                        || order.getCreatedAt().isEqual(transaction.getCreatedAt()))
                                .max(Comparator.comparing(RentalOrder::getCreatedAt))
                                .ifPresent(order -> {
                                    // Lấy RENTAL detail để có thông tin xe và thời gian
                                    order.getDetails().stream()
                                            .filter(d -> "RENTAL".equalsIgnoreCase(d.getType()))
                                            .findFirst()
                                            .ifPresent(rentalDetail -> {
                                                // Thông tin xe
                                                var vehicle = rentalDetail.getVehicle();
                                                if (vehicle != null) {
                                                    response.setVehicleId(vehicle.getVehicleId());
                                                    response.setVehicleName(vehicle.getVehicleName());

                                                    // Thông tin trạm
                                                    var station = vehicle.getRentalStation();
                                                    if (station != null) {
                                                        response.setStationId(station.getStationId());
                                                        response.setStationName(station.getName());
                                                    }
                                                }

                                                // Thời gian thuê
                                                response.setRentalStartTime(rentalDetail.getStartTime());
                                                response.setRentalEndTime(rentalDetail.getEndTime());
                                            });
                                });
                    }

                    return response;
                })
                .toList();
    }
}