------------------------------------------------------------
-- EV_RENTAL_FINAL (DROP & CREATE)
------------------------------------------------------------
IF DB_ID('ev_rental_final') IS NOT NULL
BEGIN
  ALTER DATABASE ev_rental_final SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
  DROP DATABASE ev_rental_final;
END;
GO
CREATE DATABASE ev_rental_final;
GO
USE ev_rental_final;
GO

/* =========================================================
   1) RentalStation  (gộp Address vào station)
========================================================= */
CREATE TABLE RentalStation (
                               station_id  INT IDENTITY(1,1) PRIMARY KEY,
    [name]      NVARCHAR(100) NOT NULL,
    active      BIT NOT NULL CONSTRAINT DF_RentalStation_Active DEFAULT(1),
    city        NVARCHAR(100) NOT NULL,
    district    NVARCHAR(100) NOT NULL,
    ward        NVARCHAR(100) NOT NULL,
    street      NVARCHAR(255) NOT NULL
    );
GO

/* =========================================================
   2) User (gộp Customer & Staff)
========================================================= */
CREATE TABLE [User] (
                        user_id         INT IDENTITY(1,1) PRIMARY KEY,
    full_name       NVARCHAR(100) NOT NULL,
    [password]      NVARCHAR(100) NOT NULL,
    phone           NVARCHAR(20)  NULL,
    email           NVARCHAR(100) NULL,
    [role]          NVARCHAR(20)  NOT NULL CHECK ([role] IN ('customer','staff')),
    kyc_status      NVARCHAR(50)  NOT NULL CHECK (kyc_status IN ('verified','pending')),
    -- customer-only
    license_number  NVARCHAR(50)  NULL,
    cccd_number     NVARCHAR(50)  NULL,
    cccd_front      NVARCHAR(255) NULL,
    cccd_back       NVARCHAR(255) NULL,
    license_photo   NVARCHAR(255) NULL,
    -- staff-only
    [position]      NVARCHAR(50)  NULL,
    station_id      INT           NULL,
    CONSTRAINT FK_User_Station FOREIGN KEY (station_id) REFERENCES RentalStation(station_id)
    );
GO

/* =========================================================
   3) Vehicle & 4) VehicleAttribute
========================================================= */
CREATE TABLE Vehicle (
                         vehicle_id   INT IDENTITY(1,1) PRIMARY KEY,
                         station_id   INT NOT NULL,
                         plate_number NVARCHAR(20) NOT NULL,
                         status       NVARCHAR(20) NOT NULL CHECK (status IN ('available','rented','maintenance','broken')),
                         seat_count   INT NOT NULL CHECK (seat_count IN (4,7)),
                         variant      NVARCHAR(10) NULL, -- 4 chỗ: NULL/'standard'; 7 chỗ: 'air'|'plus'|'pro'
                         CONSTRAINT UQ_Vehicle_Plate UNIQUE (plate_number),
                         CONSTRAINT FK_Vehicle_Station FOREIGN KEY (station_id) REFERENCES RentalStation(station_id),
                         CONSTRAINT CK_Vehicle_SeatVariant
                             CHECK (
                                 (seat_count = 4 AND (variant IS NULL OR variant = 'standard')) OR
                                 (seat_count = 7 AND variant IN ('air','plus','pro'))
                                 )
);
GO

CREATE TABLE VehicleAttribute (
                                  attr_id     INT IDENTITY(1,1) PRIMARY KEY,
                                  vehicle_id  INT NOT NULL,
                                  attr_name   NVARCHAR(50) NOT NULL,
    [value]     NVARCHAR(100) NOT NULL,
    CONSTRAINT FK_VAttr_Vehicle FOREIGN KEY (vehicle_id) REFERENCES Vehicle(vehicle_id)
    );
GO

/* =========================================================
   5) PricingRule  & 6) HolidayDates
   - 3 giờ đầu (base_hours=3), +1 giờ, giá ngày, phụ thu T7-CN/Lễ (+100k)
   - max_days = 7
========================================================= */
CREATE TABLE PricingRule (
                             pricing_rule_id           INT IDENTITY(1,1) PRIMARY KEY,
                             vehicle_id                INT NOT NULL UNIQUE,
                             base_hours                TINYINT NOT NULL CONSTRAINT DF_PR_BaseHours DEFAULT(3),
                             base_hours_price          DECIMAL(12,0) NOT NULL,
                             extra_hour_price          DECIMAL(12,0) NOT NULL,
                             daily_price               DECIMAL(12,0) NOT NULL,
                             weekend_daily_surcharge   DECIMAL(12,0) NOT NULL CONSTRAINT DF_PR_Wknd DEFAULT(100000),
                             max_days                  TINYINT NOT NULL CONSTRAINT DF_PR_MaxDays DEFAULT(7),
                             created_at                DATETIME NOT NULL CONSTRAINT DF_PR_Created DEFAULT(GETDATE()),
                             CONSTRAINT FK_PR_Vehicle  FOREIGN KEY (vehicle_id) REFERENCES Vehicle(vehicle_id),
                             CONSTRAINT CK_PR_BaseHours CHECK (base_hours = 3),
                             CONSTRAINT CK_PR_MaxDays   CHECK (max_days BETWEEN 1 AND 30)
);
GO

