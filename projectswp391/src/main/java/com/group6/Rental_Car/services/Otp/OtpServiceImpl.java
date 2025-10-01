package com.group6.Rental_Car.services.Otp;


import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OtpServiceImpl implements OtpService {

    private final Map<String, OtpEntry> otpStore = new ConcurrentHashMap<>();

    private static class OtpEntry {
        String email;
        Instant expiry;

        OtpEntry(String email, Instant expiry) {
            this.email = email;
            this.expiry = expiry;
        }
    }

    @Override
    public String generateOtp(String email) {
        String otp = String.format("%06d", new Random().nextInt(999999));
        otpStore.put(otp, new OtpEntry(email, Instant.now().plusSeconds(300))); // TTL 5 phút
        return otp;
    }

    @Override
    public boolean validateOtp(String otp) {
        OtpEntry entry = otpStore.get(otp);
        return entry != null && entry.expiry.isAfter(Instant.now());
    }

    @Override
    public String getEmailByOtp(String otp) {
        OtpEntry entry = otpStore.get(otp);
        return (entry != null && entry.expiry.isAfter(Instant.now())) ? entry.email : null;
    }


    @Override
    public void clearOtp(String otp) {
        otpStore.remove(otp);
    }
}
