package com.group6.Rental_Car.services.admindashboard;

import com.group6.Rental_Car.dtos.admindashboard.AdminDashboardResponse;
import com.group6.Rental_Car.entities.OrderService;
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
    private final OrderServiceRepository orderServiceRepository;

    @Override
    public AdminDashboardResponse getOverview(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            to = LocalDate.now();
            from = to.minusDays(29);
        }
        if (from.isAfter(to)) {
            LocalDate tmp = from;
            from = to;
            to = tmp;
        }

        Timestamp tsFrom = Timestamp.valueOf(from.atStartOfDay());
        Timestamp tsTo = Timestamp.valueOf(LocalDateTime.of(to, LocalTime.MAX));

        // ===== VEHICLE KPIs =====
        long totalVehicles = vehicleRepository.count();
        long availableVehicles = vehicleRepository.countByStatus("AVAILABLE");
        long rentedVehicles = vehicleRepository.countByStatus("RENTAL");
        long maintenanceVehicles = vehicleRepository.countByStatus("MAINTENANCE");

        // ===== ORDER KPIs =====
        long totalOrders = rentalOrderRepository.count();
        long completedOrders = rentalOrderRepository.countByStatus("COMPLETED");
        double revenueInRange = Optional.ofNullable(rentalOrderRepository.revenueBetween(tsFrom, tsTo)).orElse(0d);

        // ===== USER KPIs =====
        long totalUsers = userRepository.count();
        long admins = userRepository.countByRole(Role.admin);
        long staffs = userRepository.countByRole(Role.staff);
        long customers = userRepository.countByRole(Role.customer);

        // ===== SERVICE KPIs =====
        double totalServiceCost = Optional.ofNullable(orderServiceRepository.totalCostBetween(tsFrom, tsTo)).orElse(0d);
        List<OrderService> servicesInRange = orderServiceRepository.findAllInRange(from, to);
        long totalServices = servicesInRange.size();

        Map<String, Long> servicesByType = servicesInRange.stream()
                .collect(Collectors.groupingBy(
                        s -> Optional.ofNullable(s.getServiceType()).orElse("UNKNOWN"),
                        Collectors.counting()
                ));

        Map<String, Long> servicesByStatus = servicesInRange.stream()
                .collect(Collectors.groupingBy(
                        s -> Optional.ofNullable(s.getStatus()).orElse("UNKNOWN"),
                        Collectors.counting()
                ));

        // ===== SERVICES BY DAY =====
        var serviceDayRows = orderServiceRepository.countByDay(tsFrom, tsTo);
        Map<LocalDate, Long> serviceDayMap = serviceDayRows.stream().collect(Collectors.toMap(
                r -> ((java.sql.Date) r[0]).toLocalDate(),
                r -> ((Number) r[1]).longValue()
        ));
        List<AdminDashboardResponse.DayCount> servicesByDay = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            servicesByDay.add(AdminDashboardResponse.DayCount.builder()
                    .date(d)
                    .count(serviceDayMap.getOrDefault(d, 0L))
                    .build());
        }

        // ===== RECENT SERVICES =====
        var recentServices = orderServiceRepository.findTop10ByOrderByOccurredAtDesc().stream()
                .map(s -> AdminDashboardResponse.RecentService.builder()
                        .serviceId(s.getServiceId())
                        .vehicleId(s.getVehicle().getVehicleId())
                        .vehicleName(s.getVehicle().getVehicleName())
                        .serviceType(s.getServiceType())
                        .description(s.getDescription())
                        .status(s.getStatus())
                        .cost(s.getCost() != null ? s.getCost().doubleValue() : 0d)
                        .occurredAt(s.getOccurredAt())
                        .resolvedAt(s.getResolvedAt())
                        .build()
                ).toList();

        // ===== REVENUE BY STATION =====
        var revStationRows = rentalOrderRepository.revenuePerStation(tsFrom, tsTo);
        var revenueByStation = revStationRows.stream()
                .map(r -> AdminDashboardResponse.StationRevenue.builder()
                        .stationId(((Number) r[0]).intValue())
                        .stationName((String) r[1])
                        .totalRevenue(r[2] == null ? 0d : ((Number) r[2]).doubleValue())
                        .build())
                .toList();

        // ===== REVENUE BY DAY =====
        var revRows = rentalOrderRepository.revenueByDay(tsFrom, tsTo);
        Map<LocalDate, Double> revMap = revRows.stream().collect(Collectors.toMap(
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

        // ===== FEEDBACK KPIs =====
        Double avgRating = Optional.ofNullable(feedbackRepository.avgRating()).orElse(0d);
        Map<Integer, Long> ratingDistribution = feedbackRepository.ratingDistribution().stream()
                .collect(Collectors.toMap(
                        r -> ((Number) r[0]).intValue(),
                        r -> ((Number) r[1]).longValue(),
                        Long::sum,
                        TreeMap::new
                ));

        // ===== BUILD RESPONSE =====
        var kpi = AdminDashboardResponse.Kpi.builder()
                .totalVehicles(totalVehicles)
                .availableVehicles(availableVehicles)
                .rentedVehicles(rentedVehicles)
                .maintenanceVehicles(maintenanceVehicles)
                .totalOrders(totalOrders)
                .activeOrders(completedOrders)
                .revenueInRange(revenueInRange)
                .totalUsers(totalUsers)
                .admins(admins)
                .staffs(staffs)
                .customers(customers)
                .build();

        var serviceKpi = AdminDashboardResponse.ServiceKpi.builder()
                .totalServices(totalServices)
                .totalCost(totalServiceCost)
                .servicesByType(servicesByType)
                .servicesByStatus(servicesByStatus)
                .build();

        return AdminDashboardResponse.builder()
                .kpi(kpi)
                .revenueByDay(revenueByDay)
                .revenueByStation(revenueByStation)
                .avgRating(avgRating)
                .ratingDistribution(ratingDistribution)
                .servicesByDay(servicesByDay)
                .serviceKpi(serviceKpi)
                .recentServices(recentServices)
                .build();
    }
}
