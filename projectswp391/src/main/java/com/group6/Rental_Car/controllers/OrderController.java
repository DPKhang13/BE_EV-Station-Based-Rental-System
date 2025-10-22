package com.group6.Rental_Car.controllers;

import com.group6.Rental_Car.dtos.order.OrderCreateRequest;
import com.group6.Rental_Car.dtos.order.OrderResponse;
import com.group6.Rental_Car.dtos.order.OrderUpdateRequest;
import com.group6.Rental_Car.services.order.RentalOrderService;
import com.group6.Rental_Car.utils.JwtUserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/order")
@Tag(name = "Api Order", description = "Create, update, delete, pickup, return, get orders")
@RequiredArgsConstructor
public class OrderController {

    private final RentalOrderService rentalOrderService;

    // ==============================
    // 1️⃣ Tạo đơn thuê xe (User)
    // ==============================
    @PostMapping("/create")
    public ResponseEntity<OrderResponse> create(@RequestBody OrderCreateRequest request) {
        OrderResponse response = rentalOrderService.createOrder(request);
        return ResponseEntity.ok(response);
    }

    // ==============================
    // 2️⃣ Lấy tất cả đơn (Admin/Staff)
    // ==============================
    @GetMapping("/getAll")
    public ResponseEntity<List<OrderResponse>> getAll() {
        List<OrderResponse> orders = rentalOrderService.getRentalOrders();
        return ResponseEntity.ok(orders);
    }

    // ==============================
    // 3️⃣ Lấy danh sách đơn của chính khách hàng
    // ==============================
    @GetMapping("/get/my-orders")
    public ResponseEntity<List<OrderResponse>> getMyOrders(@AuthenticationPrincipal JwtUserDetails userDetails) {
        UUID customerId = userDetails.getUserId();
        List<OrderResponse> orders = rentalOrderService.findByCustomer_UserId(customerId);
        return ResponseEntity.ok(orders);
    }

    // ==============================
    // 4️⃣ Cập nhật đơn (Admin/Staff)
    // ==============================
    @PutMapping("/update/{orderId}")
    public ResponseEntity<OrderResponse> update(
            @PathVariable UUID orderId,
            @RequestBody OrderUpdateRequest request
    ) {
        OrderResponse response = rentalOrderService.updateOrder(orderId, request);
        return ResponseEntity.ok(response);
    }

    // ==============================
    // 5️⃣ Xóa đơn (Admin)
    // ==============================
    @DeleteMapping("/delete/{orderId}")
    public ResponseEntity<String> delete(@PathVariable UUID orderId) {
        rentalOrderService.deleteOrder(orderId);
        return ResponseEntity.ok("Deleted order successfully");
    }

    // ==============================
    // 6️⃣ Nhân viên xác nhận giao xe (Pickup)
    // ==============================
    @PostMapping("/{orderId}/pickup")
    public ResponseEntity<OrderResponse> confirmPickup(@PathVariable UUID orderId) {
        OrderResponse response = rentalOrderService.confirmPickup(orderId);
        return ResponseEntity.ok(response);
    }

    // ==============================
    // 7️⃣ Nhân viên xác nhận trả xe (Return)
    // ==============================
    @PostMapping("/{orderId}/return")
    public ResponseEntity<OrderResponse> confirmReturn(
            @PathVariable UUID orderId,
            @RequestBody(required = false) OrderReturnRequest request
    ) {
        Integer actualHours = (request != null) ? request.getActualHours() : null;
        OrderResponse response = rentalOrderService.confirmReturn(orderId, actualHours);
        return ResponseEntity.ok(response);
    }
}
