-- Tạo database
CREATE DATABASE ev_rental_2;
GO
USE ev_rental_2;
GO

-- ======================================
-- ADDRESS & RENTAL STATION
-- ======================================
CREATE TABLE Address (
    address_id INT PRIMARY KEY IDENTITY(1,1),
    city NVARCHAR(100),
    district NVARCHAR(100),
    ward NVARCHAR(100),
    street NVARCHAR(255)
);

CREATE TABLE RentalStation (
    station_id INT PRIMARY KEY IDENTITY(1,1),
    name NVARCHAR(100),
    address_id INT,
    active BIT,
    FOREIGN KEY (address_id) REFERENCES Address(address_id)
);

CREATE TABLE RentalTracking(
    tracking_id INT PRIMARY KEY IDENTITY(1,1),
    rental_order_id INT NOT NULL,
    vehicle_id INT NOT NULL,
    pickup_at DATETIME,
    return_due_at DATETIME,
    return_at DATETIME,
    status NVARCHAR(20) DEFAULT 'ONGOING', -- ONGOING, CLOSED, LATE, DISPUTE
    created_at DATETIME DEFAULT GETDATE(),
    updated_at DATETIME DEFAULT GETDATE(),
    CONSTRAINT fk_tracking_order FOREIGN KEY (rental_order_id) REFERENCES RentalOrder(order_id),
    FOREIGN KEY (vehicle_id) REFERENCES Vehicle(vehicle_id)
);

-- ======================================
-- USER & ROLE
-- ======================================
CREATE TABLE [User] (
    user_id INT PRIMARY KEY IDENTITY(1,1),
    full_name NVARCHAR(100),
    password NVARCHAR(100),
    phone NVARCHAR(20),
    role NVARCHAR(20) CHECK (role IN ('customer','staff')),
    kyc_status NVARCHAR(50) CHECK (kyc_status IN ('verified','pending'))
);

CREATE TABLE Customer (
    customer_id INT PRIMARY KEY,
    email NVARCHAR(100),
    license_number NVARCHAR(50),
    cccd_number NVARCHAR(50),
    cccd_front NVARCHAR(255),
    cccd_back NVARCHAR(255),
    license_photo NVARCHAR(255),
    FOREIGN KEY (customer_id) REFERENCES [User](user_id)
);

CREATE TABLE Staff (
    staff_id INT PRIMARY KEY,
    email NVARCHAR(100),
    position NVARCHAR(50),
    station_id INT,
    FOREIGN KEY (staff_id) REFERENCES [User](user_id),
    FOREIGN KEY (station_id) REFERENCES RentalStation(station_id)
);

-- ======================================
-- VEHICLE & ATTRIBUTES
-- ======================================
CREATE TABLE Vehicle (
    vehicle_id INT PRIMARY KEY IDENTITY(1,1),
    station_id INT,
    plate_number NVARCHAR(20),
    status NVARCHAR(20) CHECK (status IN ('available','rented','maintenance','broken')),
    FOREIGN KEY (station_id) REFERENCES RentalStation(station_id)
);

CREATE TABLE VehicleAttribute (
    attr_id INT PRIMARY KEY IDENTITY(1,1),
    vehicle_id INT,
    attr_name NVARCHAR(50),
    value NVARCHAR(100),
    FOREIGN KEY (vehicle_id) REFERENCES Vehicle(vehicle_id)
);

-- ======================================
-- PRICING RULE
-- ======================================
CREATE TABLE PricingRule (
    pricing_rule_id INT PRIMARY KEY IDENTITY(1,1),
    vehicle_id INT,
    rule_type NVARCHAR(20) CHECK (rule_type IN ('hourly','distance')),
    unit_limit INT,
    price DECIMAL(10,2),
    created_at DATETIME DEFAULT GETDATE(),
    FOREIGN KEY (vehicle_id) REFERENCES Vehicle(vehicle_id)
);

-- ======================================
-- RENTAL ORDER
-- ======================================
CREATE TABLE RentalOrder (
    order_id INT PRIMARY KEY IDENTITY(1,1),
    customer_id INT,
    vehicle_id INT,
    start_time DATETIME,
    end_time DATETIME,
    total_price DECIMAL(10,2),
    status NVARCHAR(20) CHECK (status IN ('pending','active','completed','cancelled')),
    FOREIGN KEY (customer_id) REFERENCES Customer(customer_id),
    FOREIGN KEY (vehicle_id) REFERENCES Vehicle(vehicle_id)
);

