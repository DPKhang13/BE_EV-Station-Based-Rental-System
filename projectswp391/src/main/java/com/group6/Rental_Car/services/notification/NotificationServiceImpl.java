package com.group6.Rental_Car.services.notification;

import com.group6.Rental_Car.dtos.notification.NotificationCreateRequest;
import com.group6.Rental_Car.dtos.notification.NotificationResponse;
import com.group6.Rental_Car.dtos.notification.NotificationUpdateRequest;
import com.group6.Rental_Car.entities.Notification;
import com.group6.Rental_Car.entities.User;
import com.group6.Rental_Car.exceptions.BadRequestException;
import com.group6.Rental_Car.exceptions.ResourceNotFoundException;
import com.group6.Rental_Car.repositories.NotificationRepository;
import com.group6.Rental_Car.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.group6.Rental_Car.utils.ValidationUtil.*;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {
    private final NotificationRepository  notificationRepository;
    private final UserRepository userRepository;

    @Override
    public NotificationResponse create (NotificationCreateRequest req){
        UUID userId = requireNonNull(req.getUserId(), "userId");
        User user = userRepository.findById(userId)
                .orElseThrow(()->new ResourceNotFoundException("User not found" + userId));

        String msg = requireNonBlank(trim(req.getMessage()), "message");
        ensureMaxLength(msg, 255, "message");

        Notification n = new Notification();
        n.setUser(user);
        n.setMessage(msg);
        n.setCreatedAt(LocalDateTime.now()); // set server time

        n = notificationRepository.save(n);
        return toResponse(n);
    }

    @Override
    public NotificationResponse update (Integer notificationId,NotificationUpdateRequest req){
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));

        if(req.getMessage()!=null){
            String msg = trim(req.getMessage());
            if(msg==null || msg.isEmpty()) throw new BadRequestException("message is required");
            ensureMaxLength(msg, 255, "message");
            n.setMessage(msg);
        }

        n = notificationRepository.save(n);
        return toResponse(n);
    }
    @Override
    public void delete(Integer notificationId) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));
        notificationRepository.delete(n); // xóa cứng
    }

    @Override
    public NotificationResponse getById(Integer notificationId) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));
        return toResponse(n);
    }

    public List<NotificationResponse> listAll() {
        return notificationRepository.findAll().stream().map(this::toResponse).toList();
    }

    private NotificationResponse toResponse(Notification n) {
        if (n.getUser() == null) throw new BadRequestException("Notification has no user");
        NotificationResponse dto = new NotificationResponse();
        dto.setNotificationId(n.getNotificationId());
        dto.setUserId(n.getUser().getUserId());
        dto.setMessage(n.getMessage());
        dto.setCreatedAt(n.getCreatedAt());
        return dto;
    }
}
