package com.group6.Rental_Car.dtos.admindashboard;
import lombok.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
@Data
@Getter @Setter @Builder
@AllArgsConstructor @NoArgsConstructor
public class AdminDashboardResponse {
    private Kpi kpi;                                  // Các chỉ số tổng quan
    private List<LabelCount> vehiclesByStatus;        // available/rented/maintenance
    private List<StationCount> vehiclesByStation;     // theo trạm
    private List<DayRevenue> revenueByDay;            // doanh thu theo ngày (range)
    private Double avgRating;                         // rating trung bình (1..5)
    private Map<Integer, Long> ratingDistribution;// phân phối rating

    @Data
    @Getter @Setter @Builder
    @AllArgsConstructor @NoArgsConstructor
    public static class Kpi {
        private Long totalVehicles;
        private Long availableVehicles;
        private Long rentedVehicles;
        private Long maintenanceVehicles;

        private Long totalOrders;
        private Long activeOrders;       // tuỳ bạn định nghĩa status

        private Double revenueInRange;   // doanh thu (range ngày)
        private Long totalUsers;
        private Long admins;
        private Long staffs;
        private Long customers;
        private Double maintenanceCostInRange;
    }

    @Data
    @Getter @Setter @AllArgsConstructor @NoArgsConstructor @Builder
    public static class LabelCount {
        private String label;
        private Long count;
    }

    @Data
    @Getter @Setter @AllArgsConstructor @NoArgsConstructor @Builder
    public static class StationCount {
        private Integer stationId;
        private String stationName;
        private Long total;
    }

    @Data
    @Getter @Setter @AllArgsConstructor @NoArgsConstructor @Builder
    public static class DayRevenue {
        private LocalDate date;
        private Double total;
    }
}
