package com.group6.Rental_Car.services.admindashboard;

import com.group6.Rental_Car.dtos.admindashboard.AdminDashboardResponse;
import com.group6.Rental_Car.entities.Incident;
import com.group6.Rental_Car.enums.IncidentStatus;
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
    private final IncidentRepository incidentRepository; // ← rename

    @Override
    public AdminDashboardResponse getOverview(LocalDate from, LocalDate to) {
        // Mặc định 30 ngày gần nhất
        if (from == null || to == null) {
            to   = LocalDate.now();
            from = to.minusDays(29);
        }
        if (from.isAfter(to)) {
            LocalDate tmp = from;
            from = to;
            to = tmp;
        }

        Timestamp tsFrom = Timestamp.valueOf(from.atStartOfDay());
        Timestamp tsTo   = Timestamp.valueOf(LocalDateTime.of(to, LocalTime.MAX));
//        LocalDateTime ldtFrom = tsFrom.toLocalDateTime();
//        LocalDateTime ldtTo   = tsTo.toLocalDateTime();

        // ===== KPI Vehicle =====
        long totalVehicles       = vehicleRepository.count();
        long availableVehicles   = vehicleRepository.countByStatus("AVAILABLE");
        long rentedVehicles      = vehicleRepository.countByStatus("RENTAL");
        long maintenanceVehicles = vehicleRepository.countByStatus("MAINTENANCE");

        // ===== KPI Orders + Revenue =====
        long totalOrders   = rentalOrderRepository.count();
        long activeOrders  = rentalOrderRepository.countByStatus("COMPLETED");
        double revenueInRange = Optional
                .ofNullable(rentalOrderRepository.revenueBetween(tsFrom, tsTo))
                .orElse(0d);

        // ===== KPI Users =====
        long totalUsers = userRepository.count();
        long admins     = userRepository.countByRole(Role.admin);
        long staffs     = userRepository.countByRole(Role.staff);
        long customers  = userRepository.countByRole(Role.customer);

        // ===== Incident (thay cho maintenance) =====
        // Tổng chi phí sự cố trong khoảng
        double incidentCostInRange = Optional
                .ofNullable(incidentRepository.totalCostBetweenTs(tsFrom, tsTo))
                .orElse(0d);

        // Lấy toàn bộ incident trong khoảng để nhóm theo status/severity
        // (cần method này trong IncidentRepository – xem ghi chú bên dưới)
        List<Incident> incidentsInRange =
                incidentRepository.findAllInRange(from, to);

        long totalIncidentsInRange = incidentsInRange.size();
        long openIncidents         = incidentsInRange.stream()
                .filter(i -> i.getStatus() == IncidentStatus.OPEN).count();
        long inProgressIncidents   = incidentsInRange.stream()
                .filter(i -> i.getStatus() == IncidentStatus.IN_PROGRESS).count();
        long resolvedIncidents     = incidentsInRange.stream()
                .filter(i -> i.getStatus() == IncidentStatus.RESOLVED).count();

        Map<String, Long> incidentsByStatus = incidentsInRange.stream()
                .collect(Collectors.groupingBy(i -> i.getStatus().name(), Collectors.counting()));

        // Nếu có enum IncidentSeverity thì thay i.getSeverity().name()
        Map<String, Long> incidentsBySeverity = incidentsInRange.stream()
                .collect(Collectors.groupingBy(i -> i.getSeverity().name(), Collectors.counting()));

        // Incidents theo ngày (điền đủ các ngày trống)
        var incDayRows = incidentRepository.incidentsByDay(tsFrom, tsTo);
        Map<LocalDate, Long> incDayMap = incDayRows.stream().collect(Collectors.toMap(
                r -> ((java.sql.Date) r[0]).toLocalDate(),
                r -> ((Number) r[1]).longValue()
        ));
        List<AdminDashboardResponse.DayCount> incidentsByDay = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            incidentsByDay.add(AdminDashboardResponse.DayCount.builder()
                    .date(d)
                    .count(incDayMap.getOrDefault(d, 0L))
                    .build());
        }

        // Recent incidents (giới hạn 10, bạn có thể đổi)
        var recentRows = incidentRepository.recentIncidents(tsFrom, tsTo, 10);
        var recentIncidents = recentRows.stream().map(r ->
                AdminDashboardResponse.RecentIncident.builder()
                        .incidentId(((Number) r[0]).intValue())
                        .vehicleId(((Number) r[1]).longValue())
                        .vehicleName((String) r[2])
                        .description((String) r[3])
                        .severity(String.valueOf(r[4]))
                        .status(String.valueOf(r[5]))
                        .occurredOn(((java.sql.Date) r[6]).toLocalDate())
                        .cost(r[7] == null ? 0d : ((Number) r[7]).doubleValue())
                        .build()
        ).toList();

        // ===== Vehicles by status =====
        var vehiclesByStatus = List.of(
                AdminDashboardResponse.LabelCount.builder().label("AVAILABLE").count(availableVehicles).build(),
                AdminDashboardResponse.LabelCount.builder().label("RENTAL").count(rentedVehicles).build(),
                AdminDashboardResponse.LabelCount.builder().label("MAINTENANCE").count(maintenanceVehicles).build()
        );

        // ===== Vehicles by station =====
        var vehiclesByStation = vehicleRepository.vehiclesPerStation().stream()
                .map(r -> AdminDashboardResponse.StationCount.builder()
                        .stationId(((Number) r[0]).intValue())
                        .stationName((String) r[1])
                        .total(((Number) r[2]).longValue())
                        .build())
                .toList();

        // ===== Revenue by day=====
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

        // ===== Rating =====
        Double avgRating = Optional.ofNullable(feedbackRepository.avgRating()).orElse(0d);
        Map<Integer, Long> ratingDistribution = feedbackRepository.ratingDistribution().stream()
                .collect(Collectors.toMap(
                        r -> ((Number) r[0]).intValue(),
                        r -> ((Number) r[1]).longValue(),
                        Long::sum,
                        TreeMap::new
                ));

        // ===== Build KPI =====
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
                //tổng chi phí incident
                .maintenanceCostInRange(incidentCostInRange)
                .build();

        // ===== Build IncidentKpi =====
        var incidentKpi = AdminDashboardResponse.IncidentKpi.builder()
                .totalIncidentsInRange(totalIncidentsInRange)
                .openIncidents(openIncidents)
                .inProgressIncidents(inProgressIncidents)
                .resolvedIncidents(resolvedIncidents)
                .incidentCostInRange(incidentCostInRange)
                .build();

        return AdminDashboardResponse.builder()
                .kpi(kpi)
                .vehiclesByStatus(vehiclesByStatus)
                .vehiclesByStation(vehiclesByStation)
                .revenueByDay(revenueByDay)
                .avgRating(avgRating)
                .ratingDistribution(ratingDistribution)
                // New: Incident section
                .incidentKpi(incidentKpi)
                .incidentsByDay(incidentsByDay)
                .incidentsByStatus(incidentsByStatus)
                .incidentsBySeverity(incidentsBySeverity)
                .recentIncidents(recentIncidents)
                .build();
    }
}
