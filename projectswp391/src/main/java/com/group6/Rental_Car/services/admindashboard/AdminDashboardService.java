package com.group6.Rental_Car.services.admindashboard;

import com.group6.Rental_Car.dtos.admindashboard.AdminDashboardResponse;

import java.time.LocalDate;

public interface AdminDashboardService {
    AdminDashboardResponse getOverview(LocalDate from, LocalDate to);


}
