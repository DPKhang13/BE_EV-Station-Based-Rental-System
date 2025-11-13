package com.group6.Rental_Car.controllers;

import com.group6.Rental_Car.dtos.loginpage.AccountDto;
import com.group6.Rental_Car.dtos.loginpage.AccountDtoResponse;
import com.group6.Rental_Car.dtos.loginpage.RegisterAccountDto;
import com.group6.Rental_Car.dtos.verifyfile.UserVerificationResponse;
import com.group6.Rental_Car.services.authencation.UserService;
import com.group6.Rental_Car.utils.JwtUserDetails;
import com.group6.Rental_Car.utils.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication API",
        description = "Các endpoint để quản lý tài khoản: đăng ký, đăng nhập, OTP, refresh token, quên mật khẩu")
public class AuthenticationController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    private final long accessTokenAge;
    private final long refreshTokenAge;

    public AuthenticationController(@Value("${JWT_ACCESSEXPIRATION}") long accessTokenAge,
                                    @Value("${JWT_REFRESHEXPIRATION}") long refreshTokenAge) {
        this.accessTokenAge = accessTokenAge;
        this.refreshTokenAge = refreshTokenAge;
    }

    // ---------- REGISTER ----------
    @PostMapping("/register")
    @Operation(summary = "Đăng ký tài khoản bằng email", description = "Tạo tài khoản mới và gửi OTP xác minh")
    public ResponseEntity<?> registerByEmail(@Valid @RequestBody RegisterAccountDto account) {
        AccountDtoResponse response = userService.registerByEmail(account);
        return ResponseEntity.ok(response);
    }

    // ---------- LOGIN ----------
    @PostMapping("/login")
    @Operation(summary = "Đăng nhập tài khoản", description = "Kiểm tra email, mật khẩu, trả về AccessToken và RefreshToken")
    public ResponseEntity<?> loginByEmail(@Valid @RequestBody AccountDto account) {
        AccountDtoResponse response = userService.loginByEmail(account);

        JwtUserDetails jwtUserDetails = JwtUserDetails.builder()
                .userId(response.getUserId())
                .role(response.getRole().toString())
                .build();

        ResponseCookie refreshCookie = ResponseCookie.from("RefreshToken", jwtUtil.generateRefreshToken(response.getUserId()))
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .maxAge(refreshTokenAge / 1000)
                .path("/")
                .build();

        ResponseCookie accessCookie = ResponseCookie.from("AccessToken", jwtUtil.generateAccessToken(jwtUserDetails))
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .maxAge(accessTokenAge / 1000)
                .path("/")
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString(), accessCookie.toString())
                .body(response);
    }

    // ---------- LOGOUT ----------
    @PostMapping("/logout")
    @Operation(summary = "Đăng xuất", description = "Xóa token khỏi cookie")
    public ResponseEntity<?> logout() {
        ResponseCookie refreshCookie = ResponseCookie.from("RefreshToken", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .maxAge(0)
                .path("/")
                .build();

        ResponseCookie accessCookie = ResponseCookie.from("AccessToken", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .maxAge(0)
                .path("/")
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString(), accessCookie.toString())
                .build();
    }

    // ---------- REFRESH TOKEN ----------
    @PostMapping("/refresh")
    @Operation(summary = "Làm mới AccessToken", description = "Dùng RefreshToken để tạo AccessToken mới")
    public ResponseEntity<?> refreshAccessToken(@CookieValue(name = "RefreshToken") String refreshToken) {
        if (jwtUtil.validateRefreshToken(refreshToken)) {
            UUID accountId = jwtUtil.extractUserIdFromRefresh(refreshToken);
            JwtUserDetails userDetails = userService.getAccountDetails(accountId);

            ResponseCookie accessCookie = ResponseCookie.from("AccessToken", jwtUtil.generateAccessToken(userDetails))
                    .httpOnly(true)
                    .secure(true)
                    .sameSite("None")
                    .maxAge(accessTokenAge / 1000)
                    .path("/")
                    .build();

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                    .build();
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    // ---------- VERIFY OTP ----------
    @PostMapping("/verify")
    @Operation(summary = "Xác minh OTP khi đăng ký", description = "Xác thực OTP gửi qua email và kích hoạt tài khoản")
    public ResponseEntity<?> verifyOtp(@RequestParam String inputOtp,
                                       @RequestParam String email) {
        AccountDtoResponse response = userService.verifyOtp(inputOtp, email);

        JwtUserDetails jwtUserDetails = JwtUserDetails.builder()
                .userId(response.getUserId())
                .role(response.getRole().toString())
                .build();

        ResponseCookie refreshCookie = ResponseCookie.from("RefreshToken", jwtUtil.generateRefreshToken(response.getUserId()))
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .maxAge(refreshTokenAge / 1000)
                .path("/")
                .build();

        ResponseCookie accessCookie = ResponseCookie.from("AccessToken", jwtUtil.generateAccessToken(jwtUserDetails))
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .maxAge(accessTokenAge / 1000)
                .path("/")
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString(), accessCookie.toString())
                .body(response);
    }

    // ---------- FORGET PASSWORD ----------
    @PostMapping("/account/forget")
    @Operation(summary = "Gửi OTP quên mật khẩu", description = "Gửi mã OTP tới email để khôi phục mật khẩu")
    public ResponseEntity<?> forgetPassword(@RequestParam String email) {
        userService.forgetPassword(email);
        return ResponseEntity.ok("OTP đã được gửi tới email của bạn");
    }

    // ---------- VERIFY FORGET PASSWORD ----------
    @PostMapping("/account/verify")
    @Operation(summary = "Xác thực OTP quên mật khẩu", description = "Kiểm tra mã OTP trước khi đặt lại mật khẩu")
    public ResponseEntity<?> verifyForgetPassword(@RequestParam String email,
                                                  @RequestParam String inputOtp) {
        boolean result = userService.verifyForgetPassword(inputOtp, email);
        return ResponseEntity.ok(result);
    }

    // ---------- RESET PASSWORD ----------
    @PostMapping("/account/reset-password")
    @Operation(summary = "Đặt lại mật khẩu", description = "Đặt lại mật khẩu sau khi xác minh OTP")
    public ResponseEntity<?> resetPassword(@RequestBody AccountDto accountDto,
                                           @RequestParam String inputOtp) {
        AccountDtoResponse response = userService.resetPassword(accountDto, inputOtp);
        return ResponseEntity.ok(response);
    }
    @PutMapping("/verify-profile/{userId}")
    public ResponseEntity<UserVerificationResponse> verifyUserProfile(@PathVariable UUID userId) {
        UserVerificationResponse response = userService.verifyUserProfile(userId);
        return ResponseEntity.ok(response);
    }
    @GetMapping("/verify-profile/pending")
    public ResponseEntity<List<UserVerificationResponse>> getPendingVerificationUsers() {
        List<UserVerificationResponse> pendingUsers = userService.getPendingVerificationUsers();
        return ResponseEntity.ok(pendingUsers);
    }
    @GetMapping("/getAll/customer")
    public ResponseEntity<List<AccountDtoResponse>> getAllCustomers() {
        userService.getAllCustomer();
        return ResponseEntity.ok(userService.getAllCustomer());
    }
    @GetMapping("/getUser/{userId}")
    public ResponseEntity<AccountDtoResponse> getUserById(@PathVariable UUID userId)
    {
        AccountDtoResponse response = userService.getUserById(userId);
        return ResponseEntity.ok(response);
    }
}