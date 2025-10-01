package com.group6.Rental_Car.services.Otp;

import com.group6.Rental_Car.entities.User;
import com.group6.Rental_Car.enums.OtpType;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Service
public class OtpServiceImpl implements OtpService {

    private static class OtpInfo {
        String otp;
        OtpType type;
        User pendingUser; // chỉ dùng khi REGISTER

        OtpInfo(String otp, OtpType type, User pendingUser) {
            this.otp = otp;
            this.type = type;
            this.pendingUser = pendingUser;
        }
    }

    private final Map<String, OtpInfo> otpStorage = new ConcurrentHashMap<>(); // email -> otp info

    @Override
    public String generateOtp(String email, OtpType type, User pendingUser) {
        String otp = String.valueOf((int) (Math.random() * 900000) + 100000); // random 6 số
        otpStorage.put(email, new OtpInfo(otp, type, pendingUser));
        return otp;
    }

    @Override
    public boolean validateOtp(String otp) {
        return otpStorage.values().stream().anyMatch(info -> info.otp.equals(otp));
    }

    @Override
    public String getEmailByOtp(String otp) {
        return otpStorage.entrySet().stream()
                .filter(entry -> entry.getValue().otp.equals(otp))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    @Override
    public void clearOtp(String otp) {
        otpStorage.entrySet().removeIf(entry -> entry.getValue().otp.equals(otp));
    }

    @Override
    public User getPendingUser(String email) {
        OtpInfo info = otpStorage.get(email);
        return (info != null) ? info.pendingUser : null;
    }

    @Override
    public void removePendingUser(String email) {
        otpStorage.remove(email);
    }

    // Helper để biết loại OTP
    public OtpType getOtpType(String email) {
        OtpInfo info = otpStorage.get(email);
        return (info != null) ? info.type : null;
    }
}
