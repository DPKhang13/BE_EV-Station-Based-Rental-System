package com.group6.Rental_Car.dtos.order;

import lombok.Data;

@Data
public class OrderUpdateRequest {

    private String status;        // cập nhật trạng thái đơn (PENDING, RENTAL, COMPLETED,...)
    private String couponCode;    // cập nhật hoặc gỡ mã giảm giá
    private Long newVehicleId;    // nếu muốn đổi xe cho khách (xe thay thế)
    private String note;          // ghi chú lý do đổi xe (optional)
}