CREATE TABLE HolidayDates (
    [date] DATE PRIMARY KEY,
    [name] NVARCHAR(100) NOT NULL
    );
GO

/* =========================================================
   7) RentalOrder  (FK -> User (customer), Vehicle)
========================================================= */
CREATE TABLE RentalOrder (
                             order_id     INT IDENTITY(1,1) PRIMARY KEY,
                             customer_id  INT NOT NULL,
                             vehicle_id   INT NOT NULL,
                             start_time   DATETIME NOT NULL,
                             end_time     DATETIME NOT NULL,
                             total_price  DECIMAL(12,0) NULL,
    [status]     NVARCHAR(20) NOT NULL CHECK ([status] IN ('pending','active','completed','cancelled')),
    CONSTRAINT FK_RO_Customer FOREIGN KEY (customer_id) REFERENCES [User](user_id),
    CONSTRAINT FK_RO_Vehicle  FOREIGN KEY (vehicle_id)  REFERENCES Vehicle(vehicle_id),
    CONSTRAINT CK_RO_Time     CHECK (end_time > start_time),
    CONSTRAINT CK_RO_Max7Day  CHECK (end_time <= DATEADD(day, 7, start_time))
    );
GO

/* =========================================================
   8) Payment
========================================================= */
CREATE TABLE Payment (
                         payment_id  INT IDENTITY(1,1) PRIMARY KEY,
                         order_id    INT NOT NULL,
                         amount      DECIMAL(12,0) NOT NULL,
                         method      NVARCHAR(20) NOT NULL CHECK (method IN ('card','cash','momo')),
    [status]    NVARCHAR(20) NOT NULL CHECK ([status] IN ('paid','failed','pending')),
    created_at  DATETIME NOT NULL CONSTRAINT DF_Payment_Created DEFAULT(GETDATE()),
    CONSTRAINT FK_Payment_Order FOREIGN KEY (order_id) REFERENCES RentalOrder(order_id)
    );
GO

/* =========================================================
   9) PenaltyPolicy  & 10) Penalty
========================================================= */
CREATE TABLE PenaltyPolicy (
                               policy_id INT IDENTITY(1,1) PRIMARY KEY,
                               category  NVARCHAR(30) NOT NULL,
                               severity  NVARCHAR(30) NOT NULL,
    [unit]    NVARCHAR(20) NULL,               -- 'hour','item','service','flat'
    default_unit_price DECIMAL(12,0) NULL,
    default_min_fee    DECIMAL(12,0) NULL,
    default_max_fee    DECIMAL(12,0) NULL,
    [description]      NVARCHAR(255) NULL,
    CONSTRAINT CK_Policy_Category CHECK (category IN ('late_return','glass','paint','mirror','odor','interior'))
    );
GO

CREATE TABLE Penalty (
                         penalty_id  INT IDENTITY(1,1) PRIMARY KEY,
                         order_id    INT NOT NULL,
    [name]      NVARCHAR(100) NOT NULL,
    [description] NVARCHAR(255) NULL,
    fee         DECIMAL(12,0) NOT NULL,
    note        NVARCHAR(255) NULL,
    category    NVARCHAR(30) NOT NULL CONSTRAINT DF_Penalty_Category DEFAULT 'other',
    severity    NVARCHAR(30) NULL,
    [unit]      NVARCHAR(20) NULL,
    qty         DECIMAL(10,2) NULL,
    unit_price  DECIMAL(12,0) NULL,
    min_fee     DECIMAL(12,0) NULL,
    max_fee     DECIMAL(12,0) NULL,
    policy_id   INT NULL,
    CONSTRAINT FK_Penalty_Order  FOREIGN KEY (order_id)  REFERENCES RentalOrder(order_id),
    CONSTRAINT FK_Penalty_Policy FOREIGN KEY (policy_id) REFERENCES PenaltyPolicy(policy_id),
    CONSTRAINT CK_Penalty_Category CHECK (category IN ('late_return','glass','paint','mirror','odor','interior','other'))
    );
