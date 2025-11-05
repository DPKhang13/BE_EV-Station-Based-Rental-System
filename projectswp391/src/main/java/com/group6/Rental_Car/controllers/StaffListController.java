package com.group6.Rental_Car.controllers;

import com.group6.Rental_Car.dtos.stafflist.staffList;
import com.group6.Rental_Car.services.staffList.staffListService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/staffList")
public class StaffListController {
    private final staffListService staffListService;
    @GetMapping("/list")
    public List<staffList> getStaffList(){
        return  staffListService.getStaffList();
    }
}
