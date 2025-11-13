# Tóm tắt các sửa đổi - Fix Payment Detail Issues

## Ngày: 14/11/2025

## Vấn đề 1: Null value in column "start_time"
**Lỗi:** 
```
ERROR: null value in column "start_time" of relation "rentalorder_detail" violates not-null constraint
```

**Nguyên nhân:** 
- Khi tạo các detail kiểu DEPOSIT, PICKUP, REFUND không có startTime và endTime
- Nhưng database constraint yêu cầu NOT NULL

**Giải pháp:**
- Lấy startTime và endTime từ detail RENTAL của cùng order
- Áp dụng cho tất cả payment-type details (DEPOSIT, PICKUP, REFUND)

**File đã sửa:**
- `PaymentServiceImpl.java` - Method `createDetail()` và phần tạo REFUND detail

---

## Vấn đề 2: Detail có status SUCCESS khi chưa thanh toán

**Lỗi:**
```
DEPOSIT detail có status = SUCCESS ngay khi tạo payment URL, mặc dù user chưa thanh toán
```

**Nguyên nhân:**
- Khi tạo payment URL (type 1 hoặc 3), code tạo detail với status "SUCCESS" ngay lập tức
- Logic đúng: status phải là "PENDING" cho đến khi có callback từ MoMo xác nhận thành công

**Giải pháp:**
1. **Khi tạo payment URL (createPaymentUrl):**
   - Tạo detail với status = "PENDING"

2. **Khi callback success (handleCallback):**
   - Update detail thành status = "SUCCESS"
   - Đây là lúc user đã thanh toán thành công

**File đã sửa:**
- `PaymentServiceImpl.java`:
  - Dòng 131: Thêm tham số "PENDING" khi tạo detail
  - Dòng 328: Thêm tham số "SUCCESS" cho fullSuccess callback
  - Xóa method `createOrUpdateDetail()` không có tham số status

---

## Flow chính xác sau khi sửa:

### Khi user tạo thanh toán:
1. Tạo Payment record
2. Tạo Detail với status = **"PENDING"**
3. Trả về MoMo payment URL

### Khi MoMo callback success:
1. Update Payment status = SUCCESS
2. Update Detail status = **"SUCCESS"**
3. Update Order status (DEPOSITED/PAID)

---

## Kết quả:
✅ Không còn lỗi null constraint  
✅ Status chính xác theo flow thanh toán  
✅ Detail DEPOSIT/PICKUP/REFUND có đầy đủ startTime/endTime  
✅ Code sạch hơn, không còn method dư thừa