-- ======================================
-- PAYMENT & PENALTY
-- ======================================
CREATE TABLE Payment (
    payment_id INT PRIMARY KEY IDENTITY(1,1),
    order_id INT,
    amount DECIMAL(10,2),
    method NVARCHAR(20) CHECK (method IN ('card','cash','momo')),
    status NVARCHAR(20) CHECK (status IN ('paid','failed','pending')),
    created_at DATETIME DEFAULT GETDATE(),
    FOREIGN KEY (order_id) REFERENCES RentalOrder(order_id)
);

CREATE TABLE Penalty (
    penalty_id INT PRIMARY KEY IDENTITY(1,1),
    order_id INT,
    name NVARCHAR(100),
    description NVARCHAR(255),
    fee DECIMAL(10,2),
    note NVARCHAR(255),
    FOREIGN KEY (order_id) REFERENCES RentalOrder(order_id)
);

-- ======================================
-- DAMAGE REPORT
-- ======================================
CREATE TABLE DamageReport (
    report_id INT PRIMARY KEY IDENTITY(1,1),
    vehicle_id INT,
    reporter_id INT,
    station_id INT,
    description NVARCHAR(500),
    status NVARCHAR(20) CHECK (status IN ('pending','in_progress','resolved')),
    created_at DATETIME DEFAULT GETDATE(),
    resolved_at DATETIME NULL,
    handled_by INT NULL,
    FOREIGN KEY (vehicle_id) REFERENCES Vehicle(vehicle_id),
    FOREIGN KEY (reporter_id) REFERENCES [User](user_id),
    FOREIGN KEY (station_id) REFERENCES RentalStation(station_id),
    FOREIGN KEY (handled_by) REFERENCES [User](user_id)
);

-- ======================================
-- FEEDBACK & PHOTO
-- ======================================
CREATE TABLE Feedback (
    feedback_id INT PRIMARY KEY IDENTITY(1,1),
    order_id INT,
    rating INT CHECK (rating BETWEEN 1 AND 5),
    comment NVARCHAR(255),
    created_at DATETIME DEFAULT GETDATE(),
    FOREIGN KEY (order_id) REFERENCES RentalOrder(order_id)
);

CREATE TABLE Photo (
    photo_id INT PRIMARY KEY IDENTITY(1,1),
    entity_type NVARCHAR(50), -- vehicle, user, order, damage_report, contract (scan/upload)
    entity_id INT,
    url NVARCHAR(255),
    uploaded_at DATETIME DEFAULT GETDATE()
);

-- ======================================
-- ADDRESS
-- ======================================
INSERT INTO Address (city, district, ward, street) VALUES
('Hanoi','Cau Giay','Dich Vong','123 Nguyen Phong Sac'),
('Hanoi','Ba Dinh','Nguyen Trung','456 Kim Ma'),
('HCM','District 1','Ben Nghe','789 Le Loi'),
('HCM','District 3','Ward 10','101 Pasteur'),
('Danang','Hai Chau','Ward 5','102 Bach Dang'),
('Hue','Hue City','Ward 2','103 Le Loi'),
('Can Tho','Ninh Kieu','Ward 4','104 Nguyen Trai'),
('Hai Phong','Le Chan','Ward 7','105 Tran Phu'),
('Vung Tau','Thang Tam','Ward 1','106 Le Hong Phong'),
('Binh Duong','Thu Dau Mot','Ward 3','107 Tran Hung Dao');

-- ======================================
-- RENTAL STATION
-- ======================================
INSERT INTO RentalStation (name, address_id, active) VALUES
('Hanoi EV Station 1',1,1),
('Hanoi EV Station 2',2,1),
('HCM EV Station 1',3,1),
('HCM EV Station 2',4,1),
('Danang EV Station',5,1),
('Hue EV Station',6,1),
('Can Tho EV Station',7,1),
('Hai Phong EV Station',8,1),
('Vung Tau EV Station',9,1),
('Binh Duong EV Station',10,1);

-- ======================================
-- USER
-- ======================================
INSERT INTO [User] (full_name,password,phone,role,kyc_status) VALUES
('Nguyen Van A','pass1','0987654321','customer','verified'),
('Tran Thi B','pass2','0912345678','customer','pending'),
('Le Van C','pass3','0901234567','customer','verified'),
('Pham Thi D','pass4','0976543210','customer','verified'),
('Hoang Van E','pass5','0932123456','customer','pending'),
('Nguyen Thi F','pass6','0923456789','staff','verified'),
('Tran Van G','pass7','0965432109','staff','verified'),
('Le Thi H','pass8','0919876543','staff','pending'),
('Pham Van I','pass9','0987123456','staff','verified'),
('Hoang Thi J','pass10','0908765432','staff','verified');