GO

/* =========================================================
   11) DamageReport
========================================================= */
CREATE TABLE DamageReport (
                              report_id    INT IDENTITY(1,1) PRIMARY KEY,
                              vehicle_id   INT NOT NULL,
                              reporter_id  INT NOT NULL,
                              station_id   INT NOT NULL,
    [description] NVARCHAR(500) NOT NULL,
    [status]     NVARCHAR(20) NOT NULL CHECK ([status] IN ('pending','in_progress','resolved')),
    created_at   DATETIME NOT NULL CONSTRAINT DF_Dam_Created DEFAULT(GETDATE()),
    resolved_at  DATETIME NULL,
    handled_by   INT NULL,
    CONSTRAINT FK_Dam_Vehicle  FOREIGN KEY (vehicle_id)  REFERENCES Vehicle(vehicle_id),
    CONSTRAINT FK_Dam_User1    FOREIGN KEY (reporter_id) REFERENCES [User](user_id),
    CONSTRAINT FK_Dam_Station  FOREIGN KEY (station_id)  REFERENCES RentalStation(station_id),
    CONSTRAINT FK_Dam_User2    FOREIGN KEY (handled_by)  REFERENCES [User](user_id)
    );
GO

/* =========================================================
   12) Feedback (1–1 với RentalOrder)
========================================================= */
CREATE TABLE Feedback (
                          feedback_id INT IDENTITY(1,1) PRIMARY KEY,
                          order_id    INT NOT NULL UNIQUE,
                          rating      INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
                          comment     NVARCHAR(255) NULL,
                          created_at  DATETIME NOT NULL CONSTRAINT DF_Fb_Created DEFAULT(GETDATE()),
                          CONSTRAINT FK_Feedback_Order FOREIGN KEY (order_id) REFERENCES RentalOrder(order_id)
);
GO

/* =========================================================
   13) Photo
========================================================= */
CREATE TABLE Photo (
                       photo_id     INT IDENTITY(1,1) PRIMARY KEY,
                       entity_type  NVARCHAR(50) NOT NULL, -- vehicle, user, order, damage_report, contract
                       entity_id    INT NOT NULL,
                       url          NVARCHAR(255) NOT NULL,
                       uploaded_at  DATETIME NOT NULL CONSTRAINT DF_Photo_Uploaded DEFAULT(GETDATE())
);
GO

/* =========================================================
   14) RentalTracking
========================================================= */
CREATE TABLE RentalTracking(
                               tracking_id     INT IDENTITY(1,1) PRIMARY KEY,
                               rental_order_id INT NOT NULL,
                               vehicle_id      INT NOT NULL,
                               pickup_at       DATETIME NULL,
                               return_due_at   DATETIME NULL,
                               return_at       DATETIME NULL,
    [status]        NVARCHAR(20) NOT NULL CONSTRAINT DF_RT_Status DEFAULT 'ONGOING', -- ONGOING, CLOSED, LATE, DISPUTE
    created_at      DATETIME NOT NULL CONSTRAINT DF_RT_Created DEFAULT GETDATE(),
    updated_at      DATETIME NOT NULL CONSTRAINT DF_RT_Updated DEFAULT GETDATE(),
    CONSTRAINT FK_RT_Order   FOREIGN KEY (rental_order_id) REFERENCES RentalOrder(order_id),
    CONSTRAINT FK_RT_Vehicle FOREIGN KEY (vehicle_id)      REFERENCES Vehicle(vehicle_id)
    );
GO

/* =========================================================
   Function + View hỗ trợ
========================================================= */
IF OBJECT_ID('dbo.udf_EffectiveDailyPrice','FN') IS NOT NULL DROP FUNCTION dbo.udf_EffectiveDailyPrice;
GO
CREATE FUNCTION dbo.udf_EffectiveDailyPrice
(
    @vehicle_id INT,
    @forDate    DATE
)
    RETURNS DECIMAL(12,0)
AS
BEGIN
    DECLARE @base  DECIMAL(12,0),
            @surch DECIMAL(12,0);

SELECT @base = daily_price, @surch = weekend_daily_surcharge
FROM PricingRule WHERE vehicle_id = @vehicle_id;

IF (@base IS NULL) RETURN NULL;

    IF (DATENAME(WEEKDAY, @forDate) IN ('Saturday','Sunday')
        OR EXISTS (SELECT 1 FROM HolidayDates h WHERE h.[date] = @forDate))
        RETURN @base + @surch;

