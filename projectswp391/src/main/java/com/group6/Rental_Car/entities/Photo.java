package com.group6.Rental_Car.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "photo")
@Getter
@Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class Photo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "photo_id")
    private Long photoId;

    // bảng "user" là keyword, nhớ quote tên bảng trong entity User:
    // @Table(name = "\"user\"")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "photo_url", nullable = false, columnDefinition = "text")
    private String photoUrl;

    @Column(name = "type", length = 50)
    private String type;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;
}

