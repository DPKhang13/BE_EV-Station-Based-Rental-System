package com.group6.Rental_Car.dtos.verifyfile;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderVerificationResponse {

    private UUID userId;            // ID khách hàng
    private UUID orderId;           // ID đơn thuê
    private String customerName;    // Tên khách hàng
    private String phone;           // SĐT khách hàng

    // Thông tin xe
    private Long vehicleId;
    private String vehicleName;
    private String plateNumber;

    // Thời gian thuê (từ chi tiết đơn)
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    // Thông tin tài chính
    private BigDecimal totalPrice;      // Tổng tiền của order
    private BigDecimal totalServices;   // Tổng tiền dịch vụ (nếu có)
    private BigDecimal remainingAmount; // Số tiền còn lại cần thanh toán

    // Trạng thái
    private String status;        // pending | confirmed | rental | done ...
    private String userStatus;    // ACTIVE | PENDING | BANNED ...
    private Integer stationId;    // ID trạm thuê (nếu có)
}
