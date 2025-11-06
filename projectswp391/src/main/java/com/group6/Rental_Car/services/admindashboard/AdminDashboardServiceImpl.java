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
    private final IncidentRepository incidentRepository;

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

        // ===== Incident =====
        double incidentCostInRange = Optional
                .ofNullable(incidentRepository.totalCostBetweenTs(tsFrom, tsTo))
                .orElse(0d);

        List<Incident> incidentsInRange = incidentRepository.findAllInRange(from, to);
        long totalIncidentsInRange = incidentsInRange.size();
        long openIncidents         = incidentsInRange.stream()
                .filter(i -> i.getStatus() == IncidentStatus.OPEN).count();
        long inProgressIncidents   = incidentsInRange.stream()
                .filter(i -> i.getStatus() == IncidentStatus.IN_PROGRESS).count();
        long resolvedIncidents     = incidentsInRange.stream()
                .filter(i -> i.getStatus() == IncidentStatus.RESOLVED).count();

        Map<String, Long> incidentsByStatus = incidentsInRange.stream()
                .collect(Collectors.groupingBy(
                        i -> i.getStatus() != null ? i.getStatus().name() : "UNKNOWN",
                        Collectors.counting()
                ));
        Map<String, Long> incidentsBySeverity = incidentsInRange.stream()
                .collect(Collectors.groupingBy(
                        i -> i.getSeverity() != null ? i.getSeverity().name() : "UNKNOWN",
                        Collectors.counting()
                ));

        // ===== Incidents theo ngày =====
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

        // ===== Revenue for each station =====
        var revStationRows = rentalOrderRepository.revenuePerStation(tsFrom, tsTo);
        var revenueByStation = revStationRows.stream()
                .map(r -> AdminDashboardResponse.StationRevenue.builder()
                        .stationId(((Number) r[0]).intValue())
                        .stationName((String) r[1])
                        .totalRevenue(r[2] == null ? 0d : ((Number) r[2]).doubleValue())
                        .build())
                .toList();


        var todayRows  = rentalOrderRepository.revenueTodayPerStation();
        var weekRows   = rentalOrderRepository.revenueThisWeekPerStation();
        var monthRows  = rentalOrderRepository.revenueThisMonthPerStation();

        Map<Integer, Double> todayMap = todayRows.stream().collect(Collectors.toMap(
                r -> ((Number) r[0]).intValue(),
                r -> ((Number) r[2]).doubleValue()));
        Map<Integer, Double> weekMap = weekRows.stream().collect(Collectors.toMap(
                r -> ((Number) r[0]).intValue(),
                r -> ((Number) r[2]).doubleValue()));
        Map<Integer, Double> monthMap = monthRows.stream().collect(Collectors.toMap(
                r -> ((Number) r[0]).intValue(),
                r -> ((Number) r[2]).doubleValue()));

        List<AdminDashboardResponse.StationRevenueAnalysis> revenueByStationAnalysis =
                revStationRows.stream()
                        .map(r -> {
                            Integer stationId = ((Number) r[0]).intValue();
                            String name = (String) r[1];
                            Double total = r[2] == null ? 0d : ((Number) r[2]).doubleValue();

                            double avgPerDay = total / 30.0;
                            double today = todayMap.getOrDefault(stationId, 0d);
                            double week = weekMap.getOrDefault(stationId, 0d);
                            double month = monthMap.getOrDefault(stationId, 0d);

                            return AdminDashboardResponse.StationRevenueAnalysis.builder()
                                    .stationId(stationId)
                                    .stationName(name)
                                    .avgPerDay(avgPerDay)
                                    .todayRevenue(today)
                                    .weekRevenue(week)
                                    .monthRevenue(month)
                                    .growthDay(0d)
                                    .growthWeek(0d)
                                    .growthMonth(0d)
                                    .build();
                        })
                        .toList();

        // ===== Recent incidents =====
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


        // ===== Vehicles by station + usage rate =====
        var usageRows = vehicleRepository.vehicleUsagePerStation();
        var vehiclesByStation = usageRows.stream()
                .map(r -> {
                    int stationId = ((Number) r[0]).intValue();
                    String stationName = (String) r[1];
                    long total = ((Number) r[2]).longValue();
                    long rented = ((Number) r[3]).longValue();
                    double utilization = total == 0 ? 0 : (rented * 100.0 / total);

                    return AdminDashboardResponse.StationCount.builder()
                            .stationId(stationId)
                            .stationName(stationName)
                            .total(total)
                            .rented(rented)
                            .utilization(utilization)
                            .build();
                })
                .toList();
        // ===== Revenue by day =====
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
                .maintenanceCostInRange(incidentCostInRange)
                .build();

        var incidentKpi = AdminDashboardResponse.IncidentKpi.builder()
                .totalIncidentsInRange(totalIncidentsInRange)
                .openIncidents(openIncidents)
                .inProgressIncidents(inProgressIncidents)
                .resolvedIncidents(resolvedIncidents)
                .incidentCostInRange(incidentCostInRange)
                .build();
        var hourRows = rentalOrderRepository.ordersByHour(tsFrom, tsTo);

// Chuẩn hóa thành đủ 24h (nếu giờ không có đơn => 0)
        long[] hours = new long[24];
        for (Object[] r : hourRows) {
            int h = ((Number) r[0]).intValue(); // 0..23
            long c = ((Number) r[1]).longValue();
            if (h >= 0 && h <= 23) hours[h] = c;
        }

// Đưa dữ liệu ra response
        List<AdminDashboardResponse.HourCount> orderByHour = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            orderByHour.add(AdminDashboardResponse.HourCount.builder()
                    .hour(h)
                    .count(hours[h])
                    .build());
        }

// Tính khung giờ cao điểm (3 tiếng liên tiếp)
        int windowSize = 3;
        long bestSum = -1;
        int bestStart = 0;
        for (int start = 0; start <= 24 - windowSize; start++) {
            long sum = 0;
            for (int k = 0; k < windowSize; k++) {
                sum += hours[start + k];
            }
            if (sum > bestSum) {
                bestSum = sum;
                bestStart = start;
            }
        }
        AdminDashboardResponse.PeakHourWindow peakWindow =
                AdminDashboardResponse.PeakHourWindow.builder()
                        .startHour(bestStart)
                        .endHour(bestStart + windowSize - 1)
                        .windowSize(windowSize)
                        .total(bestSum < 0 ? 0 : bestSum)
                        .build();
        return AdminDashboardResponse.builder()
                .kpi(kpi)
                .vehiclesByStatus(vehiclesByStatus)
                .vehiclesByStation(vehiclesByStation)
                .revenueByDay(revenueByDay)
                .avgRating(avgRating)
                .ratingDistribution(ratingDistribution)
                .revenueByStation(revenueByStation)
                .revenueByStationAnalysis(revenueByStationAnalysis)
                .incidentKpi(incidentKpi)
                .incidentsByDay(incidentsByDay)
                .incidentsByStatus(incidentsByStatus)
                .incidentsBySeverity(incidentsBySeverity)
                .recentIncidents(recentIncidents)
                .orderByHour(orderByHour)
                .peakHourWindow(peakWindow)
                .build();
    }
}
