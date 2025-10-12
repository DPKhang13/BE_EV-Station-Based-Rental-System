package com.group6.Rental_Car.services.otpmailsender;

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

    // Lưu OTP kèm thời gian hết hạn
    private final Map<String, OtpEntry> otpStore = new ConcurrentHashMap<>();
    private static final long OTP_TTL_MILLIS = 5 * 60 * 1000; // 5 phút

    @Override
    public String generateAndSendOtp(String email) {
        String otp = String.valueOf(new Random().nextInt(900000) + 100000); // 6 số

        otpStore.put(email, new OtpEntry(otp, Instant.now().plusMillis(OTP_TTL_MILLIS)));

        // Gửi mail
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("Xác thực OTP");
        message.setText("Mã OTP của bạn là: " + otp + " (có hiệu lực trong 5 phút).");
        mailSender.send(message);

        return otp;
    }

    @Override
    public boolean validateOtp(String otp) {
        String email = getEmailByOtp(otp);
        if (email == null) return false;

        OtpEntry entry = otpStore.get(email);
        if (entry == null) return false;

        boolean notExpired = Instant.now().isBefore(entry.expiryTime());
        return notExpired && entry.otp().equals(otp);
    }

    @Override
    public void clearOtp(String otp) {
        String email = getEmailByOtp(otp);
        if (email != null) {
            otpStore.remove(email);
        }
    }



    @Override
    public String getEmailByOtp(String otp) {
        return otpStore.entrySet().stream()
                .filter(e -> e.getValue().otp().equals(otp))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private record OtpEntry(String otp, Instant expiryTime) {}
}