-- ======================================
-- CUSTOMER
-- ======================================
INSERT INTO Customer (customer_id,email,license_number,cccd_number,cccd_front,cccd_back,license_photo) VALUES
(SCOPE_IDENTITY(),'a1@gmail.com','AB123','123456789','url_cccdf1','url_cccdb1','url_license1'),
(SCOPE_IDENTITY(),'a2@gmail.com','AB124','223456789','url_cccdf2','url_cccdb2','url_license2'),
(SCOPE_IDENTITY(),'a3@gmail.com','AB125','323456789','url_cccdf3','url_cccdb3','url_license3'),
(SCOPE_IDENTITY(),'a4@gmail.com','AB126','423456789','url_cccdf4','url_cccdb4','url_license4'),
(SCOPE_IDENTITY(),'a5@gmail.com','AB127','523456789','url_cccdf5','url_cccdb5','url_license5'),
(SCOPE_IDENTITY(),'a6@gmail.com','AB128','623456789','url_cccdf6','url_cccdb6','url_license6'),
(SCOPE_IDENTITY(),'a7@gmail.com','AB129','723456789','url_cccdf7','url_cccdb7','url_license7'),
(SCOPE_IDENTITY(),'a8@gmail.com','AB130','823456789','url_cccdf8','url_cccdb8','url_license8'),
(SCOPE_IDENTITY(),'a9@gmail.com','AB131','923456789','url_cccdf9','url_cccdb9','url_license9'),
(SCOPE_IDENTITY(),'a10@gmail.com','AB132','1023456789','url_cccdf10','url_cccdb10','url_license10');

-- ======================================
-- STAFF
-- ======================================
INSERT INTO Staff (email, position, station_id) VALUES
('f1@gmail.com','Manager',1),
('g1@gmail.com','Technician',2),
('h1@gmail.com','Manager',3),
('i1@gmail.com','Technician',4),
('j1@gmail.com','Staff',5),
('f2@gmail.com','Staff',6),
('g2@gmail.com','Manager',7),
('h2@gmail.com','Technician',8),
('i2@gmail.com','Staff',9),
('j2@gmail.com','Manager',10);

-- ======================================
-- VEHICLE
-- ======================================
INSERT INTO Vehicle (station_id,plate_number,status) VALUES
(1,'30A-11111','available'),
(2,'30A-11112','rented'),
(3,'30A-11113','maintenance'),
(4,'30A-11114','broken'),
(5,'30A-11115','available'),
(6,'30A-11116','available'),
(7,'30A-11117','rented'),
(8,'30A-11118','maintenance'),
(9,'30A-11119','broken'),
(10,'30A-11120','available');

-- ======================================
-- VEHICLE ATTRIBUTE
-- ======================================
INSERT INTO VehicleAttribute (vehicle_id,attr_name,value) VALUES
(1,'color','white'),(1,'battery','75kWh'),
(2,'color','black'),(2,'battery','60kWh'),
(3,'color','blue'),(3,'battery','70kWh'),
(4,'color','red'),(4,'battery','65kWh'),
(5,'color','green'),(5,'battery','80kWh'),
(6,'color','silver'),(6,'battery','90kWh'),
(7,'color','yellow'),(7,'battery','55kWh'),
(8,'color','gray'),(8,'battery','60kWh'),
(9,'color','pink'),(9,'battery','50kWh'),
(10,'color','orange'),(10,'battery','85kWh');

-- ======================================
-- PRICING RULE
-- ======================================
INSERT INTO PricingRule (vehicle_id,rule_type,unit_limit,price) VALUES
(1,'hourly',1,10.0),(1,'distance',10,50.0),
(2,'hourly',1,12.0),(2,'distance',10,55.0),
(3,'hourly',1,11.0),(3,'distance',10,53.0),
(4,'hourly',1,9.0),(4,'distance',10,45.0),
(5,'hourly',1,15.0),(5,'distance',10,60.0),
(6,'hourly',1,10.5),(6,'distance',10,50.5),
(7,'hourly',1,13.0),(7,'distance',10,57.0),
(8,'hourly',1,14.0),(8,'distance',10,58.0),
(9,'hourly',1,12.5),(9,'distance',10,55.5),
(10,'hourly',1,16.0),(10,'distance',10,65.0);

