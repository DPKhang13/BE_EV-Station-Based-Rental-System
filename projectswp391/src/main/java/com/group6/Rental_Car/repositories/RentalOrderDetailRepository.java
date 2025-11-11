package com.group6.Rental_Car.repositories;

import com.group6.Rental_Car.entities.RentalOrderDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RentalOrderDetailRepository extends JpaRepository<RentalOrderDetail, Long> {

    // Lấy toàn bộ chi tiết theo order_id
    List<RentalOrderDetail> findByOrder_OrderId(UUID orderId);

    // Lấy toàn bộ chi tiết theo vehicle_id
    List<RentalOrderDetail> findByVehicle_VehicleId(Long vehicleId);

    // Lấy các chi tiết đang hoạt động của 1 xe
    List<RentalOrderDetail> findByVehicle_VehicleIdAndStatusIn(Long vehicleId, List<String> statuses);

    // Lấy các chi tiết đang hoạt động trong 1 order
    List<RentalOrderDetail> findByOrder_OrderIdAndStatusIn(UUID orderId, List<String> statuses);

    // Lấy tất cả detail đang pending (chưa xử lý)
    List<RentalOrderDetail> findByStatus(String status);
}
