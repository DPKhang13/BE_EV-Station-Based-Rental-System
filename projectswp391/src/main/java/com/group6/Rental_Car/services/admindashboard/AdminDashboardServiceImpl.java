package com.group6.Rental_Car.services.admindashboard;

import com.group6.Rental_Car.dtos.admindashboard.AdminDashboardResponse;
import com.group6.Rental_Car.entities.OrderService;
import com.group6.Rental_Car.enums.Role;
import com.group6.Rental_Car.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
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
        // Xử lý null và swap nếu cần
        LocalDate fromDate;
        LocalDate toDate;

        if (from == null || to == null) {
            toDate = LocalDate.now();
            fromDate = toDate.minusDays(29);
        } else if (from.isAfter(to)) {
            fromDate = to;
            toDate = from;
        } else {
            fromDate = from;
            toDate = to;
        }

        LocalDateTime dtFrom = fromDate.atStartOfDay();
        LocalDateTime dtTo = LocalDateTime.of(toDate, LocalTime.MAX);

        // ===== VEHICLE KPIs =====
        long totalVehicles = vehicleRepository.count();
        long availableVehicles = vehicleRepository.countByStatus("AVAILABLE");
        long rentedVehicles = vehicleRepository.countByStatus("RENTAL");
        long maintenanceVehicles = vehicleRepository.countByStatus("MAINTENANCE");

        // ===== ORDER KPIs =====
        long totalOrders = rentalOrderRepository.count();
        long completedOrders = rentalOrderRepository.countByStatus("COMPLETED");
        double revenueInRange = Optional.ofNullable(rentalOrderRepository.revenueBetween(dtFrom, dtTo)).orElse(0d);

        // ===== USER KPIs =====
        long totalUsers = userRepository.count();
        long admins = userRepository.countByRole(Role.admin);
        long staffs = userRepository.countByRole(Role.staff);
        long customers = userRepository.countByRole(Role.customer);

        // ===== SERVICE KPIs =====
        List<OrderService> allServices = orderServiceRepository.findAll();
        double totalServiceCost = allServices.stream()
                .mapToDouble(s -> s.getCost() != null ? s.getCost().doubleValue() : 0d)
                .sum();
        long totalServices = allServices.size();

        Map<String, Long> servicesByType = allServices.stream()
                .collect(Collectors.groupingBy(
                        s -> Optional.ofNullable(s.getServiceType()).orElse("UNKNOWN"),
                        Collectors.counting()
                ));

        // ===== SERVICES BY DAY =====
        List<AdminDashboardResponse.DayCount> servicesByDay = new ArrayList<>();
        for (LocalDate d = fromDate; !d.isAfter(toDate); d = d.plusDays(1)) {
            servicesByDay.add(AdminDashboardResponse.DayCount.builder()
                    .date(d)
                    .count(0L)
                    .build());
        }

        // ===== RECENT SERVICES =====
        var recentServices = orderServiceRepository.findAll().stream()
                .limit(10)
                .map(s -> AdminDashboardResponse.RecentService.builder()
                        .serviceId(s.getServiceId())
                        .vehicleId(null)
                        .vehicleName(null)
                        .serviceType(s.getServiceType())
                        .description(s.getDescription())
                        .status(null)
                        .cost(s.getCost() != null ? s.getCost().doubleValue() : 0d)
                        .occurredAt(null)
                        .resolvedAt(null)
                        .build()
                ).toList();

        // ===== REVENUE BY STATION =====
        var revStationRows = rentalOrderRepository.revenuePerStation(dtFrom, dtTo);
        var revenueByStation = revStationRows.stream()
                .map(r -> AdminDashboardResponse.StationRevenue.builder()
                        .stationId(((Number) r[0]).intValue())
                        .stationName((String) r[1])
                        .totalRevenue(r[2] == null ? 0d : ((Number) r[2]).doubleValue())
                        .build())
                .toList();

        // ===== REVENUE BY DAY =====
        var revRows = rentalOrderRepository.revenueByDay(dtFrom, dtTo);
        Map<LocalDate, Double> revMap = revRows.stream().collect(Collectors.toMap(
                r -> ((java.sql.Date) r[0]).toLocalDate(),
                r -> ((Number) r[1]).doubleValue()
        ));
        List<AdminDashboardResponse.DayRevenue> revenueByDay = new ArrayList<>();
        for (LocalDate d = fromDate; !d.isAfter(toDate); d = d.plusDays(1)) {
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

        // ===== VEHICLES BY STATUS =====
        List<AdminDashboardResponse.LabelCount> vehiclesByStatus = Arrays.asList(
                AdminDashboardResponse.LabelCount.builder()
                        .label("AVAILABLE")
                        .count(availableVehicles)
                        .build(),
                AdminDashboardResponse.LabelCount.builder()
                        .label("RENTAL")
                        .count(rentedVehicles)
                        .build(),
                AdminDashboardResponse.LabelCount.builder()
                        .label("MAINTENANCE")
                        .count(maintenanceVehicles)
                        .build()
        );

        // ===== VEHICLES BY STATION =====
        var vehicleStationRows = vehicleRepository.countByStation();
        List<AdminDashboardResponse.StationCount> vehiclesByStation = vehicleStationRows.stream()
                .map(r -> {
                    Integer stationId = r[0] != null ? ((Number) r[0]).intValue() : null;
                    String stationName = (String) r[1];
                    Long total = ((Number) r[2]).longValue();

                    // Đếm xe đang rental tại station này
                    Long rented = stationId != null ? vehicleRepository.countByRentalStation_StationIdAndStatus(stationId, "RENTAL") : 0L;
                    Double utilization = total > 0 ? (rented * 100.0 / total) : 0.0;

                    return AdminDashboardResponse.StationCount.builder()
                            .stationId(stationId)
                            .stationName(stationName != null ? stationName : "Unknown Station")
                            .total(total)
                            .rented(rented)
                            .utilization(utilization)
                            .build();
                })
                .toList();

        // ===== ORDER BY HOUR =====
        var orderHourRows = rentalOrderRepository.countOrdersByHour(dtFrom, dtTo);
        Map<Integer, Long> orderHourMap = orderHourRows.stream()
                .collect(Collectors.toMap(
                        r -> ((Number) r[0]).intValue(),
                        r -> ((Number) r[1]).longValue()
                ));

        List<AdminDashboardResponse.HourCount> orderByHour = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            orderByHour.add(AdminDashboardResponse.HourCount.builder()
                    .hour(h)
                    .count(orderHourMap.getOrDefault(h, 0L))
                    .build());
        }

        // ===== PEAK HOUR WINDOW (3 giờ liên tiếp có nhiều đơn nhất) =====
        AdminDashboardResponse.PeakHourWindow peakHourWindow = null;
        if (!orderHourMap.isEmpty()) {
            int windowSize = 3;
            int bestStart = 0;
            long maxTotal = 0;

            for (int start = 0; start <= 24 - windowSize; start++) {
                long windowTotal = 0;
                for (int i = 0; i < windowSize; i++) {
                    windowTotal += orderHourMap.getOrDefault(start + i, 0L);
                }
                if (windowTotal > maxTotal) {
                    maxTotal = windowTotal;
                    bestStart = start;
                }
            }

            peakHourWindow = AdminDashboardResponse.PeakHourWindow.builder()
                    .startHour(bestStart)
                    .endHour(bestStart + windowSize - 1)
                    .windowSize(windowSize)
                    .total(maxTotal)
                    .build();
        }

        // ===== REVENUE BY STATION ANALYSIS =====
        List<AdminDashboardResponse.StationRevenueAnalysis> revenueByStationAnalysis = revenueByStation.stream()
                .map(sr -> {
                    Integer stationId = sr.getStationId();
                    String stationName = sr.getStationName();

                    // Tính doanh thu trung bình mỗi ngày
                    long dayCount = java.time.temporal.ChronoUnit.DAYS.between(fromDate, toDate) + 1;
                    Double avgPerDay = sr.getTotalRevenue() / dayCount;

                    // Doanh thu hôm nay
                    LocalDateTime todayStart = LocalDate.now().atStartOfDay();
                    LocalDateTime todayEnd = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);
                    Double todayRevenue = Optional.ofNullable(
                            rentalOrderRepository.revenueByStationBetween(stationId, todayStart, todayEnd)
                    ).orElse(0d);

                    // Doanh thu tuần này
                    LocalDateTime weekStart = LocalDate.now().minusDays(7).atStartOfDay();
                    Double weekRevenue = Optional.ofNullable(
                            rentalOrderRepository.revenueByStationBetween(stationId, weekStart, todayEnd)
                    ).orElse(0d);

                    // Doanh thu tháng này
                    LocalDateTime monthStart = LocalDate.now().minusDays(30).atStartOfDay();
                    Double monthRevenue = Optional.ofNullable(
                            rentalOrderRepository.revenueByStationBetween(stationId, monthStart, todayEnd)
                    ).orElse(0d);

                    // Tính tỷ lệ tăng trưởng (so với tuần/tháng trước)
                    // Đơn giản hóa: growth = 0 (có thể mở rộng sau)
                    Double growthDay = 0.0;
                    Double growthWeek = 0.0;
                    Double growthMonth = 0.0;

                    return AdminDashboardResponse.StationRevenueAnalysis.builder()
                            .stationId(stationId)
                            .stationName(stationName)
                            .avgPerDay(avgPerDay)
                            .todayRevenue(todayRevenue)
                            .weekRevenue(weekRevenue)
                            .monthRevenue(monthRevenue)
                            .growthDay(growthDay)
                            .growthWeek(growthWeek)
                            .growthMonth(growthMonth)
                            .build();
                })
                .toList();

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
                .totalServiceCost(totalServiceCost)
                .totalServices(totalServices)
                .build();

        var serviceKpi = AdminDashboardResponse.ServiceKpi.builder()
                .totalServices(totalServices)
                .totalCost(totalServiceCost)
                .servicesByType(servicesByType)
                .servicesByStatus(new HashMap<>())
                .build();

        return AdminDashboardResponse.builder()
                .kpi(kpi)
                .vehiclesByStatus(vehiclesByStatus)
                .vehiclesByStation(vehiclesByStation)
                .revenueByDay(revenueByDay)
                .revenueByStation(revenueByStation)
                .revenueByStationAnalysis(revenueByStationAnalysis)
                .orderByHour(orderByHour)
                .peakHourWindow(peakHourWindow)
                .avgRating(avgRating)
                .ratingDistribution(ratingDistribution)
                .servicesByDay(servicesByDay)
                .serviceKpi(serviceKpi)
                .recentServices(recentServices)
                .build();
    }
}
