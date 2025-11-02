package com.group6.Rental_Car.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

    @Configuration
    public class CorsConfig {


        @Bean
        public WebMvcConfigurer corsConfigurer() {
            return new WebMvcConfigurer() {
                @Override
                public void addCorsMappings(CorsRegistry registry) {
                    registry.addMapping("/**") // Cho phép tất cả endpoint
                            .allowedOriginPatterns("*") // Cho phép tất cả domain
                            .allowedMethods("*")        // GET, POST, PUT, DELETE, OPTIONS,...
                            .allowedHeaders("*")        // Cho phép tất cả header
                            .exposedHeaders("*")        // Cho phép client đọc tất cả header trả về
                            .allowCredentials(true);    // Cho phép gửi cookie/token
                }
            };
        }
    }

