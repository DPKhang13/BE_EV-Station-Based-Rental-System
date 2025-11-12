package com.group6.Rental_Car.services.otpmailsender;

import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class OtpMailServiceImpl implements OtpMailService {

    private final JavaMailSender mailSender;


    private final Map<String, OtpRecord> otpStore = new ConcurrentHashMap<>();

    private static final long OTP_EXPIRATION_MS = 5 * 60 * 1000; // 5 phút

    @Override
    public String generateAndSendOtp(String email) {
        String otp = String.format("%06d", new Random().nextInt(999999));
        Instant expiredAt = Instant.now().plusMillis(OTP_EXPIRATION_MS);

        otpStore.put(email, new OtpRecord(otp, expiredAt)); // key là email

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            message.setSubject("Your Rental_Car OTP Verification Code");
            message.setText("Mã OTP của bạn là: " + otp + "\nHiệu lực trong 5 phút.");
            mailSender.send(message);
        } catch (Exception e) {
            throw new RuntimeException("Gửi OTP thất bại: " + e.getMessage());
        }

        return otp;
    }

    @Override
    public boolean validateOtp(String email, String otp) {
        OtpRecord record = otpStore.get(email);
        if (record == null) return false;

        if (Instant.now().isAfter(record.expiredAt())) {
            otpStore.remove(email);
            return false;
        }

        return record.otp().equals(otp);
    }

    @Override
    public void clearOtp(String email) {
        otpStore.remove(email);
    }

    @Override
    public String getEmailByOtp(String otp) {
        // tìm ngược lại trong map (optional)
        return otpStore.entrySet().stream()
                .filter(e -> e.getValue().otp().equals(otp))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private record OtpRecord(String otp, Instant expiredAt) {}
}