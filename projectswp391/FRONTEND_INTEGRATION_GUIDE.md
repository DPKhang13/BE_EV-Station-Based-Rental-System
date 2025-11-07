# 🎨 HƯỚNG DẪN FRONTEND - Hiển thị ảnh xe khi chọn Brand và Color

## 📋 API Endpoint

### GET `/api/vehicles/image-url`

**Base URL:** `http://localhost:8080` (dev) hoặc `https://your-backend.com` (production)

**Full URL:** `http://localhost:8080/api/vehicles/image-url`

## 📝 Request Parameters

| Parameter | Type | Required | Description | Example |
|-----------|------|----------|-------------|---------|
| `seatCount` | Integer | ❌ No | Số chỗ ngồi (mặc định: 4) | `4`, `5`, `7` |
| `brand` | String | ✅ Yes | Hãng xe | `Vinfast`, `BMW`, `Tesla` |
| `color` | String | ✅ Yes | Màu xe | `xanh`, `đỏ`, `trắng`, `đen`, `bạc` |

## 🎯 Các giá trị hợp lệ

### Brands (hãng xe):
- `Vinfast` hoặc `vinfast` (case-insensitive)
- `BMW` hoặc `bmw`
- `Tesla` hoặc `tesla`

### Colors (màu xe):
Hỗ trợ cả tiếng Việt và tiếng Anh:

| Tiếng Việt | Tiếng Anh | Brands hỗ trợ |
|------------|-----------|---------------|
| `xanh` | `blue` | Vinfast, BMW, Tesla |
| `đỏ` | `red` | Vinfast, BMW, Tesla |
| `trắng` | `white` | Vinfast, BMW, Tesla |
| `đen` | `black` | Vinfast, BMW, Tesla |
| `bạc` | `silver` | Vinfast, BMW, Tesla |

## 💻 Code Examples

### JavaScript/React Example

```javascript
// Function để lấy URL ảnh
async function getVehicleImageUrl(brand, color, seatCount = 4) {
  try {
    const response = await fetch(
      `http://localhost:8080/api/vehicles/image-url?brand=${brand}&color=${color}&seatCount=${seatCount}`
    );
    
    if (!response.ok) {
      throw new Error('Image not found');
    }
    
    const data = await response.json();
    return data.imageUrl;
  } catch (error) {
    console.error('Error fetching image URL:', error);
    return null;
  }
}

// Sử dụng trong React component
function VehicleImageSelector() {
  const [brand, setBrand] = useState('Vinfast');
  const [color, setColor] = useState('xanh');
  const [imageUrl, setImageUrl] = useState(null);
  
  // Gọi API khi brand hoặc color thay đổi
  useEffect(() => {
    const fetchImage = async () => {
      const url = await getVehicleImageUrl(brand, color);
      setImageUrl(url);
    };
    
    if (brand && color) {
      fetchImage();
    }
  }, [brand, color]);
  
  return (
    <div>
      <select value={brand} onChange={(e) => setBrand(e.target.value)}>
        <option value="Vinfast">Vinfast</option>
        <option value="BMW">BMW</option>
        <option value="Tesla">Tesla</option>
      </select>
      
      <select value={color} onChange={(e) => setColor(e.target.value)}>
        <option value="xanh">Xanh</option>
        <option value="đỏ">Đỏ</option>
        <option value="trắng">Trắng</option>
        <option value="đen">Đen</option>
        <option value="bạc">Bạc</option>
      </select>
      
      {imageUrl && (
        <img src={imageUrl} alt={`${brand} ${color}`} />
      )}
    </div>
  );
}
```

### Axios Example

```javascript
import axios from 'axios';

const API_BASE_URL = 'http://localhost:8080/api/vehicles';

// Function với axios
async function getVehicleImage(brand, color) {
  try {
    const response = await axios.get(`${API_BASE_URL}/image-url`, {
      params: {
        brand: brand,
        color: color,
        seatCount: 4
      }
    });
    
    return response.data.imageUrl;
  } catch (error) {
    console.error('Error:', error.response?.data || error.message);
    return null;
  }
}

// Sử dụng
getVehicleImage('Vinfast', 'xanh')
  .then(url => console.log('Image URL:', url));
```

### jQuery Example

```javascript
function loadVehicleImage(brand, color) {
  $.ajax({
    url: 'http://localhost:8080/api/vehicles/image-url',
    method: 'GET',
    data: {
      brand: brand,
      color: color,
      seatCount: 4
    },
    success: function(data) {
      $('#vehicle-image').attr('src', data.imageUrl);
    },
    error: function(xhr) {
      console.error('Error:', xhr.responseJSON);
    }
  });
}

// Event listeners
$('#brand-select').change(function() {
  loadVehicleImage($(this).val(), $('#color-select').val());
});

