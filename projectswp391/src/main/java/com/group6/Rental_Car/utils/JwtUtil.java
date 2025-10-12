package com.group6.Rental_Car.utils;
import com.group6.Rental_Car.utils.JwtUserDetails;
import io.jsonwebtoken.*;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.security.Key;
import java.util.Date;
import java.util.UUID;

@AllArgsConstructor
@Builder
public class JwtUtil {

    private final Key accessKey;
    private final Key refreshKey;
    private final long accessExpirationMillis;
    private final long refreshExpirationMillis;

    // Tạo AccessToken chứa userId + role
    public String generateAccessToken(JwtUserDetails userDetails) {
        return Jwts.builder()
                .setSubject(userDetails.getUserId().toString()) // UUID -> String
                .claim("role", userDetails.getRole())           // <-- dùng .claim()
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + accessExpirationMillis))
                .signWith(accessKey, SignatureAlgorithm.HS256)
                .compact();
    }

    // Validate Access Token
    public boolean validateAccessToken(String token) {
        return validateToken(token, accessKey);
    }

    // Lấy user từ Access Token
    public JwtUserDetails extractUserFromAccess(String token) {
        return extractUserDetails(token, accessKey);
    }

    // Tạo Refresh Token chỉ chứa userId
    public String generateRefreshToken(UUID userId) {
        return Jwts.builder()
                .setSubject(userId.toString())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + refreshExpirationMillis))
                .signWith(refreshKey, SignatureAlgorithm.HS256)
                .compact();
    }

    // Validate Refresh Token
    public boolean validateRefreshToken(String token) {
        return validateToken(token, refreshKey);
    }

    // Lấy userId từ Refresh Token
    public UUID extractUserIdFromRefresh(String token) {
        Claims claims = parseClaims(token, refreshKey);
        return UUID.fromString(claims.getSubject());
    }

    // ======= PRIVATE SUPPORT METHODS =======
    private boolean validateToken(String token, Key key) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private JwtUserDetails extractUserDetails(String token, Key key) {
        Claims claims = parseClaims(token, key);
        UUID userId = UUID.fromString(claims.getSubject());
        String role = claims.get("role", String.class);

        return JwtUserDetails.builder()
                .userId(userId)
                .role(role)
                .build();
    }

    private Claims parseClaims(String token, Key key) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
