package com.group6.Rental_Car.entities;

import lombok.*;
import java.time.LocalDate;
import  jakarta.persistence.*;

@Entity
@Table(
        name= "employeeschedule",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_staff_date_shift",
                // chan trung ca truc
                columnNames = {"staff_id", "shift_date", "shift_time"}
        )
)

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor

@Builder(toBuilder = true)
public class EmployeeSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) //SQL server: INT IDENTITY
    @Column (name = "schedule_id")
    private Integer scheduleId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id", nullable = false)
    private User staff;

    // cột staff_id (FK) tham chiếu sang User.user_id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id", nullable = false)
    private RentalStation station;

    @Column(name = "shift_date", nullable = false)
    private LocalDate shiftDate;

    @Column(name = "shift_time", nullable = false, length = 50)
    private String shiftTime;

    @Column(name = "pickup_count", nullable = false)
    private int pickupCount;

    @Column(name = "return_count", nullable = false)
    private int returnCount;

}
