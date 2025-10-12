package com.group6.Rental_Car.controllers;

import com.group6.Rental_Car.dtos.order.OrderCreateRequest;

import com.group6.Rental_Car.dtos.order.OrderResponse;
import com.group6.Rental_Car.dtos.order.OrderUpdateRequest;
import com.group6.Rental_Car.services.order.RentalOrderService;
import com.group6.Rental_Car.utils.JwtUserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/order")
@Tag(name = "Api Order",description = "Creat,update,deleted,lay danh sach theo orderid, lay danh sach theo id khach hang")
public class OrderController {
    @Autowired
    private RentalOrderService rentalOrderService;

    @PostMapping("/create")
    public ResponseEntity<?> create(@RequestBody OrderCreateRequest orderCreateRequest) {
        OrderResponse response = rentalOrderService.createOrder(orderCreateRequest);
        return ResponseEntity.ok().body(response);

    }
    @GetMapping("/getAll")
    public ResponseEntity<List<?>> getAll() {
        List<OrderResponse> getOrder = rentalOrderService.getRentalOrders();
        return ResponseEntity.ok(getOrder);

    }
    @GetMapping("/get/my-orders")
    public ResponseEntity<List<?>> getMyOrders(
            @AuthenticationPrincipal JwtUserDetails userDetails) {

        UUID customerId = userDetails.getUserId();
        List<OrderResponse> orders = rentalOrderService.findByCustomer_UserId(customerId);
        return ResponseEntity.ok(orders);
    }
    @PutMapping("/update/{orderId}")
    public ResponseEntity<?> update(@PathVariable UUID orderId, @RequestBody OrderUpdateRequest orderUpdateRequest) {
        OrderResponse response = rentalOrderService.updateOrder(orderId, orderUpdateRequest);
        return ResponseEntity.ok().body(response);

    }
    @DeleteMapping("/delete/{orderId}")
    public ResponseEntity<?> delete(@PathVariable UUID orderId) {
        rentalOrderService.deleteOrder(orderId);
        return ResponseEntity.ok("Deleted order successfully");
    }

}
