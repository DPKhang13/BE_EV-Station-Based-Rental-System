------------------------------------------------------------
-- 1. Drop + Create Database
------------------------------------------------------------
IF DB_ID('Rental_Car') IS NOT NULL
    DROP DATABASE Rental_Car;
GO
CREATE DATABASE Rental_Car;
GO
USE Rental_Car;
GO

------------------------------------------------------------
-- 2. Create Tables
------------------------------------------------------------

-- RentalStation (INT)
CREATE TABLE RentalStation (
    station_id INT IDENTITY(1,1) PRIMARY KEY,
    name NVARCHAR(100) NOT NULL,
    city NVARCHAR(100),
    district NVARCHAR(100),
    ward NVARCHAR(100),
    street NVARCHAR(100)
);

-- User (UUID)
CREATE TABLE [User] (
    user_id UNIQUEIDENTIFIER DEFAULT NEWID() PRIMARY KEY,
    full_name NVARCHAR(100) NOT NULL,
    password NVARCHAR(100) NOT NULL,
    phone NVARCHAR(20),
    email NVARCHAR(100) UNIQUE NOT NULL,
    status NVARCHAR(50) NOT NULL,  -- ACTIVE | NEED_OTP | VERIFIED
    role NVARCHAR(50) NOT NULL,    -- customer | staff | admin
    station_id INT NULL,
    FOREIGN KEY (station_id) REFERENCES RentalStation(station_id)
);

-- Vehicle (UUID)
CREATE TABLE Vehicle (
    vehicle_id UNIQUEIDENTIFIER DEFAULT NEWID() PRIMARY KEY,
    station_id INT,
    plate_number NVARCHAR(20) UNIQUE,
    status NVARCHAR(50),
    seat_count INT,
    variant NVARCHAR(50),
    FOREIGN KEY (station_id) REFERENCES RentalStation(station_id)
);

-- VehicleAttribute (INT)
CREATE TABLE VehicleAttribute (
    attr_id INT IDENTITY(1,1) PRIMARY KEY,
    vehicle_id UNIQUEIDENTIFIER,
    attr_name NVARCHAR(50),
    attr_value NVARCHAR(100),
    FOREIGN KEY (vehicle_id) REFERENCES Vehicle(vehicle_id)
);

-- PricingRule (INT)
CREATE TABLE PricingRule (
    pricing_rule_id INT IDENTITY(1,1) PRIMARY KEY,
    vehicle_id UNIQUEIDENTIFIER,
    base_hours INT,
    base_hours_price DECIMAL(18,2),
    extra_hour_price DECIMAL(18,2),
    daily_price DECIMAL(18,2),
    FOREIGN KEY (vehicle_id) REFERENCES Vehicle(vehicle_id)
);

-- RentalOrder (UUID)
CREATE TABLE RentalOrder (
    order_id UNIQUEIDENTIFIER DEFAULT NEWID() PRIMARY KEY,
    customer_id UNIQUEIDENTIFIER,
    vehicle_id UNIQUEIDENTIFIER,
    start_time DATETIME,
    end_time DATETIME,
    total_price DECIMAL(18,2),
    status NVARCHAR(50),
    FOREIGN KEY (customer_id) REFERENCES [User](user_id),
    FOREIGN KEY (vehicle_id) REFERENCES Vehicle(vehicle_id)
);

-- Payment (UUID)
CREATE TABLE Payment (
    payment_id UNIQUEIDENTIFIER DEFAULT NEWID() PRIMARY KEY,
    order_id UNIQUEIDENTIFIER,
    amount DECIMAL(18,2),
    method NVARCHAR(50),
    status NVARCHAR(50),
    FOREIGN KEY (order_id) REFERENCES RentalOrder(order_id)
);

-- Feedback (INT)
CREATE TABLE Feedback (
    feedback_id INT IDENTITY(1,1) PRIMARY KEY,
    order_id UNIQUEIDENTIFIER,
    rating INT,
    comment NVARCHAR(255),
    FOREIGN KEY (order_id) REFERENCES RentalOrder(order_id)
);

-- Maintenance (INT)
CREATE TABLE Maintenance (
    maintenance_id INT IDENTITY(1,1) PRIMARY KEY,
    vehicle_id UNIQUEIDENTIFIER,
    description NVARCHAR(255),
    date DATE,
    cost DECIMAL(18,2),
    FOREIGN KEY (vehicle_id) REFERENCES Vehicle(vehicle_id)
);

-- StationInventory (INT)
CREATE TABLE StationInventory (
    inventory_id INT IDENTITY(1,1) PRIMARY KEY,
    station_id INT,
    vehicle_id UNIQUEIDENTIFIER,
    quantity INT,
    FOREIGN KEY (station_id) REFERENCES RentalStation(station_id),
    FOREIGN KEY (vehicle_id) REFERENCES Vehicle(vehicle_id)
);

