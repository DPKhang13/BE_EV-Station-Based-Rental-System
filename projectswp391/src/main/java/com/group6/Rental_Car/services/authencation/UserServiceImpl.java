package com.group6.Rental_Car.services.authencation;

import com.group6.Rental_Car.dtos.loginpage.AccountDto;
import com.group6.Rental_Car.dtos.loginpage.AccountDtoResponse;
import com.group6.Rental_Car.dtos.loginpage.RegisterAccountDto;
import com.group6.Rental_Car.dtos.otpverify.OtpRequest;
import com.group6.Rental_Car.dtos.verifyfile.UserVerificationResponse;
import com.group6.Rental_Car.entities.User;
import com.group6.Rental_Car.enums.Role;
import com.group6.Rental_Car.enums.UserStatus;
import com.group6.Rental_Car.exceptions.*;
import com.group6.Rental_Car.repositories.UserRepository;
import com.group6.Rental_Car.services.otpmailsender.OtpMailService;
import com.group6.Rental_Car.utils.JwtUserDetails;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final OtpMailService otpMailService;
    private final PasswordEncoder passwordEncoder;
    private final ModelMapper modelMapper;

    // ------- Helper -------
    private AccountDtoResponse mapToResponse(User user) {
        return AccountDtoResponse.builder()
                .userId(user.getUserId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole())
                .status(user.getStatus())
                .stationId(
                        user.getRentalStation() != null
                                ? user.getRentalStation().getStationId()
                                : null
                )
                .build();
    }

    // ========== REGISTER ==========
    @Override
    public AccountDtoResponse registerByEmail(RegisterAccountDto account) {
        if (userRepository.existsByEmail(account.getEmail().toLowerCase())) {
            throw new EmailAlreadyExistsException("Email đã tồn tại: " + account.getEmail());
        }

        String otp = otpMailService.generateAndSendOtp(account.getEmail());

        User user = modelMapper.map(account, User.class);
        user.setEmail(account.getEmail().toLowerCase());
        user.setPassword(passwordEncoder.encode(account.getPassword()));
        user.setPhone(account.getPhone());
        user.setRole(Role.customer);
        user.setStatus(UserStatus.NEED_OTP);
        user.setRentalStation(null);
        userRepository.save(user);

        return mapToResponse(user);
    }

    // ========== LOGIN ==========
    @Override
    public AccountDtoResponse loginByEmail(AccountDto account) {
        User user = userRepository.findByEmail(account.getEmail().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với email: " + account.getEmail()));

        if (!passwordEncoder.matches(account.getPassword(), user.getPassword())) {
            throw new InvalidPasswordException("Sai mật khẩu");
        }

        if (user.getStatus() != UserStatus.ACTIVE && user.getStatus() != UserStatus.ACTIVE_PENDING_VERIFICATION) {
            throw new RuntimeException("Tài khoản chưa được kích hoạt hoặc bị khóa");
        }

        return mapToResponse(user);
    }

    // ========== GET ACCOUNT DETAILS ==========
    @Override
    public JwtUserDetails getAccountDetails(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy userId: " + userId));

        return JwtUserDetails.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }

    // ========== VERIFY OTP ==========
    @Override
    public AccountDtoResponse verifyOtp(String inputOtp, String email) {
        if (!otpMailService.validateOtp(email, inputOtp)) { // ✅ đổi vị trí
            throw new OtpValidationException("Mã OTP không hợp lệ hoặc đã hết hạn");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy user: " + email));

        user.setStatus(UserStatus.ACTIVE_PENDING_VERIFICATION);
        userRepository.save(user);

        otpMailService.clearOtp(email); // ✅ dùng email làm key

        return mapToResponse(user);
    }

    // ========== FORGOT PASSWORD ==========
    @Override
    public void forgetPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy user: " + email));

        // Gửi OTP mới
        otpMailService.generateAndSendOtp(email);
    }

    // ========== VERIFY FORGOT PASSWORD OTP ==========
    @Override
    public boolean verifyForgetPassword(String inputOtp, String email) {
        if (!otpMailService.validateOtp(inputOtp,email)) {
            throw new OtpValidationException("Mã OTP không hợp lệ hoặc đã hết hạn");
        }

        String emailFromOtp = otpMailService.getEmailByOtp(inputOtp);
        if (emailFromOtp == null || !emailFromOtp.equalsIgnoreCase(email)) {
            return false;
        }

        return true;
    }

    // ========== RESET PASSWORD ==========
    @Override
    public AccountDtoResponse resetPassword(AccountDto accountDto, String inputOtp) {
        String email = otpMailService.getEmailByOtp(inputOtp);
        if (email == null) {
            throw new OtpValidationException("OTP không hợp lệ hoặc đã hết hạn");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy user: " + email));

        user.setPassword(passwordEncoder.encode(accountDto.getPassword()));
        userRepository.save(user);
        otpMailService.clearOtp(inputOtp);

        return mapToResponse(user);
    }

    @Override
    public UserVerificationResponse verifyUserProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
        if (user.getStatus() == UserStatus.ACTIVE) {
            throw new BadRequestException("Hồ sơ này đã được xác thực rồi.");
        }

        if (user.getStatus() != UserStatus.ACTIVE_PENDING_VERIFICATION) {
            throw new BadRequestException("Không thể xác thực hồ sơ do trạng thái không hợp lệ: " + user.getStatus());
        }

        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);


        return UserVerificationResponse.builder()
                .userId(user.getUserId())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .email(user.getEmail())
                .status(user.getStatus().name())
                .role(user.getRole().name())
                .build();
    }
}
