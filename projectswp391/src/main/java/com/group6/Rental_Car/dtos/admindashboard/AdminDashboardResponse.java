package com.group6.Rental_Car.dtos.admindashboard;

import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdminDashboardResponse {

    // ======= KPI CHÍNH =======
    private Kpi kpi;                                  // Tổng quan KPI
    private List<LabelCount> vehiclesByStatus;        // available/rented/maintenance
    private List<StationCount> vehiclesByStation;     // xe theo trạm
    private List<DayRevenue> revenueByDay;            // doanh thu theo ngày
    private Double avgRating;                         // rating trung bình
    private Map<Integer, Long> ratingDistribution;    // phân bố rating
    private List<StationRevenue> revenueByStation;    // doanh thu từng trạm
    private List<StationRevenueAnalysis> revenueByStationAnalysis; // phân tích doanh thu
    private List<HourCount> orderByHour;              // đơn hàng theo giờ
    private PeakHourWindow peakHourWindow;            // giờ cao điểm
    private List<DayCount> servicesByDay;          // Dịch vụ theo ngày (dùng cho chart)
    private ServiceKpi serviceKpi;                 // Thống kê dịch vụ (Service KPI)
    // ======= DỊCH VỤ GẦN NHẤT =======
    private List<RecentService> recentServices;       // danh sách dịch vụ gần nhất

    // ----------------- NESTED DTOs -----------------

    @Data
    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Kpi {
        private Long totalVehicles;
        private Long availableVehicles;
        private Long rentedVehicles;
        private Long maintenanceVehicles;

        private Long totalOrders;
        private Long activeOrders;

        private Double revenueInRange;
        private Long totalUsers;
        private Long admins;
        private Long staffs;
        private Long customers;

        private Double totalServiceCost; // tổng chi phí dịch vụ (maintenance/repair/cleaning)
        private Long totalServices;      // tổng số dịch vụ trong khoảng
    }

    // ========== LABEL + COUNT ==========
    @Data
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class LabelCount {
        private String label;
        private Long count;
    }

    // ========== VEHICLE THEO TRẠM ==========
    @Data
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class StationCount {
        private Integer stationId;
        private String stationName;
        private Long total;
        private Long rented;
        private Double utilization;
    }

    // ========== REVENUE ==========
    @Data
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class DayRevenue {
        private LocalDate date;
        private Double total;
    }

    @Data
    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class StationRevenue {
        private Integer stationId;
        private String stationName;
        private Double totalRevenue;
    }

    @Data
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class StationRevenueAnalysis {
        private Integer stationId;
        private String stationName;
        private Double avgPerDay;
        private Double todayRevenue;
        private Double weekRevenue;
        private Double monthRevenue;
        private Double growthDay;
        private Double growthWeek;
        private Double growthMonth;
    }

    // ========== GIỜ THUÊ XE ==========
    @Data
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class HourCount {
        private Integer hour;
        private Long count;
    }

    @Data
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class PeakHourWindow {
        private Integer startHour;
        private Integer endHour;
        private Integer windowSize;
        private Long total;
    }

    // ========== DỊCH VỤ GẦN NHẤT ==========
    @Data
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class RecentService {
        private Long serviceId;
        private Long vehicleId;
        private String vehicleName;
        private String serviceType;   // MAINTENANCE | CLEANING | REPAIR | OTHER
        private String description;
        private String status;        // pending | processing | done | cancelled
        private Double cost;
        private LocalDateTime occurredAt;
        private LocalDateTime resolvedAt;
    }
    @Data
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class DayCount {
        private LocalDate date;  // Ngày
        private Long count;      // Số lượng (service, order, etc.)
    }
    @Data
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class ServiceKpi {
        private Long totalServices;                      // Tổng số dịch vụ
        private Double totalCost;                        // Tổng chi phí dịch vụ
        private Map<String, Long> servicesByType;         // Phân loại theo kiểu dịch vụ (Maintenance, Repair, ...)
        private Map<String, Long> servicesByStatus;       // Phân loại theo trạng thái (pending, done, ...)
    }
}