RETURN @base;
END;
GO

IF OBJECT_ID('dbo.vwPenaltySuggested','V') IS NOT NULL DROP VIEW dbo.vwPenaltySuggested;
GO
CREATE VIEW dbo.vwPenaltySuggested AS
SELECT p.penalty_id, p.order_id, p.[name], p.[description], p.fee,
       p.category, p.severity, p.[unit], p.qty, p.unit_price, p.min_fee, p.max_fee,
       pol.default_unit_price, pol.default_min_fee, pol.default_max_fee,
       CASE
           WHEN p.[unit] IS NOT NULL AND p.qty IS NOT NULL
               THEN CAST(p.qty * COALESCE(p.unit_price, pol.default_unit_price) AS DECIMAL(12,0))
           WHEN COALESCE(p.min_fee, pol.default_min_fee) IS NOT NULL
               AND COALESCE(p.max_fee, pol.default_max_fee) IS NOT NULL
               AND COALESCE(p.min_fee, pol.default_min_fee) = COALESCE(p.max_fee, pol.default_max_fee)
               THEN COALESCE(p.min_fee, pol.default_min_fee)
           ELSE NULL
           END AS suggested_fee
FROM dbo.Penalty p
         LEFT JOIN dbo.PenaltyPolicy pol ON p.policy_id = pol.policy_id;
GO

/* =========================================================
   ============= SEED DỮ LIỆU ẢO TEST WEBSITE =============
========================================================= */

-- Stations
INSERT INTO RentalStation ([name], active, city, district, ward, street) VALUES
('Hanoi EV Station 1',1,'Hanoi','Cau Giay','Dich Vong','123 Nguyen Phong Sac'),
('Hanoi EV Station 2',1,'Hanoi','Ba Dinh','Nguyen Trung','456 Kim Ma'),
('HCM EV Station 1',  1,'HCM','District 1','Ben Nghe','789 Le Loi'),
('HCM EV Station 2',  1,'HCM','District 3','Ward 10','101 Pasteur'),
('Danang EV Station', 1,'Danang','Hai Chau','Ward 5','102 Bach Dang'),
('Hue EV Station',    1,'Hue','Hue City','Ward 2','103 Le Loi'),
('Can Tho EV Station',1,'Can Tho','Ninh Kieu','Ward 4','104 Nguyen Trai'),
('Hai Phong EV Station',1,'Hai Phong','Le Chan','Ward 7','105 Tran Phu'),
('Vung Tau EV Station', 1,'Vung Tau','Thang Tam','Ward 1','106 Le Hong Phong'),
('Binh Duong EV Station',1,'Binh Duong','Thu Dau Mot','Ward 3','107 Tran Hung Dao');
GO

-- Users: 5 customers + 5 staff
INSERT INTO [User](full_name,[password],phone,email,[role],kyc_status,license_number,cccd_number,cccd_front,cccd_back,license_photo) VALUES
(N'Nguyen Van A','pass1','0987654321','a1@gmail.com','customer','verified','AB123','123456789','url_cccdf1','url_cccdb1','url_license1'),
(N'Tran Thi B','pass2','0912345678','a2@gmail.com','customer','pending','AB124','223456789','url_cccdf2','url_cccdb2','url_license2'),
(N'Le Van C','pass3','0901234567','a3@gmail.com','customer','verified','AB125','323456789','url_cccdf3','url_cccdb3','url_license3'),
(N'Pham Thi D','pass4','0976543210','a4@gmail.com','customer','verified','AB126','423456789','url_cccdf4','url_cccdb4','url_license4'),
(N'Hoang Van E','pass5','0932123456','a5@gmail.com','customer','pending','AB127','523456789','url_cccdf5','url_cccdb5','url_license5');
INSERT INTO [User](full_name,[password],phone,email,[role],kyc_status,[position],station_id) VALUES
    (N'Nguyen Thi F','pass6','0923456789','f1@gmail.com','staff','verified','Manager',1),
    (N'Tran Van G','pass7','0965432109','g1@gmail.com','staff','verified','Technician',2),
    (N'Le Thi H','pass8','0919876543','h1@gmail.com','staff','pending','Manager',3),
    (N'Pham Van I','pass9','0987123456','i1@gmail.com','staff','verified','Technician',4),
    (N'Hoang Thi J','pass10','0908765432','j1@gmail.com','staff','verified','Staff',5);
GO

