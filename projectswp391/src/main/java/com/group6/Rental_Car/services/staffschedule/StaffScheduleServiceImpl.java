package com.group6.Rental_Car.services.staffschedule;

import com.group6.Rental_Car.dtos.staffschedule.StaffScheduleCreateRequest;
import com.group6.Rental_Car.dtos.staffschedule.StaffScheduleResponse;
import com.group6.Rental_Car.dtos.staffschedule.StaffScheduleUpdateRequest;
import com.group6.Rental_Car.entities.EmployeeSchedule;
import com.group6.Rental_Car.entities.RentalStation;
import com.group6.Rental_Car.entities.User;
import com.group6.Rental_Car.repositories.EmployeeScheduleRepository;
import com.group6.Rental_Car.repositories.RentalStationRepository;
import com.group6.Rental_Car.repositories.UserRepository;
import com.group6.Rental_Car.services.authencation.UserService;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeMap;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StaffScheduleServiceImpl implements StaffScheduleService {

    private final EmployeeScheduleRepository employeeScheduleRepository;
    private final RentalStationRepository stationRepository;
    private final UserRepository userRepository;
    private final ModelMapper modelMapper;
    private final EntityManagerFactory entityManagerFactory;

    @Override
    public StaffScheduleResponse create(StaffScheduleCreateRequest req) {
        User staff = userRepository.findById(req.getUserId())
                .orElseThrow(() -> new RuntimeException("Staff not found"));
        RentalStation station = stationRepository.findById(req.getStationId())
                .orElseThrow(() -> new RuntimeException("Station not found"));

       String shift = req.getShiftTime() == null ? "" : req.getShiftTime().trim();
        if(employeeScheduleRepository.existsByStaff_UserIdAndShiftDateAndShiftTime(staff.getUserId(), req.getShiftDate(), shift)){
            throw new RuntimeException("This staff already has a schedule for that date & shift");
        }

        EmployeeSchedule employee = new EmployeeSchedule();
        employee.setStaff(staff);
        employee.setStation(station);
        employee.setShiftDate(req.getShiftDate());
        employee.setShiftTime(shift);

       employee = employeeScheduleRepository.save(employee);
       return resMap().map(employee);
    }

    private TypeMap<EmployeeSchedule, StaffScheduleResponse> resMap() {
        var tm = modelMapper.getTypeMap(EmployeeSchedule.class, StaffScheduleResponse.class);
        if (tm == null) {
            tm = modelMapper.createTypeMap(EmployeeSchedule.class, StaffScheduleResponse.class)
                    .addMapping(EmployeeSchedule::getScheduleId, StaffScheduleResponse::setScheduleId)
                    .addMapping(src -> src.getStaff().getUserId(), StaffScheduleResponse::setStaffId)
                    .addMapping(src -> src.getStaff().getFullName(), StaffScheduleResponse::setStaffName)
                    .addMapping(src -> src.getStation().getStationId(), StaffScheduleResponse::setStationId)
                    .addMapping(src -> src.getStation().getName(), StaffScheduleResponse::setStationName);
        }
        return tm;
    }
    @Override
    public StaffScheduleResponse update(Integer id, StaffScheduleUpdateRequest req) {
       EmployeeSchedule employee = employeeScheduleRepository.findById(id)
               .orElseThrow(() -> new RuntimeException("Employee schedule not found"));

       if (req.getUserId() == null) {
           User staff = userRepository.findById(req.getUserId())
                   .orElseThrow(() -> new RuntimeException("Staff not found"));
           employee.setStaff(staff);
       }
       if  (req.getStationId() == null) {
           RentalStation station = stationRepository.findById(req.getStationId())
                   .orElseThrow(() -> new RuntimeException("Station not found"));
           employee.setStation(station);
       }
       if (req.getShiftDate() != null) {
           employee.setShiftDate(req.getShiftDate());
       }
        if (req.getShiftTime() != null && !req.getShiftTime().isBlank()) {
            employee.setShiftTime(req.getShiftTime().trim());
        }
        if (employeeScheduleRepository.existsByStaff_UserIdAndShiftDateAndShiftTimeAndScheduleIdNot(
                employee.getStaff().getUserId(),
                employee.getShiftDate(),
                employee.getShiftTime(),
                employee.getScheduleId())) {
            throw new RuntimeException("This staff already has a schedule for that date & shift");
        }
        employee  = employeeScheduleRepository.save(employee);
        return resMap().map(employee);
    }


    public Page<StaffScheduleResponse> getAll(Pageable pageable) {
        return employeeScheduleRepository.findAll(pageable).map(resMap()::map);
    }

    public Page<StaffScheduleResponse> search(UUID userId, Integer stationId, LocalDate from, LocalDate to, String q, Pageable pageable) {
        String kw = (q == null || q.isBlank()) ? null : q.trim();
        return employeeScheduleRepository.search(userId, stationId, from, to, kw, pageable)
                .map(resMap()::map);
    }
}