-- ======================================
-- RENTAL ORDER
-- ======================================
INSERT INTO RentalOrder (customer_id,vehicle_id,start_time,end_time,total_price,status) VALUES
(1,1,'2025-09-20 09:00','2025-09-20 12:00',30.0,'pending'),
(2,2,'2025-09-20 10:00','2025-09-20 14:00',40.0,'active'),
(3,3,'2025-09-21 09:00','2025-09-21 11:00',25.0,'completed'),
(4,4,'2025-09-21 10:00','2025-09-21 13:00',35.0,'cancelled'),
(5,5,'2025-09-22 08:00','2025-09-22 12:00',50.0,'pending'),
(6,6,'2025-09-22 09:00','2025-09-22 11:00',20.0,'active'),
(7,7,'2025-09-23 08:30','2025-09-23 10:30',22.0,'completed'),
(8,8,'2025-09-23 09:00','2025-09-23 12:00',33.0,'pending'),
(9,9,'2025-09-24 10:00','2025-09-24 12:30',30.0,'active'),
(10,10,'2025-09-24 11:00','2025-09-24 14:00',45.0,'completed');

-- ======================================
-- PAYMENT
-- ======================================
INSERT INTO Payment (order_id,amount,method,status) VALUES
(1,30.0,'card','paid'),
(2,40.0,'cash','paid'),
(3,25.0,'momo','paid'),
(4,35.0,'card','failed'),
(5,50.0,'cash','pending'),
(6,20.0,'momo','paid'),
(7,22.0,'card','paid'),
(8,33.0,'cash','pending'),
(9,30.0,'momo','paid'),
(10,45.0,'card','paid');

-- ======================================
-- PENALTY
-- ======================================
INSERT INTO Penalty (order_id,name,description,fee,note) VALUES
(1,'Late Return','Returned 1 hour late',5.0,'Customer accepted'),
(2,'Late Return','Returned 30 minutes late',3.0,'Customer accepted'),
(3,'Damage Fee','Scratch on door',10.0,'Customer accepted'),
(4,'Late Return','Returned 2 hours late',8.0,'Customer refused'),
(5,'Damage Fee','Broken mirror',12.0,'Customer accepted'),
(6,'Late Return','Returned 1 hour late',5.0,'Customer accepted'),
(7,'Damage Fee','Scratch on bumper',7.0,'Customer accepted'),
(8,'Late Return','Returned 45 minutes late',4.0,'Customer accepted'),
(9,'Damage Fee','Broken light',6.0,'Customer accepted'),
(10,'Late Return','Returned 30 minutes late',3.0,'Customer accepted');

-- ======================================
-- DAMAGE REPORT
-- ======================================
INSERT INTO DamageReport (vehicle_id,reporter_id,station_id,description,status,handled_by) VALUES
(1,1,1,'Scratch on bumper','pending',NULL),
(2,2,2,'Broken mirror','in_progress',NULL),
(3,3,3,'Cracked window','resolved',6),
(4,4,4,'Flat tire','pending',NULL),
(5,5,5,'Dented door','in_progress',7),
(6,6,6,'Scratched paint','resolved',8),
(7,7,7,'Broken tail light','pending',NULL),
(8,8,8,'Damage on hood','in_progress',9),
(9,9,9,'Windshield crack','resolved',10),
(10,10,10,'Interior damage','pending',NULL);

-- ======================================
-- FEEDBACK
-- ======================================
INSERT INTO Feedback (order_id,rating,comment) VALUES
(1,5,'Excellent service'),
(2,4,'Good experience'),
(3,3,'Average'),
(4,2,'Not satisfied'),
(5,5,'Very good'),
(6,4,'Satisfied'),
(7,5,'Excellent'),
(8,3,'Okay'),
(9,4,'Good'),
(10,5,'Perfect');

-- ======================================
-- PHOTO
-- ======================================
INSERT INTO Photo (entity_type,entity_id,url) VALUES
('vehicle',1,'url_vehicle1.jpg'),
('vehicle',2,'url_vehicle2.jpg'),
('vehicle',3,'url_vehicle3.jpg'),
('vehicle',4,'url_vehicle4.jpg'),
('vehicle',5,'url_vehicle5.jpg'),
('order',1,'url_order1.jpg'),
('order',2,'url_order2.jpg'),
('order',3,'url_order3.jpg'),
('damage_report',1,'url_damage1.jpg'),
('damage_report',2,'url_damage2.jpg');