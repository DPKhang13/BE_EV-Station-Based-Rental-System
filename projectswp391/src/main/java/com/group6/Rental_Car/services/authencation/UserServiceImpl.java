package com.group6.Rental_Car.services.authencation;

import com.group6.Rental_Car.dtos.LoginPage.LoginRequest;
import com.group6.Rental_Car.dtos.LoginPage.RegisterRequest;
import com.group6.Rental_Car.dtos.LoginPage.RegisterResponse;
import com.group6.Rental_Car.dtos.OtpVerify.OtpRequest;
import com.group6.Rental_Car.entities.User;
import com.group6.Rental_Car.enums.Role;
import com.group6.Rental_Car.enums.UserStatus;
import com.group6.Rental_Car.repositories.UserRepository;
import com.group6.Rental_Car.services.otpmailsender.OtpMailService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final OtpMailService otpMailService;
    private final PasswordEncoder passwordEncoder;

    @Override
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Email đã tồn tại!");
        }

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.customer)
                .status(UserStatus.NEED_OTP)
                .build();

        userRepository.save(user);
        otpMailService.generateAndSendOtp(user.getEmail());

        return mapToResponse(user);
    }

    @Override
    public RegisterResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Email không tồn tại!"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Sai mật khẩu!");
        }

        if (user.getStatus() == UserStatus.NEED_OTP) {
            otpMailService.generateAndSendOtp(user.getEmail());
            throw new RuntimeException("Cần xác thực OTP trước khi đăng nhập!");
        }

        return mapToResponse(user);
    }

    @Override
    public RegisterResponse verifyOtp(OtpRequest request) {
        if (!otpMailService.validateOtp(request.getOtp())) {
            throw new RuntimeException("OTP không hợp lệ hoặc đã hết hạn!");
        }

        String email = otpMailService.getEmailByOtp(request.getOtp());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user!"));

        if (user.getStatus() == UserStatus.NEED_OTP) {
            user.setStatus(UserStatus.ACTIVE);
            userRepository.save(user);
        }

        otpMailService.clearOtp(request.getOtp());
        return mapToResponse(user);
    }

    @Override
    public void logout(UUID userId) {
        // Controller sẽ clear cookie
    }

    @Override
    public RegisterResponse getUserDetails(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user!"));
        return mapToResponse(user);
    }

    // ------- Helper -------
    private RegisterResponse mapToResponse(User user) {
        return RegisterResponse.builder()
                .userId(user.getUserId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole())
                .status(user.getStatus())
                .build();
    }
}
