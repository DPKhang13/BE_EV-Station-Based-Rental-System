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

    // Lưu OTP tạm: key = otp, value = record(email, expiredAt)
    private final Map<String, OtpRecord> otpStore = new ConcurrentHashMap<>();

    private static final long OTP_EXPIRATION_MS = 5 * 60 * 1000; // 5 phút

    @Override
    public String generateAndSendOtp(String email) {
        String otp = String.format("%06d", new Random().nextInt(999999)); // 6 digits
        Instant expiredAt = Instant.now().plusMillis(OTP_EXPIRATION_MS);

        otpStore.put(otp, new OtpRecord(email, expiredAt));

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
    public boolean validateOtp(String otp) {
        OtpRecord record = otpStore.get(otp);
        if (record == null) return false;
        if (Instant.now().isAfter(record.expiredAt())) {
            otpStore.remove(otp);
            return false;
        }
        return true;
    }

    @Override
    public void clearOtp(String otp) {
        otpStore.remove(otp);
    }

    @Override
    public String getEmailByOtp(String otp) {
        OtpRecord record = otpStore.get(otp);
        if (record == null) return null;
        return record.email();
    }

    // Inner record lưu dữ liệu OTP
    private record OtpRecord(String email, Instant expiredAt) {}
}
