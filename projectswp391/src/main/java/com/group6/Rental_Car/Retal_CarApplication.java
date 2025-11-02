package com.group6.Rental_Car;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Retal_CarApplication {

    public static void main(String[] args) {
        SpringApplication.run(Retal_CarApplication.class, args);
    }

}