-- Vehicles (4 chỗ & 7 chỗ)
INSERT INTO Vehicle (station_id,plate_number,status,seat_count,variant) VALUES
(1,'30A-11111','available',   4, NULL),
(2,'30A-11112','rented',      4, NULL),
(3,'30A-11113','maintenance', 4, 'standard'),
(4,'30A-11114','broken',      7, 'air'),
(5,'30A-11115','available',   7, 'plus'),
(6,'30A-11116','available',   7, 'pro'),
(7,'30A-11117','rented',      4, NULL),
(8,'30A-11118','maintenance', 7, 'air'),
(9,'30A-11119','broken',      7, 'plus'),
(10,'30A-11120','available',  7, 'pro');
GO

-- Vehicle attributes (tham chiếu theo ID mới tạo 1..10)
INSERT INTO VehicleAttribute (vehicle_id,attr_name,[value]) VALUES
(1,'color','white'),(1,'battery','75kWh'),
(2,'color','black'),(2,'battery','60kWh'),
(3,'color','blue'), (3,'battery','70kWh'),
(4,'color','red'),  (4,'battery','65kWh'),
(5,'color','green'),(5,'battery','80kWh'),
(6,'color','silver'),(6,'battery','90kWh'),
(7,'color','yellow'),(7,'battery','55kWh'),
(8,'color','gray'), (8,'battery','60kWh'),
(9,'color','pink'), (9,'battery','50kWh'),
(10,'color','orange'),(10,'battery','85kWh');
GO

-- Pricing rules (VND)
-- 4 chỗ: 3h=50,000; +1h=27,000; ngày=650,000
INSERT INTO PricingRule (vehicle_id, base_hours_price, extra_hour_price, daily_price)
SELECT vehicle_id, 50000, 27000, 650000 FROM Vehicle WHERE seat_count = 4;
-- 7 chỗ (air/plus/pro)
INSERT INTO PricingRule (vehicle_id, base_hours_price, extra_hour_price, daily_price)
SELECT vehicle_id,
       CASE variant WHEN 'air' THEN 150000 WHEN 'plus' THEN 180000 ELSE 200000 END,
       CASE variant WHEN 'air' THEN  69000 WHEN 'plus' THEN  79000 ELSE  89000 END,
       CASE variant WHEN 'air' THEN 1450000 WHEN 'plus' THEN 1700000 ELSE 1800000 END
FROM Vehicle WHERE seat_count = 7;
GO

-- Holidays
INSERT INTO HolidayDates ([date],[name]) VALUES
('2025-01-01',N'New Year'),
('2025-04-30',N'Liberation Day'),
('2025-05-01',N'International Workers'' Day');
GO

-- Rental orders (≤7 ngày) — dùng subquery lấy id theo email/biển số
INSERT INTO RentalOrder (customer_id,vehicle_id,start_time,end_time,total_price,[status]) VALUES
((SELECT user_id FROM [User] WHERE email='a1@gmail.com'),
 (SELECT vehicle_id FROM Vehicle WHERE plate_number='30A-11111'),
 '2025-09-20 09:00','2025-09-20 12:00',300000,'pending'),
((SELECT user_id FROM [User] WHERE email='a2@gmail.com'),
 (SELECT vehicle_id FROM Vehicle WHERE plate_number='30A-11112'),
 '2025-09-20 10:00','2025-09-20 14:00',400000,'active'),
((SELECT user_id FROM [User] WHERE email='a3@gmail.com'),
 (SELECT vehicle_id FROM Vehicle WHERE plate_number='30A-11113'),
 '2025-09-21 09:00','2025-09-21 11:00',250000,'completed'),
((SELECT user_id FROM [User] WHERE email='a4@gmail.com'),
 (SELECT vehicle_id FROM Vehicle WHERE plate_number='30A-11114'),
 '2025-09-21 10:00','2025-09-21 13:00',350000,'cancelled'),
((SELECT user_id FROM [User] WHERE email='a5@gmail.com'),
 (SELECT vehicle_id FROM Vehicle WHERE plate_number='30A-11115'),
 '2025-09-22 08:00','2025-09-22 12:00',500000,'pending');
GO

-- Payments (tham chiếu order bằng subquery)
INSERT INTO Payment (order_id,amount,method,[status]) VALUES
((SELECT order_id FROM RentalOrder WHERE customer_id=(SELECT user_id FROM [User] WHERE email='a1@gmail.com')
  AND vehicle_id=(SELECT vehicle_id FROM Vehicle WHERE plate_number='30A-11111') AND start_time='2025-09-20 09:00'),
 300000,'card','paid'),
