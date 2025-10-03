package com.group6.Rental_Car.services.Jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MailServiceImpl implements MailService {

    private final JavaMailSender mailSender;

    @Override
    public void sendOtp(String toEmail, String otp) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject("Your OTP Code");
            message.setText("Mã OTP đăng nhập của bạn là: " + otp
                    + "\nOTP này sẽ hết hạn sau 5 phút.");

            mailSender.send(message);
            System.out.println("✅ OTP email sent to: " + toEmail);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
