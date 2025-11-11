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
    private final RentalOrderDetailRepository rentalOrderDetailRepository; // ⬅️ dùng detail cho doanh thu/ngày/giờ
    private final FeedbackRepository feedbackRepository;
    private final OrderServiceRepository orderServiceRepository;           // ⬅️ thay Incident

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
        // lưu ý: status của rentalorder theo schema mới: pending | active | completed | cancelled
        long completedOrders = rentalOrderRepository.countByStatus("completed");
        double revenueInRange = Optional
                .ofNullable(rentalOrderDetailRepository.revenueBetween(tsFrom, tsTo)) // ⬅️ sum price ở detail
                .orElse(0d);

        // ===== KPI Users =====
        long totalUsers = userRepository.count();
        long admins     = userRepository.countByRole(Role.admin);
        long staffs     = userRepository.countByRole(Role.staff);
        long customers  = userRepository.countByRole(Role.customer);

        // ===== OrderService (thay Incident) =====
        // Tổng chi phí dịch vụ/sự cố trong khoảng (sum cost)
        double incidentCostInRange = Optional
                .ofNullable(orderServiceRepository.totalCostBetweenTs(tsFrom, tsTo)) // occurred_at
                .orElse(0d);

        // Lấy toàn bộ orderservice trong khoảng để nhóm theo status/type
        List<OrderService> servicesInRange = orderServiceRepository.findAllInRange(from, to);

        long totalIncidentsInRange = servicesInRange.size();
        long pendingIncidents      = servicesInRange.stream().filter(i -> eq(i.getStatus(),"pending")).count();
        long processingIncidents   = servicesInRange.stream().filter(i -> eq(i.getStatus(),"processing")).count();
        long doneIncidents         = servicesInRange.stream().filter(i -> eq(i.getStatus(),"done")).count();
        long cancelledIncidents    = servicesInRange.stream().filter(i -> eq(i.getStatus(),"cancelled")).count();

        Map<String, Long> incidentsByStatus = servicesInRange.stream()
                .collect(Collectors.groupingBy(i -> nz(i.getStatus()).toUpperCase(), Collectors.counting()));

        // Không còn severity trong schema mới => nhóm theo service_type
        Map<String, Long> incidentsByType = servicesInRange.stream()
                .collect(Collectors.groupingBy(i -> nz(i.getServiceType()).toUpperCase(), Collectors.counting()));

        // Incidents theo ngày (điền đủ các ngày trống) -> DATE(occurred_at)
        var incDayRows = orderServiceRepository.incidentsByDay(tsFrom, tsTo);
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

        // ===== Revenue for each station (theo rentalorder_detail.price) =====
        var revStationRows = rentalOrderDetailRepository.revenuePerStation(tsFrom, tsTo);
        var revenueByStation = revStationRows.stream()
                .map(r -> AdminDashboardResponse.StationRevenue.builder()
                        .stationId(((Number) r[0]).intValue())
                        .stationName((String) r[1])
                        .totalRevenue(r[2] == null ? 0d : ((Number) r[2]).doubleValue())
                        .build())
                .toList();

        // ===== Recent order services (giới hạn 10) =====
        // SELECT: service_id, vehicle_id, vehicle_name, description, status, occurred_at, cost
        var recentRows = orderServiceRepository.recentIncidents(tsFrom, tsTo, 10);
        var recentIncidents = recentRows.stream().map(r ->
                AdminDashboardResponse.RecentIncident.builder()
                        .incidentId(((Number) r[0]).intValue())
                        .vehicleId(((Number) r[1]).longValue())
                        .vehicleName((String) r[2])
                        .description((String) r[3])
                        .status(String.valueOf(r[4]))
                        .occurredOn(((java.sql.Timestamp) r[5]).toLocalDateTime().toLocalDate())
                        .cost(r[6] == null ? 0d : ((Number) r[6]).doubleValue())
                        .build()
        ).toList();

        // ===== Vehicles by station =====
        var vehiclesByStation = vehicleRepository.vehiclesPerStation().stream()
                .map(r -> AdminDashboardResponse.StationCount.builder()
                        .stationId(((Number) r[0]).intValue())
                        .stationName((String) r[1])
                        .total(((Number) r[2]).longValue())
                        .build())
                .toList();

        // ===== Revenue by day (từ rentalorder_detail.start_time) =====
        var revRows = rentalOrderDetailRepository.revenueByDay(tsFrom, tsTo);
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

        // ===== Orders by hour & Peak Window (3 giờ) =====
        var hourRows = rentalOrderDetailRepository.ordersByHour(tsFrom, tsTo);
        long[] hours = new long[24]; // 0..23
        for (Object[] r : hourRows) {
            int h = ((Number) r[0]).intValue();
            long c = ((Number) r[1]).longValue();
            if (h >= 0 && h <= 23) hours[h] = c;
        }
        List<AdminDashboardResponse.HourCount> ordersByHour = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            ordersByHour.add(AdminDashboardResponse.HourCount.builder()
                    .hour(h)
                    .count(hours[h])
                    .build());
        }
        int windowSize = 3;
        long bestSum = -1;
        int bestStart = 0;
        for (int start = 0; start <= 24 - windowSize; start++) {
            long sum = 0;
            for (int k = 0; k < windowSize; k++) sum += hours[start + k];
            if (sum > bestSum) {
                bestSum = sum;
                bestStart = start;
            }
        }
        AdminDashboardResponse.PeakHourWindow peakWindow = AdminDashboardResponse.PeakHourWindow.builder()
                .startHour(bestStart)
                .endHour(bestStart + windowSize - 1)
                .windowSize(windowSize)
                .total(Math.max(bestSum, 0))
                .build();

        // ===== KPI =====
        var kpi = AdminDashboardResponse.Kpi.builder()
                .totalVehicles(totalVehicles)
                .availableVehicles(availableVehicles)
                .rentedVehicles(rentedVehicles)
                .maintenanceVehicles(maintenanceVehicles)
                .totalOrders(totalOrders)
                .activeOrders(completedOrders)      // nếu muốn hiển thị "đơn hoàn tất" => dùng completedOrders
                .revenueInRange(revenueInRange)
                .totalUsers(totalUsers)
                .admins(admins)
                .staffs(staffs)
                .customers(customers)
                .maintenanceCostInRange(incidentCostInRange)
                .build();

        // ===== IncidentKpi (OrderService) =====
        var incidentKpi = AdminDashboardResponse.IncidentKpi.builder()
                .totalIncidentsInRange(totalIncidentsInRange)
                .openIncidents(pendingIncidents)        // map: pending ~ open
                .inProgressIncidents(processingIncidents)
                .resolvedIncidents(doneIncidents)
                .incidentCostInRange(incidentCostInRange)
                .build();

        return AdminDashboardResponse.builder()
                .kpi(kpi)
                .vehiclesByStatus(List.of(
                        AdminDashboardResponse.LabelCount.builder().label("AVAILABLE").count(availableVehicles).build(),
                        AdminDashboardResponse.LabelCount.builder().label("RENTAL").count(rentedVehicles).build(),
                        AdminDashboardResponse.LabelCount.builder().label("MAINTENANCE").count(maintenanceVehicles).build()
                ))
                .vehiclesByStation(vehiclesByStation)
                .revenueByDay(revenueByDay)
                .avgRating(avgRating)
                .ratingDistribution(ratingDistribution)
                .revenueByStation(revenueByStation)
                .incidentKpi(incidentKpi)
                .incidentsByDay(incidentsByDay)
                .incidentsByStatus(incidentsByStatus)
                // ⬇️ nếu DTO của bạn vẫn là "incidentsBySeverity", tạm thời map theo service_type:
                .incidentsBySeverity(incidentsByType)
                .recentIncidents(recentIncidents)
                .orderByHour(ordersByHour)
                .peakHourWindow(peakWindow)
                .build();
    }

    private static boolean eq(String a, String b) {
        return a != null && a.equalsIgnoreCase(b);
    }
    private static String nz(String s) { return s == null ? "UNKNOWN" : s; }
}
