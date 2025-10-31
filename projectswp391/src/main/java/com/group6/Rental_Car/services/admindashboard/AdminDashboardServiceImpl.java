package com.group6.Rental_Car.services.admindashboard;

import com.group6.Rental_Car.dtos.admindashboard.AdminDashboardResponse;
import com.group6.Rental_Car.enums.Role;
import com.group6.Rental_Car.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {
    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;
    private final RentalOrderRepository rentalOrderRepository;
    private final FeedbackRepository feedbackRepository;
    private final MaintenanceRepository maintenanceRepository;

    @Override
    public AdminDashboardResponse getOverview(LocalDate from, LocalDate to) {
        // Mặc định 30 ngày gần nhất nếu thiếu 1 trong 2 tham số
        if (from == null || to == null) {
            to   = LocalDate.now();
            from = to.minusDays(29);
        }
        // Đảm bảo from <= to
        if (from.isAfter(to)) {
            LocalDate tmp = from; from = to; to = tmp;
        }

        Timestamp tsFrom = Timestamp.valueOf(from.atStartOfDay());
        Timestamp tsTo   = Timestamp.valueOf(LocalDateTime.of(to, LocalTime.MAX));

        // KPI Vehicle
        long totalVehicles       = vehicleRepository.count();
        long availableVehicles   = vehicleRepository.countByStatus("available");
        long rentedVehicles      = vehicleRepository.countByStatus("rented");
        long maintenanceVehicles = vehicleRepository.countByStatus("maintenance");

        // KPI Orders + Revenue
        long totalOrders      = rentalOrderRepository.count();
        long activeOrders     = rentalOrderRepository.countByStatus("active");
        double revenueInRange = Optional.ofNullable(
                rentalOrderRepository.revenueBetween(tsFrom, tsTo)
        ).orElse(0d);

        // KPI Users (YÊU CẦU: User.role @Enumerated(EnumType.STRING))
        long totalUsers = userRepository.count();
        long admins     = userRepository.countByRole(Role.admin);
        long staffs     = userRepository.countByRole(Role.staff);
        long customers  = userRepository.countByRole(Role.customer);

        double maintenanceCostInRange = Optional.ofNullable(
                maintenanceRepository.totalCostBetween(from, to)
        ).orElse(0d);

        // vehiclesByStatus (chart)
        var vehiclesByStatus = List.of(
                AdminDashboardResponse.LabelCount.builder().label("available").count(availableVehicles).build(),
                AdminDashboardResponse.LabelCount.builder().label("rented").count(rentedVehicles).build(),
                AdminDashboardResponse.LabelCount.builder().label("maintenance").count(maintenanceVehicles).build()
        );

        // vehiclesByStation (native: List<Object[]>: [stationId, stationName, total])
        var vehiclesByStation = vehicleRepository.vehiclesPerStation().stream()
                .map(r -> AdminDashboardResponse.StationCount.builder()
                        .stationId(((Number) r[0]).intValue())
                        .stationName((String) r[1])
                        .total(((Number) r[2]).longValue())
                        .build())
                .collect(Collectors.toList());

        // revenueByDay (điền đủ ngày rỗng)
        var revenueRows = rentalOrderRepository.revenueByDay(tsFrom, tsTo);
        Map<LocalDate, Double> revMap = revenueRows.stream().collect(Collectors.toMap(
                r -> ((java.sql.Date) r[0]).toLocalDate(),
                r -> ((Number) r[1]).doubleValue()
        ));
        List<AdminDashboardResponse.DayRevenue> revenueByDay = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            revenueByDay.add(AdminDashboardResponse.DayRevenue.builder()
                    .date(d)
                    .total(revMap.getOrDefault(d, 0d))
                    .build());
        }

        // rating
        Double avgRating = Optional.ofNullable(feedbackRepository.avgRating()).orElse(0d);
        Map<Integer, Long> ratingDistribution = feedbackRepository.ratingDistribution().stream()
                .collect(Collectors.toMap(
                        r -> ((Number) r[0]).intValue(),
                        r -> ((Number) r[1]).longValue(),
                        Long::sum,
                        TreeMap::new
                ));

        var kpi = AdminDashboardResponse.Kpi.builder()
                .totalVehicles(totalVehicles)
                .availableVehicles(availableVehicles)
                .rentedVehicles(rentedVehicles)
                .maintenanceVehicles(maintenanceVehicles)
                .totalOrders(totalOrders)
                .activeOrders(activeOrders)
                .revenueInRange(revenueInRange)
                .totalUsers(totalUsers)
                .admins(admins)
                .staffs(staffs)
                .customers(customers)
                .maintenanceCostInRange(maintenanceCostInRange)
                .build();

        return AdminDashboardResponse.builder()
                .kpi(kpi)
                .vehiclesByStatus(vehiclesByStatus)
                .vehiclesByStation(vehiclesByStation)
                .revenueByDay(revenueByDay)
                .avgRating(avgRating)
                .ratingDistribution(ratingDistribution)
                .build();
    }
}
