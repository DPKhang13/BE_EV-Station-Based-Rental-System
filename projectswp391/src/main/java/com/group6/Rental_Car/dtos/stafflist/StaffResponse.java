package com.group6.Rental_Car.dtos.stafflist;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffResponse {

    private Object staffId;      // để UserId (UUID) không lỗi
    private String staffName;
    private String staffEmail;
    private String staffPhone;   // <---- ÔNG ĐANG THIẾU CÁI NÀY NÊN BỊ LỖI
    private String role;
    private String stationName;
    private Long pickupCount;
    private Long returnCount;
    private String status;
}
