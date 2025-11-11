package com.group6.Rental_Car.dtos.order;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter

public class OrderReturnRequest {
    private Integer actualHours;
}
