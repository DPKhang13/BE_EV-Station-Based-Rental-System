package com.group6.Rental_Car.utils;

import com.group6.Rental_Car.exceptions.BadRequestException;

import java.util.Set;

public class VehicleValidationUtil {
    private VehicleValidationUtil() {}

    public static String trim(String s){
        if( s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    public static <T> T requireNonNull(T obj, String message) {
        if (obj == null) {
            throw new BadRequestException(message + "is required");
        }
        return obj;
    }

    public static String requireNonBlank(String obj, String message){
        if(obj == null || obj.isBlank()){
            throw new BadRequestException(message + "is required");
        }
        return obj;
    }

    public static void ensureMaxLength(String obj, int max, String message){
        if(obj == null || obj.length() > max){
            throw new BadRequestException(message + "length must be <=" + max);
        }
    }

    public static void ensureRange(int val, int min, int max, String message){
        if(val < min || val > max){
            throw new BadRequestException(message + "value must be between " + min + " and " + max);
        }
    }

    public static void ensureInSetIgnoreCase(String obj, Set<String> allowed, String message){
        if(obj == null)  return;
        if(!allowed.contains(obj.toLowerCase())) {
            throw new BadRequestException(message + "must be one of" + allowed);
        }
    }

    public static String normalizeNullableLower(String s){
        String t = trim(s);
        return ( t == null) ? null : t.toLowerCase(); //??
    }
}
