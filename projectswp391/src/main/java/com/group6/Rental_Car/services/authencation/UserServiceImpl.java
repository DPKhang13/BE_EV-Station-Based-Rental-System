package com.group6.Rental_Car.services.authencation;

import com.group6.Rental_Car.dtos.authencation.LoginRequest;
import com.group6.Rental_Car.dtos.authencation.LoginResponse;
import com.group6.Rental_Car.entities.User;
import com.group6.Rental_Car.enums.Role;
import com.group6.Rental_Car.repositories.UserRepository;
import com.group6.Rental_Car.services.Jwt.MailService;
import com.group6.Rental_Car.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final MailService mailService;

    @Override
    public Optional<LoginResponse> login(LoginRequest request) {
        return userRepository.findByEmail(request.getEmail())
                // không dùng encode password -> so sánh thẳng
                .filter(user -> request.getPassword().equals(user.getPassword()))
                .map(user -> {
                    String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
                    return new LoginResponse(
                            user.getUserId(),
                            user.getFullName(),
                            user.getEmail(),
                            user.getPhone(),
                            user.getRole(),
                            user.getKycStatus(),
                            token
                    );
                });
    }

    @Override
    public User register(User user) {
        // Chuẩn hóa role về viết hoa
        if (user.getRole() != null) {
            user.setRole(Role.valueOf(user.getRole().name().toUpperCase()));
        }

        User saved = userRepository.save(user);

        // Sinh JWT token cho user mới
        String token = jwtUtil.generateToken(saved.getEmail(), saved.getRole().name());

        // Gửi token qua email
        mailService.sendToken(saved.getEmail(), token);

        return saved;
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }
}
