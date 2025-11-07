# ✅ ĐÃ HOÀN THÀNH - Thêm field image_url cho Vehicle

## 📋 Các thay đổi đã thực hiện

### 1. Database Migration ✅
**File:** `src/main/resources/db/migration/V1__add_image_url_to_vehicles.sql`
```sql
ALTER TABLE vehicles
ADD COLUMN image_url VARCHAR(500) DEFAULT NULL;
```

**Hoặc chạy SQL trực tiếp:**
```sql
ALTER TABLE vehicles
ADD COLUMN image_url VARCHAR(500) DEFAULT NULL;
```

### 2. Entity Class ✅
**File:** `Vehicle.java`
```java
@Column(name = "image_url", length = 500)
private String imageUrl;
```

### 3. DTO Classes ✅

**VehicleResponse.java:**
```java
private String imageUrl;
```

**VehicleCreateRequest.java:**
```java
private String imageUrl;
```

**VehicleUpdateRequest.java:**
```java
private String imageUrl;
```

### 4. Service Layer ✅

**VehicleServiceImpl.java:**
- `createVehicle()`: Xử lý imageUrl khi tạo xe mới
- `updateVehicle()`: Xử lý imageUrl khi cập nhật xe

## 🚀 Cách sử dụng

### Tạo xe mới (POST /api/vehicles/add)
```json
{
  "vehicleName": "VinFast VF5 Plus",
  "brand": "Vinfast",
  "plateNumber": "29A-12345",
  "variant": "Plus",
  "color": "Blue",
  "seatCount": 4,
  "year": 2024,
  "stationId": 1,
  "batteryStatus": 100,
  "rangeKm": 300,
  "status": "Available",
  "transmission": "Automatic",
  "batteryCapacity": 37.23,
  "description": "Xe điện VinFast VF5 Plus màu xanh",
  "imageUrl": "https://s3-hcm5-r1.longvan.net/19430189-verify-customer-docs/imgCar/4_Cho/Vinfast/blue.png"
}
```

### Cập nhật xe (PUT /api/vehicles/update/{id})
```json
{
  "imageUrl": "https://s3-hcm5-r1.longvan.net/19430189-verify-customer-docs/imgCar/4_Cho/Vinfast/red.png",
  "color": "Red"
}
```

### Response Example
```json
{
  "id": 1,
  "vehicleName": "VinFast VF5 Plus",
  "brand": "Vinfast",
  "plateNumber": "29A-12345",
  "variant": "Plus",
  "color": "Blue",
  "seatCount": 4,
  "imageUrl": "https://s3-hcm5-r1.longvan.net/19430189-verify-customer-docs/imgCar/4_Cho/Vinfast/blue.png",
  "status": "Available",
  ...
}
```

## ⚠️ Lưu ý quan trọng

### 1. Field có thể NULL
- `imageUrl` là optional field
- Xe cũ không có ảnh vẫn hoạt động bình thường
- Frontend gửi URL string, không upload file

### 2. Độ dài tối đa: 500 ký tự
- Đủ để chứa URL dài từ S3 hoặc CDN
- Validate ở application layer nếu cần

### 3. Naming Convention
- Database: `image_url` (snake_case)
- Java: `imageUrl` (camelCase)
- JPA tự động map giữa 2 format

### 4. Migration Database
Chạy một trong hai cách:

**Cách 1: Flyway/Liquibase (nếu đã config)**
```bash
# Migration tự động chạy khi start app
mvnw spring-boot:run
```

**Cách 2: Chạy SQL trực tiếp**
```sql
ALTER TABLE vehicles ADD COLUMN image_url VARCHAR(500) DEFAULT NULL;
```

## ✅ Checklist Deploy

- [x] Thêm column vào database (chạy migration SQL)
- [x] Entity Vehicle có field imageUrl
- [x] DTOs có field imageUrl (Response, CreateRequest, UpdateRequest)
- [x] Service xử lý imageUrl khi create/update
- [ ] Chạy migration database (cần làm thủ công)
- [ ] Build lại project: `mvnw clean package`
- [ ] Test với Swagger/Postman

## 🧪 Test

### Test 1: Tạo xe với imageUrl
```bash
POST /api/vehicles/add
Body: {
  "vehicleName": "Test Car",
  "plateNumber": "99X-9999",
  "stationId": 1,
  "seatCount": 4,
  "variant": "Plus",
  "brand": "Vinfast",
  "color": "Blue",
  "status": "Available",
  "imageUrl": "https://example.com/car.png"
}
```

### Test 2: Tạo xe không có imageUrl (NULL)
```bash
POST /api/vehicles/add
Body: {
  "vehicleName": "Test Car 2",
  "plateNumber": "88X-8888",
  "stationId": 1,
  "seatCount": 4,
  "variant": "Plus",
  "brand": "BMW",
  "color": "White",
  "status": "Available"
  // imageUrl không gửi -> sẽ là NULL
}
```

### Test 3: Update imageUrl
```bash
PUT /api/vehicles/update/1
Body: {
  "imageUrl": "https://example.com/new-car-image.png"
}
```

### Test 4: Get vehicles
```bash
GET /api/vehicles/get
# Response sẽ có imageUrl field
```

## 📊 Endpoints đã updated

| Method | Endpoint | imageUrl support |
|--------|----------|-----------------|
| GET | /api/vehicles/get | ✅ Trả về imageUrl |
| GET | /api/vehicles/{id} | ✅ Trả về imageUrl |
| POST | /api/vehicles/add | ✅ Nhận imageUrl |
| PUT | /api/vehicles/update/{id} | ✅ Nhận imageUrl |
| **GET** | **/api/vehicles/image-url** | ✅ **Lấy URL ảnh theo brand/color** |

### 🆕 Endpoint mới: GET /api/vehicles/image-url

**Mục đích:** Lấy URL ảnh dựa trên brand và color (dành cho FE khi user chọn brand/color)

**Parameters:**
- `brand` (required): Vinfast, BMW, Tesla
- `color` (required): xanh, bạc, đen, đỏ, trắng
- `seatCount` (optional): Mặc định là 4

**Example Request:**
```
GET /api/vehicles/image-url?brand=Vinfast&color=red
```

**Example Response:**
```json
{
  "imageUrl": "https://s3-hcm5-r1.longvan.net/19430189-verify-customer-docs/imgCar/4_Cho/Vinfast/e420cb1b-1710-4dbe-a5e3-e1285c690b6e.png"
}
```

**Error Response (404):**
```json
{
  "error": "Image not found",
  "message": "No image found for brand: Honda, color: vàng"
}
```

**Supported combinations:**
- **Vinfast**: xanh, bạc, đen, đỏ, trắng
- **BMW**: trắng, bạc, xanh, đen, đỏ
- **Tesla**: bạc, xanh, đen, trắng, đỏ

---

**Status:** ✅ Code đã sẵn sàng
**Next Step:** Chạy migration database để tạo column `image_url`

