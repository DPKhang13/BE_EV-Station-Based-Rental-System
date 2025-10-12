package com.group6.Rental_Car.exceptions;

public class AuthenticationFailedException extends RuntimeException {
    public AuthenticationFailedException(String error) {
        super(error);
    }
}