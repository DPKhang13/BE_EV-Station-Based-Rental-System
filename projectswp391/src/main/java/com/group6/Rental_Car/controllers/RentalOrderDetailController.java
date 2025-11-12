package com.group6.Rental_Car.controllers;

import com.group6.Rental_Car.dtos.orderdetail.OrderDetailCreateRequest;
import com.group6.Rental_Car.dtos.orderdetail.OrderDetailResponse;
import com.group6.Rental_Car.services.orderdetails.RentalOrderDetailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/order-details")
@RequiredArgsConstructor
@Tag(name = "Order Detail API", description = "Quản lý chi tiết đơn thuê (rentalorder_detail)")
public class RentalOrderDetailController {

    private final RentalOrderDetailService rentalOrderDetailService;

    // ======================== CREATE ========================
    @Operation(summary = "Tạo chi tiết đơn thuê mới",
            description = "Thêm 1 chi tiết thuê xe mới (rentalorder_detail) vào đơn hàng cụ thể.")
    @PostMapping
    public ResponseEntity<OrderDetailResponse> createOrderDetail(
            @RequestBody OrderDetailCreateRequest request
    ) {
        OrderDetailResponse response = rentalOrderDetailService.createDetail(request);
        return ResponseEntity.ok(response);
    }

    // ======================== UPDATE ========================
    @Operation(summary = "Cập nhật chi tiết đơn thuê",
            description = "Chỉnh sửa thông tin của 1 chi tiết thuê xe theo ID chi tiết (detailId).")
    @PutMapping("/{detailId}")
    public ResponseEntity<OrderDetailResponse> updateOrderDetail(
            @PathVariable Long detailId,
            @RequestBody OrderDetailCreateRequest request
    ) {
        OrderDetailResponse response = rentalOrderDetailService.updateDetail(detailId, request);
        return ResponseEntity.ok(response);
    }

    // ======================== DELETE ========================
    @Operation(summary = "Xóa chi tiết đơn thuê",
            description = "Xóa chi tiết thuê xe theo ID (detailId) khỏi hệ thống.")
    @DeleteMapping("/{detailId}")
    public ResponseEntity<Void> deleteOrderDetail(@PathVariable Long detailId) {
        rentalOrderDetailService.deleteDetail(detailId);
        return ResponseEntity.noContent().build();
    }

    // ======================== READ ========================
    @Operation(summary = "Lấy danh sách chi tiết của 1 đơn hàng",
            description = "Trả về toàn bộ chi tiết thuê xe của 1 đơn hàng cụ thể theo orderId.")
    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<OrderDetailResponse>> getDetailsByOrder(@PathVariable UUID orderId) {
        List<OrderDetailResponse> responseList = rentalOrderDetailService.getDetailsByOrder(orderId);
        return ResponseEntity.ok(responseList);
    }

    @Operation(summary = "Lấy danh sách chi tiết theo xe",
            description = "Trả về toàn bộ chi tiết thuê của 1 xe cụ thể (vehicleId).")
    @GetMapping("/vehicle/{vehicleId}")
    public ResponseEntity<List<OrderDetailResponse>> getDetailsByVehicle(@PathVariable Long vehicleId) {
        List<OrderDetailResponse> responseList = rentalOrderDetailService.getDetailsByVehicle(vehicleId);
        return ResponseEntity.ok(responseList);
    }

    @Operation(summary = "Lấy danh sách chi tiết thuê đang hoạt động theo xe",
            description = "Trả về các chi tiết thuê xe có trạng thái đang hoạt động (pending, active, confirmed).")
    @GetMapping("/vehicle/{vehicleId}/active")
    public ResponseEntity<List<OrderDetailResponse>> getActiveDetailsByVehicle(@PathVariable Long vehicleId) {
        List<OrderDetailResponse> responseList = rentalOrderDetailService.getActiveDetailsByVehicle(vehicleId);
        return ResponseEntity.ok(responseList);
    }

    @Operation(summary = "Lấy danh sách chi tiết thuê đang hoạt động theo đơn hàng",
            description = "Trả về các chi tiết thuê của 1 đơn hàng có trạng thái đang hoạt động (pending, active, confirmed).")
    @GetMapping("/order/{orderId}/active")
    public ResponseEntity<List<OrderDetailResponse>> getActiveDetailsByOrder(@PathVariable UUID orderId) {
        List<OrderDetailResponse> responseList = rentalOrderDetailService.getActiveDetailsByOrder(orderId);
        return ResponseEntity.ok(responseList);
    }
    @GetMapping("/order/staff/{orderId}")
    public ResponseEntity<List<OrderDetailResponse>> getOrderDetails(
            @PathVariable UUID orderId
    ) {
        return ResponseEntity.ok(rentalOrderDetailService.getDetailsByOrder(orderId));
    }
}
