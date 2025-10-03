package com.group6.Rental_Car.services.authencation;

import com.group6.Rental_Car.dtos.LoginPage.LoginRequest;
import com.group6.Rental_Car.dtos.LoginPage.RegisterRequest;
import com.group6.Rental_Car.dtos.LoginPage.RegisterResponse;
import com.group6.Rental_Car.dtos.OtpVerify.OtpRequest;
import com.group6.Rental_Car.entities.User;
import com.group6.Rental_Car.enums.Role;
import com.group6.Rental_Car.enums.UserStatus;
import com.group6.Rental_Car.exceptions.EmailAlreadyExistsException;
import com.group6.Rental_Car.exceptions.InvalidPasswordException;
import com.group6.Rental_Car.exceptions.OtpValidationException;
import com.group6.Rental_Car.exceptions.ResourceNotFoundException;
import com.group6.Rental_Car.repositories.UserRepository;
import com.group6.Rental_Car.services.otpmailsender.OtpMailService;
import lombok.RequiredArgsConstructor;
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
            throw new EmailAlreadyExistsException("Email đã tồn tại!");
        }

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
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
                .orElseThrow(() -> new ResourceNotFoundException("Email không tồn tại!"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidPasswordException("Sai mật khẩu!");
        }

        if (user.getStatus() == UserStatus.NEED_OTP) {
            otpMailService.generateAndSendOtp(user.getEmail());
            throw new OtpValidationException("Cần xác thực OTP trước khi đăng nhập!");
        }

        return mapToResponse(user);
    }

    @Override
    public RegisterResponse verifyOtp(OtpRequest request) {
        if (!otpMailService.validateOtp(request.getOtp())) {
            throw new OtpValidationException("OTP không hợp lệ hoặc đã hết hạn!");
        }

        String email = otpMailService.getEmailByOtp(request.getOtp());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy user!"));

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
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy user!"));
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
