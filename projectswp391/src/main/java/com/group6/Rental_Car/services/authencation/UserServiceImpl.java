package com.group6.Rental_Car.services.authencation;

import com.group6.Rental_Car.dtos.loginpage.AccountDto;
import com.group6.Rental_Car.dtos.loginpage.AccountDtoResponse;
import com.group6.Rental_Car.dtos.loginpage.RegisterAccountDto;
import com.group6.Rental_Car.dtos.otpverify.OtpRequest;
import com.group6.Rental_Car.dtos.stafflist.StaffCreateRequest;
import com.group6.Rental_Car.dtos.stafflist.StaffResponse;
import com.group6.Rental_Car.dtos.verifyfile.UserVerificationResponse;
import com.group6.Rental_Car.entities.Photo;
import com.group6.Rental_Car.entities.RentalStation;
import com.group6.Rental_Car.entities.User;
import com.group6.Rental_Car.enums.Role;
import com.group6.Rental_Car.enums.UserStatus;
import com.group6.Rental_Car.exceptions.*;
import com.group6.Rental_Car.repositories.EmployeeScheduleRepository;
import com.group6.Rental_Car.repositories.PhotoRepository;
import com.group6.Rental_Car.repositories.RentalStationRepository;
import com.group6.Rental_Car.repositories.UserRepository;
import com.group6.Rental_Car.services.otpmailsender.OtpMailService;
import com.group6.Rental_Car.utils.JwtUserDetails;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final OtpMailService otpMailService;
    private final PasswordEncoder passwordEncoder;
    private final ModelMapper modelMapper;
    private final PhotoRepository photoRepository;
    private final RentalStationRepository rentalStationRepository;
    private final EmployeeScheduleRepository employeeScheduleRepository;
    // ------- Helper -------
    private AccountDtoResponse mapToResponse(User user) {
        return AccountDtoResponse.builder()
                .userId(user.getUserId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole())
                .status(user.getStatus())
                .stationId(
                        user.getRentalStation() != null
                                ? user.getRentalStation().getStationId()
                                : null
                )
                .build();
    }

    // ========== REGISTER ==========
    @Override
    public AccountDtoResponse registerByEmail(RegisterAccountDto account) {
        if (userRepository.existsByEmail(account.getEmail().toLowerCase())) {
            throw new EmailAlreadyExistsException("Email đã tồn tại: " + account.getEmail());
        }

        String otp = otpMailService.generateAndSendOtp(account.getEmail());

        User user = modelMapper.map(account, User.class);
        user.setEmail(account.getEmail().toLowerCase());
        user.setPassword(passwordEncoder.encode(account.getPassword()));
        user.setPhone(account.getPhone());
        user.setRole(Role.customer);
        user.setStatus(UserStatus.NEED_OTP);
        user.setRentalStation(null);
        userRepository.save(user);

        return mapToResponse(user);
    }

    // ========== LOGIN ==========
    @Override
    public AccountDtoResponse loginByEmail(AccountDto account) {
        User user = userRepository.findByEmail(account.getEmail().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với email: " + account.getEmail()));

        if (!passwordEncoder.matches(account.getPassword(), user.getPassword())) {
            throw new InvalidPasswordException("Sai mật khẩu");
        }

        if (user.getStatus() != UserStatus.ACTIVE && user.getStatus() != UserStatus.ACTIVE_PENDING) {
            throw new RuntimeException("Tài khoản chưa được kích hoạt hoặc bị khóa");
        }

        return mapToResponse(user);
    }

    // ========== GET ACCOUNT DETAILS ==========
    @Override
    public JwtUserDetails getAccountDetails(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy userId: " + userId));

        return JwtUserDetails.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }

    // ========== VERIFY OTP ==========
    @Override
    public AccountDtoResponse verifyOtp(String inputOtp, String email) {
        if (!otpMailService.validateOtp(email, inputOtp)) { //  đổi vị trí
            throw new OtpValidationException("Mã OTP không hợp lệ hoặc đã hết hạn");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy user: " + email));

        user.setStatus(UserStatus.ACTIVE_PENDING);
        userRepository.save(user);

        otpMailService.clearOtp(email); //  dùng email làm key

        return mapToResponse(user);
    }

    // ========== FORGOT PASSWORD ==========
    @Override
    public void forgetPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy user: " + email));

        // Gửi OTP mới
        otpMailService.generateAndSendOtp(email);
    }

    // ========== VERIFY FORGOT PASSWORD OTP ==========
    @Override
    public boolean verifyForgetPassword(String inputOtp, String email) {
        if (!otpMailService.validateOtp(email, inputOtp)) {
            throw new OtpValidationException("Mã OTP không hợp lệ hoặc đã hết hạn");
        }

        String emailFromOtp = otpMailService.getEmailByOtp(inputOtp);
        if (emailFromOtp == null || !emailFromOtp.equalsIgnoreCase(email)) {
            return false;
        }

        return true;
    }

    // ========== RESET PASSWORD ==========
    @Override
    public AccountDtoResponse resetPassword(AccountDto accountDto, String inputOtp) {
        String email = otpMailService.getEmailByOtp(inputOtp);
        if (email == null) {
            throw new OtpValidationException("OTP không hợp lệ hoặc đã hết hạn");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy user: " + email));

        user.setPassword(passwordEncoder.encode(accountDto.getPassword()));
        userRepository.save(user);
        otpMailService.clearOtp(inputOtp);

        return mapToResponse(user);
    }

    @Override
    public UserVerificationResponse verifyUserProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
        if (user.getStatus() == UserStatus.ACTIVE) {
            throw new BadRequestException("Hồ sơ này đã được xác thực rồi.");
        }

        if (user.getStatus() != UserStatus.ACTIVE_PENDING) {
            throw new BadRequestException("Không thể xác thực hồ sơ do trạng thái không hợp lệ: " + user.getStatus());
        }

        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        List<Photo> photos = photoRepository.findByUser_UserIdOrderByUploadedAtDesc(userId);

        // Map theo type (giả sử type = "ID_CARD" và "DRIVER_LICENSE")
        String idCardUrl = photos.stream()
                .filter(p -> "CCCD".equalsIgnoreCase(p.getType()))
                .map(Photo::getPhotoUrl)
                .findFirst()
                .orElse(null);

        String driverLicenseUrl = photos.stream()
                .filter(p -> "GPLX".equalsIgnoreCase(p.getType()))
                .map(Photo::getPhotoUrl)
                .findFirst()
                .orElse(null);

        return UserVerificationResponse.builder()
                .userId(user.getUserId())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .email(user.getEmail())
                .status(user.getStatus().name())
                .role(user.getRole().name())
                .idCardUrl(idCardUrl)
                .driverLicenseUrl(driverLicenseUrl)
                .build();
    }

    @Override
    @Transactional
    public List<UserVerificationResponse> getPendingVerificationUsers() {
        List<User> users = userRepository.findByStatusIn(List.of(
                UserStatus.ACTIVE,
                UserStatus.ACTIVE_PENDING
        ));

        return users.stream().map(user -> {
            List<Photo> photos = photoRepository.findByUser_UserIdOrderByUploadedAtDesc(user.getUserId());

            String idCardUrl = photos.stream()
                    .filter(p -> "CCCD".equalsIgnoreCase(p.getType()))
                    .map(Photo::getPhotoUrl)
                    .findFirst()
                    .orElse(null);

            String driverLicenseUrl = photos.stream()
                    .filter(p -> "GPLX".equalsIgnoreCase(p.getType()))
                    .map(Photo::getPhotoUrl)
                    .findFirst()
                    .orElse(null);


            String userStatusDisplay = switch (user.getStatus()) {
                case ACTIVE -> "ĐÃ XÁC THỰC (HỒ SƠ)";
                case ACTIVE_PENDING -> "CHƯA XÁC THỰC";
                default -> "KHÔNG HỢP LỆ";
            };

            return UserVerificationResponse.builder()
                    .userId(user.getUserId())
                    .fullName(user.getFullName())
                    .phone(user.getPhone())
                    .email(user.getEmail())
                    .status(user.getStatus().name())
                    .userStatus(userStatusDisplay)
                    .role(user.getRole().name())
                    .idCardUrl(idCardUrl)
                    .driverLicenseUrl(driverLicenseUrl)
                    .build();
        }).toList();
    }

    @Override
    public List<AccountDtoResponse> getAllCustomer() {
        return userRepository.findByRole(Role.customer)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public AccountDtoResponse getUserById(UUID userId) {
        return userRepository.findById(userId)
                .map(this::mapToResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy userId: " + userId));
    }
    @Override
    public User createStaff(StaffCreateRequest request) {

        RentalStation station = rentalStationRepository.findById(request.getStationId())
                .orElseThrow(() -> new RuntimeException("Station not found"));

        String encodedPassword = passwordEncoder.encode(request.getPassword());

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .password(encodedPassword)
                .role(Role.staff)
                .status(UserStatus.ACTIVE)
                .rentalStation(station)
                .build();

        return userRepository.save(user);
    }


    @Override
    public StaffResponse updateStaffByEmail(String email,
                                            com.group6.Rental_Car.dtos.stafflist.StaffUpdateRequest request) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy user với email: " + email));

        if (user.getRole() != Role.staff) {
            throw new BadRequestException("User này không phải STAFF");
        }

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }

        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }

        if (request.getStationId() != null) {
            RentalStation station = rentalStationRepository.findById(request.getStationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy trạm"));
            user.setRentalStation(station);
        }

        userRepository.save(user);

        return toStaffResponse(user);
    }


    @Transactional
    @Override
    public void deleteByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy user với email: " + email));

        // XÓA SCHEDULE TRƯỚC
        employeeScheduleRepository.deleteByStaff_UserId(user.getUserId());

        userRepository.delete(user);
    }

    public StaffResponse toStaffResponse(User user) {
        StaffResponse dto = new StaffResponse();
        dto.setStaffId(user.getUserId());
        dto.setStaffName(user.getFullName());
        dto.setStaffEmail(user.getEmail());
        dto.setStaffPhone(user.getPhone());
        dto.setStatus(user.getStatus().name());
        dto.setRole(user.getRole().name());

        if (user.getRentalStation() != null) {
            dto.setStationName(user.getRentalStation().getName());
            dto.setStationName(user.getRentalStation().getName());
        }

        return dto;
    }

}
