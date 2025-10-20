package com.group6.Rental_Car.controllers;

import com.group6.Rental_Car.dtos.loginpage.LoginRequest;
import com.group6.Rental_Car.dtos.loginpage.RegisterRequest;
import com.group6.Rental_Car.dtos.loginpage.RegisterResponse;
import com.group6.Rental_Car.dtos.otpverify.OtpRequest;
import com.group6.Rental_Car.services.authencation.UserService;
import com.group6.Rental_Car.utils.JwtUserDetails;
import com.group6.Rental_Car.utils.JwtUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication API",
        description = "Các endpoint cho register, login, verify OTP, logout, refresh token")
public class AuthenticationController {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final long accessTokenAge;
    private final long refreshTokenAge;

    public AuthenticationController(UserService userService,
                                    JwtUtil jwtUtil,
                                    @Value("${JWT_ACCESSEXPIRATION}") long accessTokenAge,
                                    @Value("${JWT_REFRESHEXPIRATION}") long refreshTokenAge) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.accessTokenAge = accessTokenAge;
        this.refreshTokenAge = refreshTokenAge;
    }

    // ---------- REGISTER ----------
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse accountDtoResponse = userService.register(request);

        JwtUserDetails jwtUserDetails = JwtUserDetails.builder()
                .userId(accountDtoResponse.getUserId())
                .email(accountDtoResponse.getEmail())
                .role(accountDtoResponse.getRole().name()) // convert Enum -> String
                .build();

        ResponseCookie refreshCookie = ResponseCookie.from("RefreshToken", jwtUtil.generateRefreshToken(accountDtoResponse.getUserId()))
                .httpOnly(true).secure(false).sameSite("None")
                .maxAge(refreshTokenAge / 1000).path("/").build();

        ResponseCookie accessCookie = ResponseCookie.from("AccessToken", jwtUtil.generateAccessToken(jwtUserDetails))
                .httpOnly(true).secure(false).sameSite("None")
                .maxAge(accessTokenAge / 1000).path("/").build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString(), accessCookie.toString())
                .body(accountDtoResponse);
    }

    // ---------- LOGIN ----------
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        RegisterResponse accountDtoResponse = userService.login(request);

        JwtUserDetails jwtUserDetails = JwtUserDetails.builder()
                .userId(accountDtoResponse.getUserId())
                .email(accountDtoResponse.getEmail())
                .role(accountDtoResponse.getRole().name())
                .build();

        ResponseCookie refreshCookie = ResponseCookie.from("RefreshToken", jwtUtil.generateRefreshToken(accountDtoResponse.getUserId()))
                .httpOnly(true).secure(false).sameSite("None")
                .maxAge(refreshTokenAge / 1000).path("/").build();

        ResponseCookie accessCookie = ResponseCookie.from("AccessToken", jwtUtil.generateAccessToken(jwtUserDetails))
                .httpOnly(true).secure(false).sameSite("None")
                .maxAge(accessTokenAge / 1000).path("/").build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString(), accessCookie.toString())
                .body(accountDtoResponse);
    }

    // ---------- VERIFY OTP ----------
    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody OtpRequest request) {
        RegisterResponse userResp = userService.verifyOtp(request);

        JwtUserDetails jwtUserDetails = JwtUserDetails.builder()
                .userId(userResp.getUserId())
                .email(userResp.getEmail())
                .role(userResp.getRole().name())
                .build();

        ResponseCookie refreshCookie = ResponseCookie.from("RefreshToken", jwtUtil.generateRefreshToken(userResp.getUserId()))
                .httpOnly(true).secure(false).sameSite("None")
                .maxAge(refreshTokenAge / 1000).path("/").build();

        ResponseCookie accessCookie = ResponseCookie.from("AccessToken", jwtUtil.generateAccessToken(jwtUserDetails))
                .httpOnly(true).secure(false).sameSite("None")
                .maxAge(accessTokenAge / 1000).path("/").build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString(), accessCookie.toString())
                .body(userResp);
    }

    // ---------- LOGOUT ----------
    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        ResponseCookie refreshCookie = ResponseCookie.from("RefreshToken", "")
                .httpOnly(true).secure(false).sameSite("None")
                .maxAge(0).path("/").build();

        ResponseCookie accessCookie = ResponseCookie.from("AccessToken", "")
                .httpOnly(true).secure(false).sameSite("None")
                .maxAge(0).path("/").build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString(), accessCookie.toString())
                .build();
    }

    // ---------- REFRESH TOKEN ----------
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshAccessToken(@CookieValue(name = "RefreshToken") String refreshToken) {
        if (jwtUtil.validateRefreshToken(refreshToken)) {
            UUID userId = jwtUtil.extractUserIdFromRefresh(refreshToken);

            RegisterResponse userResp = userService.getUserDetails(userId);
            JwtUserDetails jwtUserDetails = JwtUserDetails.builder()
                    .userId(userResp.getUserId())
                    .email(userResp.getEmail())
                    .role(userResp.getRole().name())
                    .build();

            ResponseCookie accessCookie = ResponseCookie.from("AccessToken", jwtUtil.generateAccessToken(jwtUserDetails))
                    .httpOnly(true).secure(false).sameSite("None")
                    .maxAge(accessTokenAge / 1000).path("/").build();

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                    .build();
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

    }
}
