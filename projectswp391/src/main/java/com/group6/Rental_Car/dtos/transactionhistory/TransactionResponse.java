package com.group6.Rental_Car.dtos.transactionhistory;

import com.group6.Rental_Car.entities.*;
import lombok.*;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionResponse {

    // transaction info
    private UUID transactionId;
    private BigDecimal amount;
    private String type;
    private String status;
    private LocalDateTime createdAt;

    // user
    private String customerName;
    private String customerPhone;

    // vehicle
    private Long vehicleId;
    private String vehicleName;

    // station
    private Integer stationId;
    private String stationName;

    // rental timeline
    private LocalDateTime rentalStartTime;
    private LocalDateTime rentalEndTime;
    private LocalDateTime actualReturnTime;

    public static TransactionResponse fromUser(TransactionHistory history) {

        User u = history.getUser();

        if (u == null) {
            return TransactionResponse.builder()
                    .transactionId(history.getTransactionId())
                    .amount(history.getAmount())
                    .status(history.getStatus())
                    .type(history.getType())
                    .createdAt(history.getCreatedAt())
                    .build();
        }

        RentalOrder order = getLatestOrder(u);
        RentalOrderDetail detail = getLatestDetailFromOrder(order);
        Vehicle vehicle = detail != null ? detail.getVehicle() : null;

        return TransactionResponse.builder()
                .transactionId(history.getTransactionId())
                .amount(history.getAmount())
                .status(history.getStatus())
                .type(history.getType())
                .createdAt(history.getCreatedAt())

                .customerName(u.getFullName())
                .customerPhone(u.getPhone())

                .vehicleId(vehicle != null ? vehicle.getVehicleId() : null)
                .vehicleName(vehicle != null ? vehicle.getVehicleName() : null)

                .stationId(vehicle != null ? vehicle.getRentalStation().getStationId() : null)
                .stationName(vehicle != null ? vehicle.getRentalStation().getName() : null)

                .rentalStartTime(detail != null ? detail.getStartTime() : null)
                .rentalEndTime(detail != null ? detail.getEndTime() : null)
                .actualReturnTime(order != null ? order.getActualReturnTime() : null)

                .build();
    }


    private static RentalOrder getLatestOrder(User user) {
        // ƯU TIÊN: order đã trả xe
        return user.getRentalOrders()
                .stream()
                .filter(o -> o.getActualReturnTime() != null)
                .max(Comparator.comparing(RentalOrder::getActualReturnTime))
                .orElse(
                        // FALLBACK: nếu chưa có cái nào trả xe → lấy order mới nhất
                        user.getRentalOrders()
                                .stream()
                                .max(Comparator.comparing(RentalOrder::getCreatedAt))
                                .orElse(null)
                );
    }

    private static RentalOrderDetail getLatestDetailFromOrder(RentalOrder order) {
        if (order == null) return null;

        return order.getDetails()
                .stream()
                .filter(d -> "RENTAL".equalsIgnoreCase(d.getType()))
                .max(Comparator.comparing(RentalOrderDetail::getStartTime))
                .orElse(null);
    }



    private static RentalOrderDetail getLatestDetail(User user) {
        return user.getRentalOrders()
                .stream()
                .flatMap(o -> o.getDetails().stream())
                .filter(d -> "RENTAL".equalsIgnoreCase(d.getType()))
                .max(Comparator.comparing(RentalOrderDetail::getStartTime))
                .orElse(null);
    }

}
