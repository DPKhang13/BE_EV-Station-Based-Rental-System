# 📋 TÓM TẮT BUSINESS RULES
## EV Station-Based Rental System

---

## 🎯 **TOP 50 BUSINESS RULES QUAN TRỌNG NHẤT**

### **1. AUTHENTICATION & USER (10 rules)**

1. ✅ Email phải unique, đúng format
2. ✅ Password: min 6 ký tự, có chữ hoa/thường/số/ký tự đặc biệt
3. ✅ User phải verify OTP trước khi login
4. ✅ User phải upload CCCD + GPLX trước khi đặt xe
5. ✅ Chỉ user ACTIVE mới có thể đặt xe
6. ✅ Admin phải verify profile trước khi user ACTIVE
7. ✅ Status flow: NEED_OTP → ACTIVE_PENDING → ACTIVE

### **2. ORDER CREATION (10 rules)**

8. ✅ Không thể đặt xe nếu đã có đơn PENDING/RENTAL
9. ✅ Không thể đặt xe nếu có booking trùng lặp thời gian
10. ✅ StartTime < EndTime (bắt buộc)
11. ✅ Giá tính theo từng ngày (weekend tự động dùng holidayPrice)
12. ✅ Coupon được áp dụng sau khi tính basePrice
13. ✅ Nếu holiday = true → dùng holidayPrice cho tất cả ngày
14. ✅ Vehicle status → BOOKED nếu đây là booking đầu tiên
15. ✅ Timeline BOOKED được tạo khi tạo order

### **3. PAYMENT (10 rules)**

16. ✅ Deposit = 50% totalPrice
17. ✅ Final payment chỉ có thể thanh toán sau deposit
18. ✅ Full payment = 100% totalPrice
19. ✅ Service payment = phí trễ/dịch vụ phát sinh
20. ✅ Payment method: captureWallet, payWithMethod, momo
21. ✅ Payment status: PENDING → SUCCESS/FAILED
22. ✅ RemainingAmount = totalPrice - sum(successful payments)
23. ✅ Auto-cancel order sau 30 phút nếu chưa thanh toán
24. ✅ Không auto-cancel nếu có payment PENDING

### **4. VEHICLE (10 rules)**

25. ✅ PlateNumber phải unique
26. ✅ SeatCount phải là 4 hoặc 7
27. ✅ Variant phải là air/pro/plus
28. ✅ Status: AVAILABLE → BOOKED → RENTAL → CHECKING → AVAILABLE
29. ✅ Không thể set RENTAL nếu xe đang được người khác thuê
30. ✅ Vehicle status tự động update dựa vào timeline
31. ✅ Battery status giảm random khi trả xe (20%-initial)

### **5. PRICING & COUPON (5 rules)**

32. ✅ Weekend (thứ 7, CN) tự động dùng holidayPrice
33. ✅ Late fee = lateFeePerDay * số ngày trễ
34. ✅ Coupon <= 100 → phần trăm (%)
35. ✅ Coupon > 100 → giá cố định (VND)
36. ✅ Final price không bao giờ < 0

### **6. PICKUP & RETURN (5 rules)**

37. ✅ Order phải AWAITING/PAID để pickup
38. ✅ Sau pickup: Order → RENTAL, Vehicle → RENTAL
39. ✅ Nếu trả muộn → tính phí trễ và cộng vào totalPrice
40. ✅ Sau return: Vehicle → CHECKING, Order → PENDING_FINAL_PAYMENT
41. ✅ Booking tiếp theo tự động được promote từ hàng chờ

---

## 🔄 **STATE FLOWS**

### **Order Flow:**
```
PENDING → (deposit) → DEPOSITED → (final payment) → AWAITING
                                                         ↓
                                                    (pickup)
                                                         ↓
                                                      RENTAL
                                                         ↓
                                                    (return)
                                                         ↓
                                          PENDING_FINAL_PAYMENT
                                                         ↓
                                                  (complete)
                                                         ↓
                                                    COMPLETED
```

### **Vehicle Flow:**
```
AVAILABLE → (first booking) → BOOKED → (pickup) → RENTAL
                                                         ↓
                                                    (return)
                                                         ↓
                                                    CHECKING
                                                         ↓
                                                  AVAILABLE
```

### **Payment Flow:**
```
PENDING → SUCCESS/FAILED
```

---

## ⚠️ **CÁC RULES QUAN TRỌNG DỄ QUÊN**

### **Validation Rules:**
- ✅ StartTime và EndTime phải hợp lệ (end > start)
- ✅ Số ngày thuê = days between start và end (KHÔNG bao gồm end date)
- ✅ Price >= 0 (không bao giờ âm)

### **Business Logic:**
- ✅ User chỉ có thể có 1 đơn active tại một thời điểm
- ✅ Vehicle không thể được 2 người thuê cùng lúc
- ✅ Timeline BOOKED bị xóa khi confirm pickup
- ✅ Late fee được tạo như service detail

### **Auto Processing:**
- ✅ Auto-cancel pending orders sau 30 phút
- ✅ Auto-promote booking tiếp theo khi xe available
- ✅ Auto-reduce battery khi trả xe

---

## 📊 **THỐNG KÊ**

**Tổng cộng:** ~208 Business Rules đã được phát hiện

**Phân loại:**
- Authentication: 15 rules
- User Management: 8 rules  
- Order Management: 44 rules
- Vehicle Management: 18 rules
- Payment: 29 rules
- Pricing: 12 rules
- Coupon: 12 rules
- Staff Schedule: 14 rules
- Validation: 17 rules
- Business Logic: 22 rules

---

## 📝 **KẾT LUẬN**

Hệ thống có **hệ thống business rules khá đầy đủ** và được implement rõ ràng trong code. Một số rules có thể cần được document rõ hơn hoặc refactor để dễ maintain hơn.

**Để xem chi tiết:** Xem file `BUSINESS_RULES_COMPREHENSIVE.md`


