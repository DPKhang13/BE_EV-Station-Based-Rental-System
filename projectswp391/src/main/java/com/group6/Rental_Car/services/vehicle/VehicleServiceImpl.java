package com.group6.Rental_Car.services.vehicle;

import com.group6.Rental_Car.dtos.vehicle.VehicleCreateRequest;
import com.group6.Rental_Car.dtos.vehicle.VehicleDetailResponse;
import com.group6.Rental_Car.dtos.vehicle.VehicleResponse;
import com.group6.Rental_Car.dtos.vehicle.VehicleUpdateRequest;
import com.group6.Rental_Car.dtos.vehicle.VehicleUpdateStatusRequest;
import com.group6.Rental_Car.entities.RentalOrderDetail;
import com.group6.Rental_Car.entities.Vehicle;
import com.group6.Rental_Car.entities.VehicleModel;
import com.group6.Rental_Car.entities.VehicleTimeline;
import com.group6.Rental_Car.exceptions.BadRequestException;
import com.group6.Rental_Car.exceptions.ConflictException;
import com.group6.Rental_Car.exceptions.ResourceNotFoundException;
import com.group6.Rental_Car.repositories.RentalOrderDetailRepository;
import com.group6.Rental_Car.repositories.RentalStationRepository;
import com.group6.Rental_Car.repositories.VehicleModelRepository;
import com.group6.Rental_Car.repositories.VehicleRepository;
import com.group6.Rental_Car.repositories.VehicleTimelineRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.group6.Rental_Car.utils.ValidationUtil.*;

@Service
@RequiredArgsConstructor
public class VehicleServiceImpl implements VehicleService {
    private static final Set<String> ALLOWED_STATUS = Set.of("available", "rented", "maintenance","BOOKED");
    private static final Set<String> ALLOWED_VARIANT = Set.of("air", "pro", "plus");

    private final VehicleRepository vehicleRepository;
    private final RentalStationRepository rentalStationRepository;
    private final VehicleTimelineRepository vehicleTimelineRepository;
    private final RentalOrderDetailRepository rentalOrderDetailRepository;
    private final VehicleModelService vehicleModelService; // <-- thay vì repository
    private final ModelMapper modelMapper;
    private final VehicleModelRepository vehicleModelRepository;

