package com.group6.Rental_Car.services.staffList;

import com.group6.Rental_Car.dtos.stafflist.staffList;
import com.group6.Rental_Car.repositories.EmployeeScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
@Service
@RequiredArgsConstructor
public class staffListServiceImpl implements staffListService {
    private final EmployeeScheduleRepository employeeScheduleRepository;

    @Override
    public List<staffList> getStaffList() {
        return employeeScheduleRepository.getStaffList();

    }
}
