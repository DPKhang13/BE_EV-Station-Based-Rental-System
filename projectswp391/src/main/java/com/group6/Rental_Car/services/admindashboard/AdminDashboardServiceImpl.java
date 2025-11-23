package com.group6.Rental_Car.services.admindashboard;

import com.group6.Rental_Car.dtos.admindashboard.AdminDashboardResponse;
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
    private final RentalOrderDetailRepository rentalOrderDetailRepository;

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
        // Tính từ RentalOrderDetail có type = "SERVICE" trong khoảng thời gian
        List<com.group6.Rental_Car.entities.RentalOrderDetail> serviceDetailsForKpi = rentalOrderDetailRepository.findAll().stream()
                .filter(d -> "SERVICE".equalsIgnoreCase(d.getType())
                        && d.getStartTime() != null
                        && !d.getStartTime().isBefore(dtFrom)
                        && !d.getStartTime().isAfter(dtTo))
                .toList();
        
        double totalServiceCost = serviceDetailsForKpi.stream()
                .mapToDouble(d -> d.getPrice() != null ? d.getPrice().doubleValue() : 0d)
                .sum();
        long totalServices = serviceDetailsForKpi.size();

        // Phân loại theo description hoặc type
        Map<String, Long> servicesByType = serviceDetailsForKpi.stream()
                .collect(Collectors.groupingBy(
                        d -> {
                            String desc = d.getDescription();
                            if (desc != null && !desc.isEmpty()) {
                                // Lấy từ đầu description (có thể là service type)
                                return desc.length() > 50 ? desc.substring(0, 50) : desc;
                            }
                            return "SERVICE";
                        },
                        Collectors.counting()
                ));
        
        // Phân loại theo status
        Map<String, Long> servicesByStatus = serviceDetailsForKpi.stream()
                .collect(Collectors.groupingBy(
                        d -> Optional.ofNullable(d.getStatus()).orElse("UNKNOWN"),
                        Collectors.counting()
                ));

        // ===== SERVICES BY DAY =====
        // Đếm services theo ngày từ serviceDetailsForKpi
        Map<LocalDate, Long> servicesByDayMap = serviceDetailsForKpi.stream()
                .collect(Collectors.groupingBy(
                        d -> d.getStartTime().toLocalDate(),
                        Collectors.counting()
                ));
        
        List<AdminDashboardResponse.DayCount> servicesByDay = new ArrayList<>();
        for (LocalDate d = fromDate; !d.isAfter(toDate); d = d.plusDays(1)) {
            servicesByDay.add(AdminDashboardResponse.DayCount.builder()
                    .date(d)
                    .count(servicesByDayMap.getOrDefault(d, 0L))
                    .build());
        }

        // ===== RECENT SERVICES =====
        // Lấy 10 service details gần nhất (sắp xếp theo startTime giảm dần)
        var recentServices = serviceDetailsForKpi.stream()
                .sorted((a, b) -> b.getStartTime().compareTo(a.getStartTime()))
                .limit(10)
                .map(d -> AdminDashboardResponse.RecentService.builder()
                        .serviceId(d.getDetailId())
                        .vehicleId(d.getVehicle() != null ? d.getVehicle().getVehicleId() : null)
                        .vehicleName(d.getVehicle() != null ? 
                                (d.getVehicle().getPlateNumber() != null ? d.getVehicle().getPlateNumber() : "N/A") : null)
                        .serviceType(d.getDescription() != null ? d.getDescription() : "SERVICE")
                        .description(d.getDescription())
                        .status(d.getStatus())
                        .cost(d.getPrice() != null ? d.getPrice().doubleValue() : 0d)
                        .occurredAt(d.getStartTime())
                        .resolvedAt(d.getEndTime())
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

                    // Tính doanh thu trung bình mỗi ngày (bao gồm cả SERVICE)
                    long dayCount = java.time.temporal.ChronoUnit.DAYS.between(fromDate, toDate) + 1;
                    Double avgPerDay = sr.getTotalRevenue() / dayCount;

                    // Doanh thu hôm nay (bao gồm cả SERVICE)
                    LocalDateTime todayStart = LocalDate.now().atStartOfDay();
                    LocalDateTime todayEnd = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);
                    Double todayRevenue = calculateRevenueWithService(stationId, todayStart, todayEnd);

                    // Doanh thu tuần này (bao gồm cả SERVICE)
                    LocalDateTime weekStart = LocalDate.now().minusDays(7).atStartOfDay();
                    Double weekRevenue = calculateRevenueWithService(stationId, weekStart, todayEnd);

                    // Doanh thu tháng này (bao gồm cả SERVICE)
                    LocalDateTime monthStart = LocalDate.now().minusDays(30).atStartOfDay();
                    Double monthRevenue = calculateRevenueWithService(stationId, monthStart, todayEnd);

                    // Tính tỷ lệ tăng trưởng (so với kỳ trước) - bao gồm cả SERVICE
                    // Growth Day: so sánh hôm nay với hôm qua
                    LocalDateTime yesterdayStart = LocalDate.now().minusDays(1).atStartOfDay();
                    LocalDateTime yesterdayEnd = LocalDateTime.of(LocalDate.now().minusDays(1), LocalTime.MAX);
                    Double yesterdayRevenue = calculateRevenueWithService(stationId, yesterdayStart, yesterdayEnd);
                    Double growthDay = yesterdayRevenue > 0 ? 
                            ((todayRevenue - yesterdayRevenue) / yesterdayRevenue) * 100 : 0.0;
                    
                    // Growth Week: so sánh tuần này với tuần trước
                    LocalDateTime lastWeekStart = LocalDate.now().minusDays(14).atStartOfDay();
                    LocalDateTime lastWeekEnd = LocalDate.now().minusDays(8).atStartOfDay();
                    Double lastWeekRevenue = calculateRevenueWithService(stationId, lastWeekStart, lastWeekEnd);
                    Double growthWeek = lastWeekRevenue > 0 ? 
                            ((weekRevenue - lastWeekRevenue) / lastWeekRevenue) * 100 : 0.0;
                    
                    // Growth Month: so sánh tháng này với tháng trước
                    LocalDateTime lastMonthStart = LocalDate.now().minusDays(60).atStartOfDay();
                    LocalDateTime lastMonthEnd = LocalDate.now().minusDays(31).atStartOfDay();
                    Double lastMonthRevenue = calculateRevenueWithService(stationId, lastMonthStart, lastMonthEnd);
                    Double growthMonth = lastMonthRevenue > 0 ? 
                            ((monthRevenue - lastMonthRevenue) / lastMonthRevenue) * 100 : 0.0;

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
                .servicesByStatus(servicesByStatus)
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
    
    /**
     * Tính revenue bao gồm cả RENTAL và SERVICE từ RentalOrderDetail
     */
    private Double calculateRevenueWithService(Integer stationId, LocalDateTime start, LocalDateTime end) {
        return rentalOrderDetailRepository.findAll().stream()
                .filter(d -> d.getVehicle() != null && d.getVehicle().getRentalStation() != null
                        && d.getVehicle().getRentalStation().getStationId() != null
                        && d.getVehicle().getRentalStation().getStationId().equals(stationId))
                .filter(d -> d.getStartTime() != null 
                        && !d.getStartTime().isBefore(start) 
                        && !d.getStartTime().isAfter(end))
                .filter(d -> {
                    // Bao gồm cả RENTAL và SERVICE
                    String type = d.getType();
                    return "RENTAL".equalsIgnoreCase(type) || "SERVICE".equalsIgnoreCase(type);
                })
                .filter(d -> {
                    // Chỉ tính các detail có order status hợp lệ
                    if (d.getOrder() == null) return false;
                    String orderStatus = d.getOrder().getStatus();
                    if (orderStatus == null) return false;
                    String upperStatus = orderStatus.toUpperCase();
                    return upperStatus.contains("RENTAL") || 
                           upperStatus.contains("COMPLETED") ||
                           upperStatus.contains("RETURN") ||
                           upperStatus.contains("ACTIVE") ||
                           upperStatus.contains("PAID") ||
                           upperStatus.contains("AWAITING") ||
                           upperStatus.contains("DEPOSITED") ||
                           upperStatus.contains("PENDING");
                })
                .mapToDouble(d -> d.getPrice() != null ? d.getPrice().doubleValue() : 0d)
                .sum();
    }
}
