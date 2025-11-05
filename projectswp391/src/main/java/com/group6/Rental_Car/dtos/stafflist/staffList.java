
package com.group6.Rental_Car.dtos.stafflist;

import java.util.UUID;

public interface staffList {
    UUID getStaffId();
    String getStaffName();
    String getStaffEmail();
    String getRole();
    String getStationName();
    Long getPickupCount();
    Long getReturnCount();
    String getStatus();
}
