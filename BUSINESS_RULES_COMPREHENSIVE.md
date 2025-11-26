# 📋 TỔNG HỢP TẤT CẢ BUSINESS RULES
## EV Station-Based Rental System

---

## 🎯 **MỤC LỤC**

1. [Authentication & Authorization Rules](#1-authentication--authorization-rules)
2. [User Registration & Verification Rules](#2-user-registration--verification-rules)
3. [Order Management Rules](#3-order-management-rules)
4. [Vehicle Management Rules](#4-vehicle-management-rules)
5. [Payment Rules](#5-payment-rules)
6. [Pricing Rules](#6-pricing-rules)
7. [Coupon Rules](#7-coupon-rules)
8. [Staff Schedule Rules](#8-staff-schedule-rules)
9. [State Transition Rules](#9-state-transition-rules)
10. [Validation Rules](#10-validation-rules)
11. [Business Logic Rules](#11-business-logic-rules)

---

## 1. **AUTHENTICATION & AUTHORIZATION RULES**

### **1.1. User Registration Rules**
- ✅ **BR-001:** Email phải unique trong hệ thống
- ✅ **BR-002:** Email phải đúng format (@Email validation)
- ✅ **BR-003:** Password phải có:
  - Tối thiểu 6 ký tự, tối đa 200 ký tự
  - Ít nhất 1 chữ thường (a-z)
  - Ít nhất 1 chữ hoa (A-Z)
  - Ít nhất 1 số (0-9)
  - Ít nhất 1 ký tự đặc biệt (@$!%*?&)
- ✅ **BR-004:** Password phải được hash bằng BCrypt (strength = 12)
- ✅ **BR-005:** Role mặc định khi đăng ký = `customer`
- ✅ **BR-006:** Status mặc định khi đăng ký = `NEED_OTP`

### **1.2. User Login Rules**
- ✅ **BR-007:** User chỉ có thể login nếu status = `ACTIVE` hoặc `ACTIVE_PENDING`
- ✅ **BR-008:** User với status = `NEED_OTP`, `INACTIVE`, `VERIFIED` không thể login
- ✅ **BR-009:** Password phải match với password đã hash trong database
- ✅ **BR-010:** Email không tồn tại → throw ResourceNotFoundException

### **1.3. OTP Verification Rules**
- ✅ **BR-011:** OTP được gửi qua email khi đăng ký
- ✅ **BR-012:** OTP phải được verify trước khi user có thể đăng nhập
- ✅ **BR-013:** Sau khi verify OTP thành công, status chuyển từ `NEED_OTP` → `ACTIVE_PENDING`
- ✅ **BR-014:** OTP không hợp lệ hoặc hết hạn → throw OtpValidationException
- ✅ **BR-015:** OTP được clear sau khi verify thành công

### **1.4. Password Reset Rules**
- ✅ **BR-016:** User có thể yêu cầu reset password qua email
- ✅ **BR-017:** OTP mới được gửi khi yêu cầu forgot password
- ✅ **BR-018:** Phải verify OTP trước khi reset password
- ✅ **BR-019:** Password mới phải tuân theo format validation (BR-003)

### **1.5. Document Verification Rules**
- ✅ **BR-020:** User phải upload 2 loại giấy tờ trước khi đặt xe:
  - CCCD (Căn cước công dân)
  - GPLX (Giấy phép lái xe)
- ✅ **BR-021:** UserDocsGuard.assertHasDocs() được gọi trước khi tạo order
- ✅ **BR-022:** User thiếu documents → throw ResponseStatusException 403
- ✅ **BR-023:** Chỉ admin/staff mới có thể verify user profile
- ✅ **BR-024:** User status phải là `ACTIVE_PENDING` mới có thể verify
- ✅ **BR-025:** Sau khi verify, status chuyển từ `ACTIVE_PENDING` → `ACTIVE`

---

## 2. **USER REGISTRATION & VERIFICATION RULES**

### **2.1. User Creation Rules**
- ✅ **BR-026:** Email phải lowercase trước khi lưu vào database
- ✅ **BR-027:** Phone number là required field
- ✅ **BR-028:** FullName là required field

### **2.2. User Status Rules**
- ✅ **BR-029:** User status flow:
  ```
  NEED_OTP → (verify OTP) → ACTIVE_PENDING → (admin verify) → ACTIVE
                                                            ↓
                                                      INACTIVE (nếu bị khóa)
  ```
- ✅ **BR-030:** User chỉ có thể đặt xe khi status = `ACTIVE`
- ✅ **BR-031:** User `ACTIVE_PENDING` đã verify OTP nhưng chưa được admin approve

### **2.3. Role Management Rules**
- ✅ **BR-032:** 3 roles: `customer`, `staff`, `admin`
- ✅ **BR-033:** Customer mặc định không có station (rentalStation = null)
- ✅ **BR-034:** Staff phải được assign vào một station

---

## 3. **ORDER MANAGEMENT RULES**

### **3.1. Order Creation Rules**
- ✅ **BR-035:** User phải có documents đầy đủ (CCCD + GPLX) trước khi tạo order
- ✅ **BR-036:** User không thể tạo order mới nếu đã có đơn đang xử lý:
  - Status = `DEPOSITED`
  - Status = `PENDING`
  - Status = `RENTAL`
  - Status starts with `PENDING`
- ✅ **BR-037:** Vehicle phải tồn tại trong hệ thống
- ✅ **BR-038:** StartTime và EndTime phải hợp lệ:
  - StartTime != null
  - EndTime != null
  - EndTime > StartTime
- ✅ **BR-039:** Không thể đặt xe nếu có booking trùng lặp trong khoảng thời gian đó
- ✅ **BR-040:** Order status mặc định khi tạo = `PENDING`

### **3.2. Order Price Calculation Rules**
- ✅ **BR-041:** Giá được tính dựa trên PricingRule của carmodel
- ✅ **BR-042:** Nếu `isHoliday = true` và có `holidayPrice`:
  - Dùng `holidayPrice` cho tất cả các ngày
  - `basePrice = holidayPrice * số ngày`
- ✅ **BR-043:** Nếu không phải holiday:
  - Tính giá theo từng ngày
  - Tự động detect weekend (thứ 7, chủ nhật)
  - Weekend → dùng `holidayPrice` (nếu có)
  - Weekday → dùng `dailyPrice`
- ✅ **BR-044:** Coupon được áp dụng sau khi tính basePrice
- ✅ **BR-045:** Total price = basePrice - coupon discount (nếu có)

### **3.3. Vehicle Status Update Rules (Khi tạo order)**
- ✅ **BR-046:** Nếu vehicle status = `AVAILABLE` và đây là booking đầu tiên:
  - Vehicle status → `BOOKED`
- ✅ **BR-047:** Nếu vehicle đã có booking khác:
  - Giữ nguyên status hiện tại (không set BOOKED)
- ✅ **BR-048:** Vehicle timeline được tạo với status = `BOOKED`

### **3.4. Order Update Rules**
- ✅ **BR-049:** Order status có thể được update trực tiếp (chưa có validation)
- ✅ **BR-050:** Coupon code có thể được update sau khi tạo order

### **3.5. Change Vehicle Rules**
- ✅ **BR-051:** Chỉ có thể đổi xe khi order status là:
  - `DEPOSITED`
  - `AWAITING`
  - `PENDING`
- ✅ **BR-052:** Không thể đổi xe khi đã `RENTAL` (đã nhận xe)
- ✅ **BR-053:** Xe mới phải có status = `AVAILABLE`
- ✅ **BR-054:** Xe cũ được release (set về AVAILABLE nếu không có booking khác)
- ✅ **BR-055:** Xe mới được set status = `BOOKED`
- ✅ **BR-056:** Timeline được tạo cho xe mới với sourceType = `VEHICLE_CHANGED`

### **3.6. Order Cancellation Rules**
- ✅ **BR-057:** Order có thể bị cancel bất kỳ lúc nào (chưa rõ constraints)
- ✅ **BR-058:** Cancellation reason có thể được ghi lại

### **3.7. Order Completion Rules**
- ✅ **BR-059:** Chỉ có thể complete order từ các status:
  - `AWAITING`
  - `PAID`
  - `PENDING_FINAL_PAYMENT`
  - `RETURNED`
- ✅ **BR-060:** Không thể complete order đã `COMPLETED`
- ✅ **BR-061:** Không thể complete order đã `FAILED` hoặc `REFUNDED`
- ✅ **BR-062:** Phải thanh toán hết (remainingAmount = 0) mới có thể complete
- ✅ **BR-063:** Sau khi complete, status → `COMPLETED`

### **3.8. Confirm Pickup Rules**
- ✅ **BR-064:** Order phải ở status `AWAITING` hoặc `PAID` để pickup
- ✅ **BR-065:** Xe không được đang được khách hàng khác thuê
- ✅ **BR-066:** Sau khi pickup:
  - Order status → `RENTAL`
  - Vehicle status → `RENTAL`
  - Timeline BOOKED bị xóa
  - Timeline mới với status = `RENTAL` được tạo
- ✅ **BR-067:** Thông báo cho các khách hàng khác đã book cùng xe
- ✅ **BR-068:** Pickup count của staff tăng lên

### **3.9. Confirm Return Rules**
- ✅ **BR-069:** ActualReturnTime có thể khác EndTime
- ✅ **BR-070:** Nếu trả muộn (actualDays > expectedDays):
  - Tính phí trễ = `lateFeePerDay * số ngày trễ`
  - Cộng phí trễ vào totalPrice
  - Tạo detail mới với type = `SERVICE`, description = "Phí trễ hạn X ngày"
  - Cập nhật remainingAmount của payment
- ✅ **BR-071:** Nếu trả sớm (actualDays < expectedDays):
  - Log thông tin (không tính tiền)
- ✅ **BR-072:** Battery status được tự động giảm khi trả xe:
  - Random từ max(20%, initialBattery - 60%) đến initialBattery
  - Đảm bảo không dưới 20%
- ✅ **BR-073:** Sau khi return:
  - Vehicle status → `CHECKING`
  - Order status → `PENDING_FINAL_PAYMENT` (nếu đã thanh toán hết)
  - Order status → `PENDING_FINAL_PAYMENT` (nếu còn phí trễ/dịch vụ chưa thanh toán)
- ✅ **BR-074:** Timeline của order bị xóa
- ✅ **BR-075:** Kiểm tra và chuyển booking tiếp theo từ hàng chờ
- ✅ **BR-076:** Return count của staff tăng lên

### **3.10. Auto-Cancel Pending Orders Rules**
- ✅ **BR-077:** Pending orders tự động bị hủy sau 30 phút không thanh toán
- ✅ **BR-078:** Không hủy nếu có payment PENDING
- ✅ **BR-079:** Không hủy nếu đã có payment SUCCESS
- ✅ **BR-080:** Sau khi auto-cancel:
  - Order status → `PAYMENT_FAILED`
  - Vehicle status → `AVAILABLE` (nếu không có booking khác)

---

## 4. **VEHICLE MANAGEMENT RULES**

### **4.1. Vehicle Creation Rules**
- ✅ **BR-081:** PlateNumber phải unique trong hệ thống
- ✅ **BR-082:** PlateNumber tối đa 20 ký tự
- ✅ **BR-083:** Status phải là một trong: `available`, `rented`, `maintenance`, `BOOKED`
- ✅ **BR-084:** StationId phải tồn tại
- ✅ **BR-085:** SeatCount phải là 4 hoặc 7
- ✅ **BR-086:** Variant phải là `air`, `pro`, hoặc `plus`
- ✅ **BR-087:** Variant phải match với SeatCount:
  - SeatCount = 4 → variant bắt buộc phải có
  - SeatCount = 7 → variant bắt buộc phải có
- ✅ **BR-088:** Images được upload lên S3 với folder = `vehicles/{plateNumber}`
- ✅ **BR-089:** VehicleModel được tạo cùng với Vehicle

### **4.2. Vehicle Update Rules**
- ✅ **BR-090:** Status có thể update nhưng phải trong allowed set
- ✅ **BR-091:** StationId có thể thay đổi
- ✅ **BR-092:** Variant phải được validate lại khi update SeatCount

### **4.3. Vehicle Status Transition Rules**
- ✅ **BR-093:** Khi chuyển status → `AVAILABLE`:
  - Xóa timeline MAINTENANCE và CHECKING
  - Giữ lại timeline BOOKED
  - Kiểm tra và set BOOKED nếu có booking
- ✅ **BR-094:** Khi chuyển status → `MAINTENANCE`:
  - Tạo timeline MAINTENANCE
- ✅ **BR-095:** Khi chuyển status → `CHECKING`:
  - Tạo timeline CHECKING

### **4.4. Vehicle Availability Rules**
- ✅ **BR-096:** Vehicle available khi:
  - Status = `AVAILABLE`
  - Không có booking trùng lặp trong khoảng thời gian
- ✅ **BR-097:** Vehicle không available khi:
  - Status = `RENTAL` (đang được thuê)
  - Status = `MAINTENANCE` (đang bảo trì)
  - Có booking đang active trong khoảng thời gian đó

### **4.5. Vehicle Deletion Rules**
- ✅ **BR-098:** Khi xóa vehicle, VehicleModel cũng bị xóa (orphan removal)

---

## 5. **PAYMENT RULES**

### **5.1. Payment Type Rules**
- ✅ **BR-099:** Payment types:
  - Type 1: Deposit (Đặt cọc) = 50% totalPrice
  - Type 2: Final Payment (Thanh toán còn lại)
  - Type 3: Full Payment (Thanh toán toàn bộ)
  - Type 5: Service Payment (Thanh toán dịch vụ/phí trễ)

### **5.2. Payment Creation Rules**
- ✅ **BR-100:** Payment type phải từ 1-5
- ✅ **BR-101:** Payment method phải là một trong:
  - `captureWallet`
  - `payWithMethod`
  - `momo`
- ✅ **BR-102:** Payment status mặc định = `PENDING`

### **5.3. Deposit Payment Rules (Type 1)**
- ✅ **BR-103:** Amount = totalPrice / 2 (50%)
- ✅ **BR-104:** RemainingAmount = totalPrice - amount
- ✅ **BR-105:** Order status → `DEPOSITED` sau khi thanh toán thành công

### **5.4. Final Payment Rules (Type 2)**
- ✅ **BR-106:** Chỉ có thể thanh toán type 2 nếu đã có deposit payment SUCCESS
- ✅ **BR-107:** Amount = remainingAmount từ deposit payment
- ✅ **BR-108:** Nếu có full payment SUCCESS với remainingAmount > 0:
  - Amount = remainingAmount của full payment
- ✅ **BR-109:** RemainingAmount = 0
- ✅ **BR-110:** Nếu đã có payment type 2 PENDING → update thay vì tạo mới
- ✅ **BR-111:** Tạo detail type = `PICKUP` với status = `PENDING`

### **5.5. Full Payment Rules (Type 3)**
- ✅ **BR-112:** Amount = totalPrice
- ✅ **BR-113:** RemainingAmount = 0
- ✅ **BR-114:** Order status → `PAID` hoặc `AWAITING` sau khi thanh toán

### **5.6. Service Payment Rules (Type 5)**
- ✅ **BR-115:** Thanh toán cho phí trễ hoặc dịch vụ phát sinh
- ✅ **BR-116:** Amount = remainingAmount từ deposit hoặc full payment
- ✅ **BR-117:** Không có dịch vụ phát sinh → throw BadRequestException
- ✅ **BR-118:** Nếu đã có payment type 5 PENDING → update thay vì tạo mới

### **5.7. Payment Status Rules**
- ✅ **BR-119:** Payment status flow:
  ```
  PENDING → SUCCESS/FAILED
  ```
- ✅ **BR-120:** Payment SUCCESS → Order status được cập nhật
- ✅ **BR-121:** Payment FAILED → Order status = `PAYMENT_FAILED`
- ✅ **BR-122:** Payment SUCCESS → TransactionHistory được tạo

### **5.8. Remaining Amount Calculation Rules**
- ✅ **BR-123:** RemainingAmount = totalPrice - sum of all successful payments
- ✅ **BR-124:** RemainingAmount được cập nhật khi:
  - Tạo dịch vụ mới (phí trễ, cleaning, etc.)
  - Thanh toán thành công

### **5.9. MoMo Payment Integration Rules**
- ✅ **BR-125:** MoMo callback được xử lý để cập nhật payment status
- ✅ **BR-126:** Payment URL được tạo từ MoMo API
- ✅ **BR-127:** ResultCode = 0 → SUCCESS, khác 0 → FAILED

---

## 6. **PRICING RULES**

### **6.1. Pricing Rule Structure**
- ✅ **BR-128:** Mỗi carmodel có một PricingRule
- ✅ **BR-129:** PricingRule có:
  - `dailyPrice`: Giá ngày thường
  - `holidayPrice`: Giá ngày lễ/cuối tuần (optional)
  - `lateFeePerDay`: Phí trễ mỗi ngày

### **6.2. Rental Price Calculation Rules**
- ✅ **BR-130:** Số ngày thuê = days between startDate và endDate (KHÔNG bao gồm endDate)
- ✅ **BR-131:** Ví dụ: 23/11 đến 28/11 = 5 ngày (23, 24, 25, 26, 27)
- ✅ **BR-132:** Mỗi ngày được tính riêng:
  - Weekday → dailyPrice
  - Weekend (thứ 7, CN) → holidayPrice (nếu có), ngược lại → dailyPrice
- ✅ **BR-133:** Nếu `isHoliday = true`:
  - Tất cả các ngày đều dùng holidayPrice
  - BasePrice = holidayPrice * số ngày

### **6.3. Late Fee Calculation Rules**
- ✅ **BR-134:** Late fee = lateFeePerDay * số ngày trễ
- ✅ **BR-135:** Số ngày trễ = actualDays - expectedDays
- ✅ **BR-136:** Late fee chỉ tính khi actualDays > expectedDays
- ✅ **BR-137:** Late fee được cộng vào totalPrice khi trả xe

### **6.4. Weekend Detection Rules**
- ✅ **BR-138:** Weekend = Saturday hoặc Sunday
- ✅ **BR-139:** Weekend tự động dùng holidayPrice (nếu có)

---

## 7. **COUPON RULES**

### **7.1. Coupon Validation Rules**
- ✅ **BR-140:** Coupon phải tồn tại trong database
- ✅ **BR-141:** Coupon phải trong thời gian hiệu lực:
  - `validFrom <= today <= validTo`
- ✅ **BR-142:** Coupon status phải = `active` (case-insensitive)
- ✅ **BR-143:** Coupon chưa có hiệu lực → throw BadRequestException
- ✅ **BR-144:** Coupon đã hết hạn → throw BadRequestException
- ✅ **BR-145:** Coupon không active → throw BadRequestException

### **7.2. Coupon Discount Calculation Rules**
- ✅ **BR-146:** Discount <= 100 → coi là phần trăm (%)
  - Discount = 10.00 → 10% → giảm 10%
  - FinalPrice = basePrice * (1 - discount/100)
- ✅ **BR-147:** Discount > 100 → coi là giá cố định (VND)
  - Discount = 50000 → giảm 50,000 VND
  - FinalPrice = basePrice - discount
- ✅ **BR-148:** FinalPrice không bao giờ < 0 (minimum = 0)

### **7.3. Coupon Application Rules**
- ✅ **BR-149:** Coupon được apply sau khi tính basePrice
- ✅ **BR-150:** Coupon code có thể để trống (không bắt buộc)
- ✅ **BR-151:** Coupon code được trim() trước khi validate

---

## 8. **STAFF SCHEDULE RULES**

### **8.1. Schedule Creation Rules**
- ✅ **BR-152:** Staff phải tồn tại
- ✅ **BR-153:** Station phải tồn tại
- ✅ **BR-154:** Không được trùng lặp:
  - Một staff không thể có 2 ca làm việc cùng ngày và cùng shift
- ✅ **BR-155:** Unique constraint: (staff_id, shift_date, shift_time)

### **8.2. Schedule Update Rules**
- ✅ **BR-156:** Khi update, vẫn phải check duplicate (trừ chính nó)
- ✅ **BR-157:** Shift time được trim() trước khi lưu

### **8.3. Shift Time Rules**
- ✅ **BR-158:** Shift times: `MORNING`, `AFTERNOON`, `EVENING`, `NIGHT`
- ✅ **BR-159:** Shift time detection:
  - 6h-12h → MORNING
  - 12h-18h → AFTERNOON
  - 18h-22h → EVENING
  - 22h-6h → NIGHT

### **8.4. Staff Performance Tracking Rules**
- ✅ **BR-160:** Pickup count tăng khi staff confirm pickup
- ✅ **BR-161:** Return count tăng khi staff confirm return
- ✅ **BR-162:** Count được lưu trong EmployeeSchedule theo ca làm việc
- ✅ **BR-163:** Nếu không tìm thấy schedule, không tạo tự động (chỉ log)

### **8.5. Staff Status Rules**
- ✅ **BR-164:** Chỉ user với role = `staff` mới có thể toggle status
- ✅ **BR-165:** Toggle status: ACTIVE ↔ INACTIVE

---

## 9. **STATE TRANSITION RULES**

### **9.1. Order Status Transitions**
```
PENDING 
  ↓ (deposit payment success)
DEPOSITED
  ↓ (final payment success hoặc full payment)
AWAITING / PAID
  ↓ (confirm pickup)
RENTAL
  ↓ (confirm return)
PENDING_FINAL_PAYMENT
  ↓ (complete order)
COMPLETED

PENDING → (auto-cancel sau 30 phút)
PAYMENT_FAILED

PENDING → (cancel)
CANCELLED

PENDING → (payment failed)
FAILED

PENDING → (refund)
REFUNDED
```

### **9.2. Vehicle Status Transitions**
```
AVAILABLE
  ↓ (first booking)
BOOKED
  ↓ (confirm pickup)
RENTAL
  ↓ (confirm return)
CHECKING
  ↓ (staff check xong)
AVAILABLE

AVAILABLE → (maintenance)
MAINTENANCE → (maintenance done)
AVAILABLE
```

### **9.3. Payment Status Transitions**
```
PENDING → SUCCESS/FAILED
```

### **9.4. User Status Transitions**
```
Register
  ↓
NEED_OTP
  ↓ (verify OTP)
ACTIVE_PENDING
  ↓ (admin verify)
ACTIVE

ACTIVE → (admin deactivate)
INACTIVE
```

---

## 10. **VALIDATION RULES**

### **10.1. General Validation Rules**
- ✅ **BR-166:** StationId phải > 0
- ✅ **BR-167:** VehicleId phải > 0
- ✅ **BR-168:** OrderId phải là valid UUID
- ✅ **BR-169:** UserId phải là valid UUID
- ✅ **BR-170:** PlateNumber không được để trống
- ✅ **BR-171:** Email phải đúng format và lowercase
- ✅ **BR-172:** Phone number là required

### **10.2. Date/Time Validation Rules**
- ✅ **BR-173:** StartTime và EndTime không được null
- ✅ **BR-174:** EndTime phải > StartTime
- ✅ **BR-175:** Shift date không được null

### **10.3. Price/Amount Validation Rules**
- ✅ **BR-176:** Price/Amount phải >= 0
- ✅ **BR-177:** TotalPrice không được âm
- ✅ **BR-178:** Discount không được âm

### **10.4. String Length Validation Rules**
- ✅ **BR-179:** PlateNumber <= 20 characters
- ✅ **BR-180:** Email <= 255 characters (database constraint)
- ✅ **BR-181:** FullName không có giới hạn rõ ràng
- ✅ **BR-182:** Description có thể dài (TEXT column)

---

## 11. **BUSINESS LOGIC RULES**

### **11.1. Vehicle Availability Check Rules**
- ✅ **BR-183:** Check overlap booking:
  - Tìm tất cả RentalOrderDetail có:
    - vehicleId = vehicle.getVehicleId()
    - status IN (`PENDING`, `CONFIRMED`, `RENTAL`)
    - (startTime, endTime) overlap với (request.startTime, request.endTime)
- ✅ **BR-184:** Nếu có overlap → throw BadRequestException

### **11.2. Order Detail Rules**
- ✅ **BR-185:** Mỗi order có ít nhất 1 detail với type = `RENTAL`
- ✅ **BR-186:** Detail type có thể là:
  - `DEPOSIT`: Đặt cọc
  - `RENTAL`: Thuê xe
  - `RETURN`: Trả xe
  - `SERVICE`: Dịch vụ/phí trễ
  - `FULL_PAYMENT`: Thanh toán đầy đủ
  - `PICKUP`: Nhận xe
  - `OTHER`: Khác
- ✅ **BR-187:** Detail status có thể là:
  - `pending`, `PENDING`
  - `confirmed`, `CONFIRMED`
  - `active`
  - `done`
  - `cancelled`
  - `RENTAL`

### **11.3. Vehicle Timeline Rules**
- ✅ **BR-188:** Timeline được tạo khi:
  - Tạo order (status = `BOOKED`)
  - Confirm pickup (status = `RENTAL`)
  - Change vehicle (status = `BOOKED`)
  - Vehicle maintenance (status = `MAINTENANCE`)
  - Vehicle checking (status = `CHECKING`)
  - Auto queue booking (status = `BOOKED`)
- ✅ **BR-189:** Timeline sourceType có thể là:
  - `ORDER_RENTAL`: Từ order thuê xe
  - `ORDER_PICKUP`: Từ pickup
  - `VEHICLE_CHANGED`: Xe được đổi
  - `VEHICLE_MAINTENANCE`: Bảo trì xe
  - `VEHICLE_CHECKING`: Kiểm tra xe
  - `AUTO_QUEUE`: Tự động từ hàng chờ
- ✅ **BR-190:** Timeline BOOKED bị xóa khi confirm pickup

### **11.4. Battery Status Rules**
- ✅ **BR-191:** Battery status được lưu trong VehicleModel
- ✅ **BR-192:** Battery status format: số + "%" (ví dụ: "80%")
- ✅ **BR-193:** Khi trả xe, battery tự động giảm:
  - Random từ max(20%, initial - 60%) đến initial
- ✅ **BR-194:** Battery không bao giờ dưới 20%

### **11.5. Queue Management Rules**
- ✅ **BR-195:** Khi xe được trả về (status = AVAILABLE):
  - Tìm booking tiếp theo (status = PENDING hoặc CONFIRMED)
  - Tạo timeline BOOKED cho booking đó
  - Cập nhật vehicle status dựa vào timeline
- ✅ **BR-196:** Booking được ưu tiên theo startTime (sớm nhất trước)

### **11.6. Notification Rules**
- ✅ **BR-197:** Thông báo cho khách hàng khác khi:
  - Xe đã được khách hàng khác nhận (confirm pickup)
- ✅ **BR-198:** Các booking khác (status = PENDING hoặc CONFIRMED) sẽ nhận thông báo

### **11.7. Service (OrderService) Rules**
- ✅ **BR-199:** Service types:
  - `TRAFFIC_FEE`: Phí giao thông
  - `CLEANING`: Vệ sinh
  - `MAINTENANCE`: Bảo trì
  - `REPAIR`: Sửa chữa
  - `OTHER`: Khác
- ✅ **BR-200:** Service cost được cộng vào remainingAmount của payment
- ✅ **BR-201:** Late fee được tạo như một service với type = `SERVICE`

### **11.8. Transaction History Rules**
- ✅ **BR-202:** TransactionHistory được tạo khi payment SUCCESS
- ✅ **BR-203:** Transaction type có thể là: `PAYMENT`, `REFUND`, etc.

### **11.9. Staff Assignment Rules**
- ✅ **BR-204:** Staff được assign vào một station
- ✅ **BR-205:** Staff có thể có nhiều schedule trong nhiều ngày/ca khác nhau
- ✅ **BR-206:** Một staff không thể làm 2 ca cùng ngày (unique constraint)

### **11.10. Auto-Processing Rules**
- ✅ **BR-207:** Scheduler chạy auto-cancel pending orders sau 30 phút
- ✅ **BR-208:** Scheduler kiểm tra payment status trước khi cancel

---

## 📊 **TỔNG KẾT**

### **Số lượng Business Rules đã phát hiện:**
- **Authentication & Authorization:** 15 rules
- **User Registration & Verification:** 8 rules
- **Order Management:** 44 rules
- **Vehicle Management:** 18 rules
- **Payment:** 29 rules
- **Pricing:** 12 rules
- **Coupon:** 12 rules
- **Staff Schedule:** 14 rules
- **State Transitions:** 4 groups
- **Validation:** 17 rules
- **Business Logic:** 22 rules

### **Tổng cộng:** ~208 Business Rules

---

## ⚠️ **CÁC BUSINESS RULES CẦN XEM XÉT THÊM**

### **Rules chưa rõ ràng:**
1. ❓ Order cancellation rules - Khi nào được cancel? Có refund không?
2. ❓ Refund rules - Quy trình hoàn tiền như thế nào?
3. ❓ Vehicle maintenance rules - Ai được phép set maintenance? Bao lâu?
4. ❓ Staff assignment rules - Staff có thể làm nhiều station không?
5. ❓ Service creation rules - Ai tạo service? Khi nào?

### **Rules có thể cần bổ sung:**
6. ⚠️ Maximum rental days - Có giới hạn số ngày thuê không?
7. ⚠️ Minimum rental hours - Có giới hạn tối thiểu không?
8. ⚠️ Vehicle age restriction - Xe bao lâu phải bảo trì định kỳ?
9. ⚠️ Customer rating rules - Có blacklist customer không?
10. ⚠️ Promotion rules - Có chương trình khuyến mãi ngoài coupon không?

---

**Version:** 1.0  
**Last Updated:** Hôm nay  
**Status:** Comprehensive Business Rules Documentation