    @Override
    public VehicleResponse createVehicle(VehicleCreateRequest req) {
        Vehicle vehicle = modelMapper.map(req, Vehicle.class);

        //Validate thuộc tính của Vehicle
        String plate = requireNonBlank(trim(req.getPlateNumber()), "plateNumber");
        ensureMaxLength(plate, 20, "plateNumber");
        if (vehicleRepository.existsByPlateNumber(plate))
            throw new ConflictException("plateNumber already exists");

        String status = requireNonBlank(trim(req.getStatus()), "status");
        ensureInSetIgnoreCase(status, ALLOWED_STATUS, "status");

        Integer stationId = requireNonNull(req.getStationId(), "stationId");
        var st = rentalStationRepository.findById(stationId)
                .orElseThrow(() -> new ResourceNotFoundException("Station not found: " + stationId));

        Integer seat = req.getSeatCount();
        String normalizedVariant = validateVariantBySeatCount(seat, req.getVariant());


        if (req.getStationId() != null) {
            var station = rentalStationRepository.findById(req.getStationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Rental Station not found "));
            vehicle.setRentalStation(station);
        }

        vehicleRepository.save(vehicle);

        VehicleCreateRequest attrReq = new VehicleCreateRequest();
        attrReq.setBrand(req.getBrand());
        attrReq.setColor(req.getColor());
        attrReq.setSeatCount(seat);
        attrReq.setVariant(normalizedVariant);// << dùng biến đã validate/normalize
        attrReq.setBatteryStatus(req.getBatteryStatus());
        attrReq.setBatteryCapacity(req.getBatteryCapacity());
        attrReq.setCarmodel(req.getCarmodel());

        VehicleModel attr = vehicleModelService.createModel(vehicle, attrReq);


        return vehicleModelService.convertToDto(vehicle, attr);
    }

    @Override
    public VehicleResponse updateVehicle(Long vehicleId, VehicleUpdateRequest req) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found "));

        // status (nếu client gửi lên)
        if (req.getStatus() != null) {
            String status = req.getStatus().trim().toLowerCase();
            if (!status.equalsIgnoreCase("available") && !status.equalsIgnoreCase("rented") && !status.equalsIgnoreCase("maintenance")) {
                throw new BadRequestException("status must be one of: available|rented|maintenance");
            }
            vehicle.setStatus(status);
        }

        // station (nếu client gửi lên)
        if (req.getStationId() != null) {
            var station = rentalStationRepository.findById(req.getStationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Rental Station not found "));
            vehicle.setRentalStation(station);
        }


        modelMapper.map(req, vehicle);
        vehicle = vehicleRepository.save(vehicle);

        // Lấy attribute hiện tại để tính giá trị hiệu lực khi client chỉ gửi 1 trong 2
        VehicleModel currentAttr = vehicleModelService.findByVehicle(vehicle);

        Integer effectiveSeat = (req.getSeatCount() != null)
                ? req.getSeatCount()
                : (currentAttr != null ? currentAttr.getSeatCount() : null);

        String effectiveVariantRaw = (req.getVariant() != null)
                ? req.getVariant()
                : (currentAttr != null ? currentAttr.getVariant() : null);

        String normalizedVariant = null;
        if (effectiveSeat != null || effectiveVariantRaw != null) {
            normalizedVariant = validateVariantBySeatCount(effectiveSeat, effectiveVariantRaw);
        }

        VehicleUpdateRequest attrReq = new VehicleUpdateRequest();
        if (req.getSeatCount() != null) attrReq.setSeatCount(effectiveSeat);
        if (req.getVariant() != null || (effectiveSeat != null && currentAttr == null)) {
            attrReq.setVariant(normalizedVariant);
        }
        attrReq.setBrand(req.getBrand());
        attrReq.setColor(req.getColor());
        attrReq.setBatteryStatus(req.getBatteryStatus());
        attrReq.setBatteryCapacity(req.getBatteryCapacity());
        attrReq.setCarmodel(req.getCarmodel());

        vehicleRepository.save(vehicle);

        VehicleModel attr = vehicleModelService.updateModel(vehicle, req);

        return vehicleModelService.convertToDto(vehicle, attr);
    }

    @Override
    public VehicleResponse getVehicleById(Long id) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));

        var attr = vehicleModelService.findByVehicle(vehicle);
        return vehicleModelService.convertToDto(vehicle, attr);
    }

    @Override
    public void deleteVehicle(Long id) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));

        vehicleModelService.deleteByVehicle(vehicle);
        vehicleRepository.delete(vehicle);
    }

    @Override
    public List<VehicleResponse> getAllVehicles() {
        return vehicleRepository.findAll().stream()
                .map(v -> vehicleModelService.convertToDto(v, vehicleModelService.findByVehicle(v)))
                .collect(Collectors.toList());
    }

    @Override
    public List<VehicleResponse> getVehiclesByStation(Integer stationId) {
        // Validate stationId
        if (stationId == null || stationId <= 0) {
            throw new BadRequestException("stationId phải là số dương");
        }

        // Kiểm tra station có tồn tại không
        rentalStationRepository.findById(stationId)
                .orElseThrow(() -> new ResourceNotFoundException("Rental Station không tồn tại: " + stationId));

        // Lấy tất cả xe theo station, sắp xếp theo biển số
        return vehicleRepository.findByRentalStation_StationIdOrderByPlateNumberAsc(stationId).stream()
                .map(v -> vehicleModelService.convertToDto(v, vehicleModelService.findByVehicle(v)))
                .collect(Collectors.toList());
    }

    @Override
    public List<VehicleResponse> getVehiclesByCarmodel(String carmodel) {
        if (carmodel == null || carmodel.isBlank()) {
            throw new BadRequestException("carmodel không được để trống");
        }

        List<VehicleModel> models = vehicleModelRepository.findByCarmodelIgnoreCase(carmodel.trim());
        return models.stream()
                .map(model -> vehicleModelService.convertToDto(model.getVehicle(), model))
                .collect(Collectors.toList());
    }

    @Override
    public VehicleDetailResponse getVehicleDetailById(Long vehicleId) {
        // Lấy xe
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle không tồn tại: " + vehicleId));

        // Lấy model của xe
        VehicleModel model = vehicleModelService.findByVehicle(vehicle);

        // Tạo response cơ bản
        VehicleDetailResponse response = VehicleDetailResponse.builder()
                .vehicleId(vehicle.getVehicleId())
                .plateNumber(vehicle.getPlateNumber())
                .status(vehicle.getStatus())
                .vehicleName(vehicle.getVehicleName())
                .description(vehicle.getDescription())
                .hasBooking(false)
                .bookingNote("Chưa có đơn thuê")
                .build();

        // Thêm thông tin station
        if (vehicle.getRentalStation() != null) {
            response.setStationId(vehicle.getRentalStation().getStationId());
            response.setStationName(vehicle.getRentalStation().getName());
        }

        // Thêm thông tin model
        if (model != null) {
            response.setBrand(model.getBrand());
            response.setColor(model.getColor());
            response.setTransmission(model.getTransmission());
            response.setSeatCount(model.getSeatCount());
            response.setYear(model.getYear());
            response.setVariant(model.getVariant());
            response.setBatteryStatus(model.getBatteryStatus());
            response.setBatteryCapacity(model.getBatteryCapacity());
            response.setCarmodel(model.getCarmodel());
        }

        // Tìm đơn thuê đang diễn ra (status = RENTAL)
        List<RentalOrderDetail> activeRentals = rentalOrderDetailRepository
                .findByVehicle_VehicleIdAndStatusIn(vehicleId, List.of("RENTAL", "PENDING"));

        if (!activeRentals.isEmpty()) {
            // Lấy chi tiết đơn thuê gần đây nhất
            RentalOrderDetail activeDetail = activeRentals.getFirst();

            if (activeDetail.getOrder() != null && activeDetail.getOrder().getCustomer() != null) {
                response.setHasBooking(true);
                response.setCustomerName(activeDetail.getOrder().getCustomer().getFullName());
                response.setCustomerPhone(activeDetail.getOrder().getCustomer().getPhone());
                response.setCustomerEmail(activeDetail.getOrder().getCustomer().getEmail());
                response.setRentalStartDate(activeDetail.getStartTime());
                response.setRentalEndDate(activeDetail.getEndTime());
                response.setRentalOrderStatus(activeDetail.getOrder().getStatus());
                response.setBookingNote("Khách: " + activeDetail.getOrder().getCustomer().getFullName() +
                        " | Từ: " + activeDetail.getStartTime() +
                        " | Đến: " + activeDetail.getEndTime());
            }
        }

        return response;
    }

    @Override
    public VehicleResponse updateStatusVehicle(Long vehicleId, VehicleUpdateStatusRequest req) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));

        String oldStatus = vehicle.getStatus();
        String newStatus = null;

        if (req.getStatus() != null && !req.getStatus().isBlank()) {
            newStatus = req.getStatus().trim().toUpperCase();
            vehicle.setStatus(newStatus);
        }

        vehicleRepository.save(vehicle);

        // Xử lý timeline dựa trên status mới
        if (newStatus != null) {
            handleTimelineOnStatusChange(vehicle, oldStatus, newStatus);
        }

        VehicleModel model = vehicleModelService.findByVehicle(vehicle);
        if (model == null) {
            throw new ResourceNotFoundException("VehicleModel not found for vehicleId: " + vehicleId);
        }

        if (req.getBatteryStatus() != null && !req.getBatteryStatus().isBlank()) {
            model.setBatteryStatus(req.getBatteryStatus().trim());
            vehicleModelRepository.save(model);
        }

        return vehicleModelService.convertToDto(vehicle, model);
    }

    /**
     * Xử lý timeline khi staff thay đổi status của xe
     */
    private void handleTimelineOnStatusChange(Vehicle vehicle, String oldStatus, String newStatus) {
        // Nếu chuyển về AVAILABLE → chỉ xóa timeline MAINTENANCE/CHECKING (giữ lại timeline booking)
        if ("AVAILABLE".equalsIgnoreCase(newStatus)) {
            deleteMaintenanceAndCheckingTimelines(vehicle.getVehicleId());

            //  Kiểm tra xem xe có booking trong tương lai không
            checkAndSetBookedStatus(vehicle);
        }
        // Nếu chuyển sang MAINTENANCE → tạo timeline MAINTENANCE
        else if ("MAINTENANCE".equalsIgnoreCase(newStatus)) {
            createMaintenanceTimeline(vehicle, "Xe đang bảo trì", null);
        }
        // Nếu chuyển sang CHECKING → tạo timeline CHECKING
        else if ("CHECKING".equalsIgnoreCase(newStatus)) {
            createCheckingTimeline(vehicle, "Xe đang được kiểm tra");
        }
    }

    /**
     * Kiểm tra và tự động set status BOOKED nếu xe có booking trong tương lai
     */
    private void checkAndSetBookedStatus(Vehicle vehicle) {
        LocalDateTime now = LocalDateTime.now();

        // Tìm timeline BOOKED trong tương lai
        List<VehicleTimeline> futureBookings = vehicleTimelineRepository.findByVehicle_VehicleId(vehicle.getVehicleId())
                .stream()
                .filter(t -> "BOOKED".equalsIgnoreCase(t.getStatus())
                        && (t.getStartTime() == null || t.getStartTime().isAfter(now) || t.getStartTime().isEqual(now))
                        && ("ORDER_RENTAL".equals(t.getSourceType()) || "ORDER_PICKUP".equals(t.getSourceType())))
                .collect(Collectors.toList());

        if (!futureBookings.isEmpty()) {
            // Có booking trong tương lai → set xe thành BOOKED
            vehicle.setStatus("BOOKED");
            vehicleRepository.save(vehicle);
            System.out.println("✅ Xe " + vehicle.getVehicleId() + " có booking trong tương lai → Tự động set status = BOOKED");
        }
    }

    /**
     * Xóa chỉ timeline MAINTENANCE và CHECKING (giữ lại timeline booking)
     */
    private void deleteMaintenanceAndCheckingTimelines(Long vehicleId) {
        if (vehicleId == null) return;

        List<VehicleTimeline> timelines = vehicleTimelineRepository.findByVehicle_VehicleId(vehicleId);

        // Chỉ xóa timeline có sourceType là VEHICLE_MAINTENANCE hoặc VEHICLE_CHECKING
        List<VehicleTimeline> toDelete = timelines.stream()
                .filter(t -> "VEHICLE_MAINTENANCE".equals(t.getSourceType())
                        || "VEHICLE_CHECKING".equals(t.getSourceType()))
                .collect(Collectors.toList());

        if (!toDelete.isEmpty()) {
            vehicleTimelineRepository.deleteAll(toDelete);
        }
    }

    /**
     * Tạo timeline MAINTENANCE
     */
    private void createMaintenanceTimeline(Vehicle vehicle, String note, LocalDateTime endTime) {
        // Xóa chỉ timeline MAINTENANCE/CHECKING cũ (giữ lại booking)
        deleteMaintenanceAndCheckingTimelines(vehicle.getVehicleId());

        LocalDateTime now = LocalDateTime.now();
        VehicleTimeline timeline = VehicleTimeline.builder()
                .vehicle(vehicle)
                .day(now.toLocalDate())
                .startTime(now)
                .endTime(endTime != null ? endTime : now.plusDays(7)) // Mặc định 7 ngày
                .status("MAINTENANCE")
                .sourceType("VEHICLE_MAINTENANCE")
                .note(note != null ? note : "Xe đang bảo trì")
                .build();
        vehicleTimelineRepository.save(timeline);
    }

    /**
     * Tạo timeline CHECKING
     */
    private void createCheckingTimeline(Vehicle vehicle, String note) {
        // Xóa chỉ timeline MAINTENANCE/CHECKING cũ (giữ lại booking)
        deleteMaintenanceAndCheckingTimelines(vehicle.getVehicleId());

        LocalDateTime now = LocalDateTime.now();
        VehicleTimeline timeline = VehicleTimeline.builder()
                .vehicle(vehicle)
                .day(now.toLocalDate())
                .startTime(now)
                .endTime(now.plusDays(1)) // Mặc định 1 ngày
                .status("CHECKING")
                .sourceType("VEHICLE_CHECKING")
                .note(note != null ? note : "Xe đang được kiểm tra")
                .build();
        vehicleTimelineRepository.save(timeline);
    }
}
