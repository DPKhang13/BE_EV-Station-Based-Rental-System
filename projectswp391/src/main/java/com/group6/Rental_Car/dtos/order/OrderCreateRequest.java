package com.group6.Rental_Car.dtos.order;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderCreateRequest {

    private UUID customerId;          // ID khách hàng (backend có thể override từ JWT)
    private Long vehicleId;           // Xe được chọn

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;  // Thời điểm bắt đầu thuê

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;    // Thời điểm kết thúc thuê

    private String couponCode;        // Mã giảm giá (nếu có)
    private boolean holiday;          // Có phải ngày lễ hay không (áp dụng holidayPrice)
}