((SELECT order_id FROM RentalOrder WHERE customer_id=(SELECT user_id FROM [User] WHERE email='a2@gmail.com')
  AND vehicle_id=(SELECT vehicle_id FROM Vehicle WHERE plate_number='30A-11112') AND start_time='2025-09-20 10:00'),
 400000,'cash','paid'),
((SELECT order_id FROM RentalOrder WHERE customer_id=(SELECT user_id FROM [User] WHERE email='a3@gmail.com')
  AND vehicle_id=(SELECT vehicle_id FROM Vehicle WHERE plate_number='30A-11113') AND start_time='2025-09-21 09:00'),
 250000,'momo','paid'),
((SELECT order_id FROM RentalOrder WHERE customer_id=(SELECT user_id FROM [User] WHERE email='a4@gmail.com')
  AND vehicle_id=(SELECT vehicle_id FROM Vehicle WHERE plate_number='30A-11114') AND start_time='2025-09-21 10:00'),
 350000,'card','failed'),
((SELECT order_id FROM RentalOrder WHERE customer_id=(SELECT user_id FROM [User] WHERE email='a5@gmail.com')
  AND vehicle_id=(SELECT vehicle_id FROM Vehicle WHERE plate_number='30A-11115') AND start_time='2025-09-22 08:00'),
 500000,'cash','pending');
GO

-- Penalty policies
INSERT INTO PenaltyPolicy (category, severity, [unit], default_unit_price, default_min_fee, default_max_fee, [description]) VALUES
('late_return','per_hour','hour',100000,NULL,NULL,N'Trá xe trễ: +100k/giờ'),
('glass','replace',NULL,NULL,1000000,2000000,N'Thay kính 1–2tr'),
('glass','scratch_light',NULL,NULL,100000,150000,N'Kính trầy nhẹ 100–150k'),
('paint','scratch_light',NULL,NULL,150000,150000,N'Sơn xước nhẹ 150k'),
('paint','scratch_dent',NULL,NULL,1000000,1000000,N'Xước + móp 1tr'),
('paint','severe_negotiated',NULL,NULL,NULL,NULL,N'Hư hại nặng: thương lượng'),
('mirror','damage_range',NULL,NULL,500000,3000000,N'Gương: 500k–3tr'),
('odor','basic_clean','service',NULL,250000,250000,N'Vệ sinh mùi cơ bản 250k'),
('interior','negotiated',NULL,NULL,NULL,NULL,N'Nội thất: thương lượng');
GO

-- Penalties (10 dòng đa dạng) — order_id & policy_id lấy bằng subquery
INSERT INTO Penalty (order_id,[name],[description],fee,note,category,severity,[unit],qty,unit_price,policy_id) VALUES
((SELECT order_id FROM RentalOrder WHERE customer_id=(SELECT user_id FROM [User] WHERE email='a1@gmail.com')
  AND vehicle_id=(SELECT vehicle_id FROM Vehicle WHERE plate_number='30A-11111') AND start_time='2025-09-20 09:00'),
 N'Late Return', N'Trễ 1 giờ', 100000, N'Cộng 100k/giờ', 'late_return','per_hour','hour',1,100000,
 (SELECT policy_id FROM PenaltyPolicy WHERE category='late_return' AND severity='per_hour')),
((SELECT order_id FROM RentalOrder WHERE customer_id=(SELECT user_id FROM [User] WHERE email='a2@gmail.com')
  AND vehicle_id=(SELECT vehicle_id FROM Vehicle WHERE plate_number='30A-11112') AND start_time='2025-09-20 10:00'),
 N'Late Return', N'Trễ 2 giờ', 200000, N'Cộng 100k/giờ', 'late_return','per_hour','hour',2,100000,
 (SELECT policy_id FROM PenaltyPolicy WHERE category='late_return' AND severity='per_hour'));
