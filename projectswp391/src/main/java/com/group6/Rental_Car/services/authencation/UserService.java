package com.group6.Rental_Car.services.authencation;

import com.group6.Rental_Car.dtos.loginpage.AccountDto;
import com.group6.Rental_Car.dtos.loginpage.AccountDtoResponse;
import com.group6.Rental_Car.dtos.loginpage.RegisterAccountDto;
import com.group6.Rental_Car.dtos.otpverify.OtpRequest;
import com.group6.Rental_Car.dtos.verifyfile.UserVerificationResponse;
import com.group6.Rental_Car.utils.JwtUserDetails;

import java.util.UUID;

public interface UserService  {
    public AccountDtoResponse registerByEmail(RegisterAccountDto account);

    public AccountDtoResponse loginByEmail(AccountDto account);

    public JwtUserDetails getAccountDetails(UUID userId);

    public AccountDtoResponse verifyOtp(String inputOtp, String email);

    public void forgetPassword(String email);

    public boolean verifyForgetPassword(String inputOtp, String email);

    public AccountDtoResponse resetPassword(AccountDto accountDto, String inputOtp);

    public UserVerificationResponse verifyUserProfile(UUID userId);

}
