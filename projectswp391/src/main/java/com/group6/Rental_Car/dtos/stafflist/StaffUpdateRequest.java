package com.group6.Rental_Car.dtos.stafflist;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StaffUpdateRequest {

    @NotNull
    private String fullName;

    @NotNull
    @Email(message = "Email không hợp lệ")
    private String email;

    @NotNull
    private String phone;

    @Size(min = 6, message = "Mật khẩu phải có ít nhất 6 ký tự")
    private String password; // optional, nếu null thì không đổi

    @NotNull
    private Integer stationId;   // đổi trạm làm việc
}