INSERT INTO Penalty (order_id,[name],[description],fee,note,category,severity,min_fee,max_fee,policy_id) VALUES
    ((SELECT order_id FROM RentalOrder WHERE customer_id=(SELECT user_id FROM [User] WHERE email='a3@gmail.com')
    AND vehicle_id=(SELECT vehicle_id FROM Vehicle WHERE plate_number='30A-11113') AND start_time='2025-09-21 09:00'),
 N'Glass Replace', N'Thay kính trước', 1500000, N'Trong khung 1–2tr', 'glass','replace',1000000,2000000,
 (SELECT policy_id FROM PenaltyPolicy WHERE category='glass' AND severity='replace')),
                                          ((SELECT order_id FROM RentalOrder WHERE customer_id=(SELECT user_id FROM [User] WHERE email='a4@gmail.com')
                                                                               AND vehicle_id=(SELECT vehicle_id FROM Vehicle WHERE plate_number='30A-11114') AND start_time='2025-09-21 10:00'),
    N'Glass Scratch Light', N'Trầy nhẹ kính hông', 120000, N'100–150k', 'glass','scratch_light',100000,150000,
                                                                                 (SELECT policy_id FROM PenaltyPolicy WHERE category='glass' AND severity='scratch_light')),
                                                                                                                          ((SELECT order_id FROM RentalOrder WHERE customer_id=(SELECT user_id FROM [User] WHERE email='a5@gmail.com')
                                                                                                                                                               AND vehicle_id=(SELECT vehicle_id FROM Vehicle WHERE plate_number='30A-11115') AND start_time='2025-09-22 08:00'),
    N'Paint Scratch Light', N'Xước nhẹ cánh cửa', 150000, N'Fixed 150k', 'paint','scratch_light',150000,150000,
                                                                                                                                                                 (SELECT policy_id FROM PenaltyPolicy WHERE category='paint' AND severity='scratch_light')),
                                                                                                                                                                                                          ((SELECT order_id FROM RentalOrder WHERE customer_id=(SELECT user_id FROM [User] WHERE email='a1@gmail.com')
                                                                                                                                                                                                                                               AND vehicle_id=(SELECT vehicle_id FROM Vehicle WHERE plate_number='30A-11111') AND start_time='2025-09-20 09:00'),
    N'Paint Scratch + Dent', N'Xước + móp nhẹ', 1000000, N'Fixed 1tr', 'paint','scratch_dent',1000000,1000000,
                                                                                                                                                                                                                                                 (SELECT policy_id FROM PenaltyPolicy WHERE category='paint' AND severity='scratch_dent')),
                                                                                                                                                                                                                                                                                          ((SELECT order_id FROM RentalOrder WHERE customer_id=(SELECT user_id FROM [User] WHERE email='a2@gmail.com')
                                                                                                                                                                                                                                                                                                                               AND vehicle_id=(SELECT vehicle_id FROM Vehicle WHERE plate_number='30A-11112') AND start_time='2025-09-20 10:00'),
    N'Paint Severe', N'Hư hại nghiêm trọng', 2500000, N'Thương lượng', 'paint','severe_negotiated',NULL,NULL,
                                                                                                                                                                                                                                                                                                                                 (SELECT policy_id FROM PenaltyPolicy WHERE category='paint' AND severity='severe_negotiated')),
                                                                                                                                                                                                                                                                                                                                                                          ((SELECT order_id FROM RentalOrder WHERE customer_id=(SELECT user_id FROM [User] WHERE email='a3@gmail.com')
                                                                                                                                                                                                                                                                                                                                                                                                               AND vehicle_id=(SELECT vehicle_id FROM Vehicle WHERE plate_number='30A-11113') AND start_time='2025-09-21 09:00'),
    N'Mirror Damage', N'Nứt gương trái', 800000, N'500k–3tr', 'mirror','damage_range',500000,3000000,
                                                                                                                                                                                                                                                                                                                                                                                                                 (SELECT policy_id FROM PenaltyPolicy WHERE category='mirror' AND severity='damage_range')),
                                                                                                                                                                                                                                                                                                                                                                                                                                                          ((SELECT order_id FROM RentalOrder WHERE customer_id=(SELECT user_id FROM [User] WHERE email='a4@gmail.com')
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               AND vehicle_id=(SELECT vehicle_id FROM Vehicle WHERE plate_number='30A-11114') AND start_time='2025-09-21 10:00'),
    N'Odor Cleaning', N'Vệ sinh mùi cơ bản', 250000, N'Gói 250k', 'odor','basic_clean',250000,250000,
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 (SELECT policy_id FROM PenaltyPolicy WHERE category='odor' AND severity='basic_clean')),
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          ((SELECT order_id FROM RentalOrder WHERE customer_id=(SELECT user_id FROM [User] WHERE email='a5@gmail.com')
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               AND vehicle_id=(SELECT vehicle_id FROM Vehicle WHERE plate_number='30A-11115') AND start_time='2025-09-22 08:00'),
    N'Interior Damage', N'Rách ghế sau', 2200000, N'Thương lượng', 'interior','negotiated',NULL,NULL,
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 (SELECT policy_id FROM PenaltyPolicy WHERE category='interior' AND severity='negotiated'));
GO

