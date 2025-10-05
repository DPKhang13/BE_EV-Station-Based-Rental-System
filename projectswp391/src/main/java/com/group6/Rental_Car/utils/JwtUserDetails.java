package com.group6.Rental_Car.utils;

import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class JwtUserDetails implements UserDetails {
    private UUID userId;     // UUID userId
    private String email;    // email từ DB hoặc từ OAuth2
    private String password; // password từ DB (hash), nếu login bằng Google thì có thể để rỗng
    private String role;     // role của user



    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (role == null) return List.of();
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
     return ""; // nếu login DB thì trả về password hash, nếu OAuth2 thì ""
    }

    @Override
    public String getUsername() {
        return this.email; // email sẽ là định danh duy nhất
    }
}
