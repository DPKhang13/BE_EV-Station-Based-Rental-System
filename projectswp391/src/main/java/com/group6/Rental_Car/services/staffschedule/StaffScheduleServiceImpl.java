    package com.group6.Rental_Car.services.staffschedule;

    import com.group6.Rental_Car.dtos.stafflist.StaffResponse;
    import com.group6.Rental_Car.dtos.staffschedule.StaffScheduleCreateRequest;
    import com.group6.Rental_Car.dtos.staffschedule.StaffScheduleResponse;
    import com.group6.Rental_Car.dtos.staffschedule.StaffScheduleUpdateRequest;
    import com.group6.Rental_Car.entities.EmployeeSchedule;
    import com.group6.Rental_Car.entities.RentalStation;
    import com.group6.Rental_Car.entities.User;
    import com.group6.Rental_Car.enums.Role;
    import com.group6.Rental_Car.repositories.EmployeeScheduleRepository;
    import com.group6.Rental_Car.repositories.RentalStationRepository;
    import com.group6.Rental_Car.repositories.UserRepository;
    import jakarta.transaction.Transactional;
    import lombok.RequiredArgsConstructor;

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
           return toResponse(employee);
        }

        @Override
        public StaffScheduleResponse update(Integer id, StaffScheduleUpdateRequest req) {
           EmployeeSchedule employee = employeeScheduleRepository.findById(id)
                   .orElseThrow(() -> new RuntimeException("Employee schedule not found"));

           if (req.getUserId() != null) {
               User staff = userRepository.findById(req.getUserId())
                       .orElseThrow(() -> new RuntimeException("Staff not found"));
               employee.setStaff(staff);
           }
           if  (req.getStationId() != null) {
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
            return toResponse(employee);
        }


        public Page<StaffScheduleResponse> getAll(Pageable pageable) {
            return employeeScheduleRepository.findAll(pageable).map(this::toResponse);
        }

        @Transactional
        public void intPickup(UUID staffId, LocalDate date, String shiftTime) {
            var es = employeeScheduleRepository.findByStaff_UserIdAndShiftDateAndShiftTime(staffId, date, shiftTime)
                    .orElseThrow(() -> new IllegalStateException("Employee schedule not found"));
            es.setPickupCount(es.getPickupCount() + 1);
            employeeScheduleRepository.saveAndFlush(es);
        }

        @Transactional
        public void intReturn(UUID staffId, LocalDate date, String shiftTime) {
            var es = employeeScheduleRepository.findByStaff_UserIdAndShiftDateAndShiftTime(staffId, date, shiftTime)
                    .orElseThrow(() -> new IllegalStateException("Employee schedule not found"));
            es.setReturnCount(es.getReturnCount() + 1);
            employeeScheduleRepository.saveAndFlush(es);
        }


        private StaffScheduleResponse toResponse(EmployeeSchedule e) {
            StaffScheduleResponse dto = new StaffScheduleResponse();
            dto.setScheduleId(e.getScheduleId());
            dto.setStaffId(e.getStaff().getUserId());
            dto.setStaffName(e.getStaff().getFullName());
            dto.setStationId(e.getStation().getStationId());
            dto.setStationName(e.getStation().getName());
            dto.setShiftDate(e.getShiftDate());
            dto.setShiftTime(e.getShiftTime());
            return dto;
        }

        @Override
        public List<StaffResponse> getStaffList() {
            List<User> staffList = userRepository.findByRole(Role.staff);

            return staffList.stream().map(u -> {
                long pickupCount = 0L;
                long returnCount = 0L;

                List<EmployeeSchedule> schedules = u.getEmployeeSchedules();
                if (schedules != null) {
                    pickupCount = schedules.stream()
                            .mapToLong(EmployeeSchedule::getPickupCount)
                            .sum();

                    returnCount = schedules.stream()
                            .mapToLong(EmployeeSchedule::getReturnCount)
                            .sum();
                }

                return new StaffResponse(
                        u.getUserId().toString(),
                        u.getFullName(),
                        u.getEmail(),
                        u.getRole().name(),
                        u.getRentalStation() != null ? u.getRentalStation().getName() : null,
                        pickupCount,
                        returnCount,
                        u.getStatus().toString()
                );
            }).toList();
        }
    }
