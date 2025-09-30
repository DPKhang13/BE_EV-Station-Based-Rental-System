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
    public void sendToken(String toEmail, String token) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject("Your Login Token");
            message.setText("Here is your login token: " + token);

            mailSender.send(message); //  chỉ gửi 1 lần
            System.out.println(" Token email sent to: " + toEmail);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
