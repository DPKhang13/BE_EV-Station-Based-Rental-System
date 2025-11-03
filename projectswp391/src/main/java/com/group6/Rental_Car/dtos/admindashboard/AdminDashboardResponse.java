package com.group6.Rental_Car.dtos.admindashboard;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private Map<Integer, Long> ratingDistribution;    // phân phối rating

    // ==== INCIDENT ====
    private IncidentKpi incidentKpi;                  // KPI cho sự cố trong khoảng ngày
    private List<DayCount> incidentsByDay;            // Số incident theo ngày (để vẽ chart)
    private Map<String, Long> incidentsByStatus;      // Phân phối theo status (OPEN/IN_PROGRESS/RESOLVED…)
    private Map<String, Long> incidentsBySeverity;    // Phân phối theo mức độ (LOW/MEDIUM/HIGH/CRITICAL…)
    private List<RecentIncident> recentIncidents;     // Danh sách incident gần nhất

    // ----------------- NESTED DTOs -----------------

    @Data
    @Getter @Setter @Builder
    @AllArgsConstructor @NoArgsConstructor
    public static class Kpi {
        private Long totalVehicles;
        private Long availableVehicles;
        private Long rentedVehicles;
        private Long maintenanceVehicles;

        private Long totalOrders;
        private Long activeOrders;

        private Double revenueInRange;            // doanh thu (range ngày)
        private Long totalUsers;
        private Long admins;
        private Long staffs;
        private Long customers;

        // Giữ lại cho tương thích cũ; nếu đã migrate sang Incident, có thể bỏ sau
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

    // ====== INCIDENT DTOs ======

    @Data
    @Getter @Setter @AllArgsConstructor @NoArgsConstructor @Builder
    public static class IncidentKpi {
        private Long totalIncidentsInRange;       // tổng số incident trong khoảng ngày
        private Long openIncidents;               // trạng thái OPEN/PENDING
        private Long inProgressIncidents;         // đang xử lý
        private Long resolvedIncidents;           // đã xử lý
        private Double incidentCostInRange;       // tổng chi phí incident trong khoảng ngày
    }

    @Data
    @Getter @Setter @AllArgsConstructor @NoArgsConstructor @Builder
    public static class DayCount {
        private LocalDate date;
        private Long count;
    }

    @Data
    @Getter @Setter @AllArgsConstructor @NoArgsConstructor @Builder
    public static class RecentIncident {
        private Integer incidentId;
        private Long vehicleId;
        private String vehicleName;
        private String description;
        private String severity;                  // LOW/MEDIUM/HIGH/CRITICAL
        private String status;                    // OPEN/IN_PROGRESS/RESOLVED
        private LocalDate occurredOn;
        private Double cost;
    }
}