-- Damage reports (reporter/handled_by lấy theo email; station theo tên)
INSERT INTO DamageReport (vehicle_id,reporter_id,station_id,[description],[status],handled_by) VALUES
((SELECT vehicle_id FROM Vehicle WHERE plate_number='30A-11111'),
 (SELECT user_id FROM [User] WHERE email='a1@gmail.com'),
 (SELECT station_id FROM RentalStation WHERE [name]='Hanoi EV Station 1'),
 N'Scratch on bumper','pending',NULL),
((SELECT vehicle_id FROM Vehicle WHERE plate_number='30A-11112'),
 (SELECT user_id FROM [User] WHERE email='a2@gmail.com'),
 (SELECT station_id FROM RentalStation WHERE [name]='Hanoi EV Station 2'),
 N'Broken mirror','in_progress',NULL),
((SELECT vehicle_id FROM Vehicle WHERE plate_number='30A-11113'),
 (SELECT user_id FROM [User] WHERE email='a3@gmail.com'),
 (SELECT station_id FROM RentalStation WHERE [name]='HCM EV Station 1'),
 N'Cracked window','resolved',
 (SELECT user_id FROM [User] WHERE email='f1@gmail.com')),
((SELECT vehicle_id FROM Vehicle WHERE plate_number='30A-11114'),
 (SELECT user_id FROM [User] WHERE email='a4@gmail.com'),
 (SELECT station_id FROM RentalStation WHERE [name]='HCM EV Station 2'),
 N'Flat tire','pending',NULL),
((SELECT vehicle_id FROM Vehicle WHERE plate_number='30A-11115'),
 (SELECT user_id FROM [User] WHERE email='a5@gmail.com'),
 (SELECT station_id FROM RentalStation WHERE [name]='Danang EV Station'),
 N'Dented door','in_progress',
 (SELECT user_id FROM [User] WHERE email='g1@gmail.com'));
GO

-- Feedbacks (1–1 với order)
INSERT INTO Feedback (order_id,rating,comment) VALUES
((SELECT order_id FROM RentalOrder WHERE customer_id=(SELECT user_id FROM [User] WHERE email='a1@gmail.com')
  AND vehicle_id=(SELECT vehicle_id FROM Vehicle WHERE plate_number='30A-11111') AND start_time='2025-09-20 09:00'),
 5, N'Excellent service'),
((SELECT order_id FROM RentalOrder WHERE customer_id=(SELECT user_id FROM [User] WHERE email='a2@gmail.com')
  AND vehicle_id=(SELECT vehicle_id FROM Vehicle WHERE plate_number='30A-11112') AND start_time='2025-09-20 10:00'),
 4, N'Good experience'),
((SELECT order_id FROM RentalOrder WHERE customer_id=(SELECT user_id FROM [User] WHERE email='a3@gmail.com')
  AND vehicle_id=(SELECT vehicle_id FROM Vehicle WHERE plate_number='30A-11113') AND start_time='2025-09-21 09:00'),
 3, N'Average');
GO

-- Photos
INSERT INTO Photo (entity_type,entity_id,url) VALUES
('vehicle',(SELECT vehicle_id FROM Vehicle WHERE plate_number='30A-11111'),'url_vehicle1.jpg'),
('vehicle',(SELECT vehicle_id FROM Vehicle WHERE plate_number='30A-11112'),'url_vehicle2.jpg'),
('order',(SELECT order_id FROM RentalOrder WHERE customer_id=(SELECT user_id FROM [User] WHERE email='a1@gmail.com')
          AND vehicle_id=(SELECT vehicle_id FROM Vehicle WHERE plate_number='30A-11111') AND start_time='2025-09-20 09:00'),
 'url_order1.jpg'),
('damage_report',(SELECT TOP 1 report_id FROM DamageReport WHERE [description]=N'Scratch on bumper'),'url_damage1.jpg');
GO

-- Tracking (tạo theo 5 order seed)
INSERT INTO RentalTracking (rental_order_id, vehicle_id, pickup_at, return_due_at, [status])
SELECT order_id, vehicle_id, start_time, end_time, 'ONGOING'
FROM RentalOrder;
GO

/* =======================
   Quick checks (tuỳ chọn)
======================= */
-- SELECT COUNT(*) AS table_count FROM sys.tables;  -- 14
-- SELECT [date],[name] FROM HolidayDates ORDER BY [date];
-- SELECT 'Orders' what, COUNT(*) cnt FROM RentalOrder
-- UNION ALL SELECT 'Payments', COUNT(*) FROM Payment
-- UNION ALL SELECT 'Penalties', COUNT(*) FROM Penalty
-- UNION ALL SELECT 'Feedbacks', COUNT(*) FROM Feedback
-- UNION ALL SELECT 'Tracking', COUNT(*) FROM RentalTracking;