-- EmployeeSchedule (INT)
CREATE TABLE EmployeeSchedule (
    schedule_id INT IDENTITY(1,1) PRIMARY KEY,
    staff_id UNIQUEIDENTIFIER,
    station_id INT,
    shift_date DATE,
    shift_time NVARCHAR(50),
    FOREIGN KEY (staff_id) REFERENCES [User](user_id),
    FOREIGN KEY (station_id) REFERENCES RentalStation(station_id)
);

-- Notification (INT)
CREATE TABLE Notification (
    notification_id INT IDENTITY(1,1) PRIMARY KEY,
    user_id UNIQUEIDENTIFIER,
    message NVARCHAR(255),
    created_at DATETIME,
    FOREIGN KEY (user_id) REFERENCES [User](user_id)
);

-- TransactionHistory (UUID)
CREATE TABLE TransactionHistory (
    transaction_id UNIQUEIDENTIFIER DEFAULT NEWID() PRIMARY KEY,
    user_id UNIQUEIDENTIFIER,
    amount DECIMAL(18,2),
    type NVARCHAR(50),
    created_at DATETIME,
    FOREIGN KEY (user_id) REFERENCES [User](user_id)
);

-- Coupon (INT)
CREATE TABLE Coupon (
    coupon_id INT IDENTITY(1,1) PRIMARY KEY,
    code NVARCHAR(50) UNIQUE,
    discount DECIMAL(18,2),
    valid_from DATE,
    valid_to DATE,
    status NVARCHAR(50)
);

------------------------------------------------------------
-- 3. Insert Seed Data
------------------------------------------------------------

-- RentalStation
INSERT INTO RentalStation (name, city, district, ward, street) VALUES
(N'Hanoi EV Station 1','Hanoi','Cau Giay','Dich Vong','123 Nguyen Phong Sac'),
(N'HCM EV Station 1','HCM','District 1','Ben Nghe','789 Le Loi');

-- User (3 user mẫu: customer, staff, admin)
DECLARE @station1 INT = 1, @station2 INT = 2;
DECLARE @user1 UNIQUEIDENTIFIER = NEWID(), 
        @user2 UNIQUEIDENTIFIER = NEWID(), 
        @user3 UNIQUEIDENTIFIER = NEWID();

INSERT INTO [User](user_id,full_name,password,phone,email,status,role,station_id) VALUES
(@user1,N'Nguyen Van A','pass1','0987654321','fptngulonrietquen@gmail.com','ACTIVE','customer',null),
(@user2,N'Tran Thi B','pass2','0912345678','testswp39111@gmail.com','VERIFIED','staff',@station2),
(@user3,N'Le Van Admin','adminpass','0900000000','admin@gmail.com','VERIFIED','admin',NULL);

-- Vehicle
DECLARE @vehicle1 UNIQUEIDENTIFIER = NEWID(), 
        @vehicle2 UNIQUEIDENTIFIER = NEWID();

INSERT INTO Vehicle(vehicle_id,station_id,plate_number,status,seat_count,variant) VALUES
(@vehicle1,@station1,'30A-11111','available',4,'standard'),
(@vehicle2,@station2,'30A-11112','rented',7,'plus');

-- PricingRule
INSERT INTO PricingRule (vehicle_id,base_hours,base_hours_price,extra_hour_price,daily_price) VALUES
(@vehicle1,3,200000,80000,1000000),
(@vehicle2,3,250000,100000,1200000);

-- RentalOrder
DECLARE @order1 UNIQUEIDENTIFIER = NEWID(), 
        @order2 UNIQUEIDENTIFIER = NEWID();

INSERT INTO RentalOrder(order_id,customer_id,vehicle_id,start_time,end_time,total_price,status) VALUES
(@order1,@user1,@vehicle1,'2025-09-20 09:00','2025-09-20 12:00',300000,'pending'),
(@order2,@user1,@vehicle2,'2025-09-21 09:00','2025-09-21 13:00',500000,'active');

-- Payment
INSERT INTO Payment(order_id,amount,method,status) VALUES
(@order1,300000,'card','paid'),
(@order2,500000,'cash','pending');

-- Feedback
INSERT INTO Feedback(order_id,rating,comment) VALUES
(@order1,5,N'Excellent service'),
(@order2,4,N'Good car');

-- Maintenance
INSERT INTO Maintenance(vehicle_id,description,date,cost) VALUES
(@vehicle1,N'Engine check','2025-09-10',500000);

-- StationInventory
INSERT INTO StationInventory(station_id,vehicle_id,quantity) VALUES
(@station1,@vehicle1,5),
(@station2,@vehicle2,3);

-- EmployeeSchedule
INSERT INTO EmployeeSchedule(staff_id,station_id,shift_date,shift_time) VALUES
(@user2,@station2,'2025-09-21','Morning');

-- Notification
INSERT INTO Notification(user_id,message,created_at) VALUES
(@user1,N'Your booking is confirmed','2025-09-20 08:00');

-- TransactionHistory
INSERT INTO TransactionHistory(user_id,amount,type,created_at) VALUES
(@user1,300000,'payment','2025-09-20 09:30');

-- Coupon
INSERT INTO Coupon(code,discount,valid_from,valid_to,status) VALUES
('SALE50',50000,'2025-09-01','2025-12-31','active');
GO
