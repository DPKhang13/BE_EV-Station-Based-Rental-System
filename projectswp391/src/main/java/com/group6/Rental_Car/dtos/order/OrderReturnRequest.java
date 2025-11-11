package com.group6.Rental_Car.dtos.order;

import lombok.Data;

@Data
public class OrderReturnRequest {
    private Integer actualDays; // số ngày thuê thực tế (nếu người dùng muốn override)
}