$('#color-select').change(function() {
  loadVehicleImage($('#brand-select').val(), $(this).val());
});
```

## 📤 Request Examples

### Example 1: Vinfast màu xanh
```
GET http://localhost:8080/api/vehicles/image-url?brand=Vinfast&color=xanh
```

**Response:**
```json
{
  "imageUrl": "https://s3-hcm5-r1.longvan.net/19430189-verify-customer-docs/imgCar/4_Cho/Vinfast/a80cae76-5c8a-4226-ac85-116ba2da7a3a.png"
}
```

### Example 2: BMW màu đỏ (dùng tiếng Anh)
```
GET http://localhost:8080/api/vehicles/image-url?brand=BMW&color=red
```

**Response:**
```json
{
  "imageUrl": "https://s3-hcm5-r1.longvan.net/19430189-verify-customer-docs/imgCar/4_Cho/BMW/7f3edc23-30ba-4e84-83a9-c8c418f2362d.png"
}
```

### Example 3: Tesla màu trắng
```
GET http://localhost:8080/api/vehicles/image-url?brand=Tesla&color=white&seatCount=4
```

**Response:**
```json
{
  "imageUrl": "https://s3-hcm5-r1.longvan.net/19430189-verify-customer-docs/imgCar/4_Cho/Tesla/unnamed%20%282%29.jpg"
}
```

### Example 4: Lỗi - Brand không hỗ trợ
```
GET http://localhost:8080/api/vehicles/image-url?brand=Honda&color=xanh
```

**Response (404):**
```json
{
  "error": "Image not found",
  "message": "No image found for brand: Honda, color: xanh"
}
```

## 🔧 Troubleshooting

### Vấn đề 1: CORS Error
**Triệu chứng:**
```
Access to fetch at 'http://localhost:8080/api/vehicles/image-url' 
from origin 'http://localhost:3000' has been blocked by CORS policy
```

**Giải pháp:**
- Backend đã có `@CrossOrigin(origins = "*")` trên endpoint
- Nếu vẫn lỗi, restart backend

### Vấn đề 2: 404 Image not found
**Triệu chứng:**
```json
{
  "error": "Image not found",
  "message": "No image found for brand: ..., color: ..."
}
```

**Giải pháp:**
- Check lại giá trị `brand` và `color`
- Đảm bảo sử dụng giá trị trong bảng "Các giá trị hợp lệ" ở trên
- Color có thể dùng tiếng Việt hoặc tiếng Anh

### Vấn đề 3: Ảnh không load
**Triệu chứng:** Response trả về URL nhưng ảnh không hiển thị

**Giải pháp:**
1. Copy URL từ response và paste vào browser tab mới
2. Nếu thấy ảnh → OK, vấn đề ở FE code
3. Nếu lỗi 403 → S3 bucket chưa public, cần config S3

### Vấn đề 4: 500 Internal Server Error
**Triệu chứng:** Backend trả về lỗi 500

**Giải pháp:**
- Check backend logs
- Đảm bảo parameters được gửi đúng format
- Restart backend

## 📊 Testing

### Test với Browser Console
```javascript
// Paste vào browser console
fetch('http://localhost:8080/api/vehicles/image-url?brand=Vinfast&color=xanh')
  .then(res => res.json())
  .then(data => console.log(data.imageUrl))
  .catch(err => console.error(err));
```

### Test với cURL
```bash
curl "http://localhost:8080/api/vehicles/image-url?brand=Vinfast&color=xanh"
```

### Test với Postman
1. Method: `GET`
2. URL: `http://localhost:8080/api/vehicles/image-url`
3. Params:
   - `brand`: `Vinfast`
   - `color`: `xanh`
4. Send

## 🎨 UI/UX Recommendations

### Loading State
```javascript
const [loading, setLoading] = useState(false);

const fetchImage = async () => {
  setLoading(true);
  try {
    const url = await getVehicleImageUrl(brand, color);
    setImageUrl(url);
  } finally {
    setLoading(false);
  }
};

// Trong JSX
{loading ? <Spinner /> : <img src={imageUrl} />}
```

### Error State
```javascript
const [error, setError] = useState(null);

const fetchImage = async () => {
  try {
    const url = await getVehicleImageUrl(brand, color);
    if (!url) {
      setError('Không tìm thấy ảnh cho lựa chọn này');
    } else {
      setImageUrl(url);
      setError(null);
    }
  } catch (err) {
    setError('Lỗi khi tải ảnh');
  }
};

// Trong JSX
{error && <div className="error">{error}</div>}
```

### Placeholder/Default Image
```javascript
const DEFAULT_IMAGE = '/images/car-placeholder.png';

<img 
  src={imageUrl || DEFAULT_IMAGE} 
  alt={`${brand} ${color}`}
  onError={(e) => e.target.src = DEFAULT_IMAGE}
/>
```

## 📝 Checklist

- [ ] Backend đang chạy tại `http://localhost:8080`
- [ ] Đã test endpoint với Swagger/Postman → Response OK
- [ ] FE có thể gọi API (check Network tab trong DevTools)
- [ ] Response trả về `imageUrl` field
- [ ] Copy URL từ response, paste vào browser → Thấy ảnh
- [ ] Nếu ảnh hiển thị trên browser → Vấn đề ở FE code
- [ ] Nếu ảnh không hiển thị → S3 bucket chưa public

## 🚀 Production Deployment

### Update API Base URL
```javascript
const API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080';

// Hoặc
const API_BASE_URL = 
  process.env.NODE_ENV === 'production' 
    ? 'https://api.yourdomain.com' 
    : 'http://localhost:8080';
```

### Environment Variables (.env)
```
REACT_APP_API_URL=https://api.yourdomain.com
```

---

**Need help?** Check backend logs hoặc browser DevTools Network tab để debug!

