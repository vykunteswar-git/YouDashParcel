package com.youdash.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Data;

import com.youdash.model.RiderApprovalStatus;

@Entity
@Table(name = "youdash_riders")
@Data
public class RiderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "phone")
    private String phone;

    @Column(name = "vehicle_type")
    private String vehicleType;

    @Column(name = "is_available")
    private Boolean isAvailable;

    @Column(name = "current_lat")
    private Double currentLat;

    @Column(name = "current_lng")
    private Double currentLng;

    @Column(name = "rating")
    private Double rating;

    @Column(name = "license_image_url")
    private String licenseImageUrl;

    @Column(name = "rc_image_url")
    private String rcImageUrl;

    @Column(name = "aadhaar_image_url")
    private String aadhaarImageUrl;

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "fcm_token")
    private String fcmToken;

    @Column(name = "approval_status", length = 24)
    private String approvalStatus;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.approvalStatus == null) {
            this.approvalStatus = RiderApprovalStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
