package com.youdash.entity;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;

import jakarta.persistence.*;
import lombok.Data;

import com.youdash.model.RiderApprovalStatus;

@Entity
@Table(name = "youdash_riders")
@Data
public class RiderEntity {

    private static final SecureRandom PUBLIC_ID_RANDOM = new SecureRandom();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "phone")
    private String phone;

    @Column(name = "email", length = 255, unique = true)
    private String email;

    @Column(name = "public_id", unique = true, length = 32)
    private String publicId;

    @Column(name = "vehicle_id")
    private Long vehicleId;

    @Column(name = "vehicle_type")
    private String vehicleType;

    @Column(name = "vehicle_number", length = 32)
    private String vehicleNumber;

    @Column(name = "emergency_phone")
    private String emergencyPhone;

    @Column(name = "is_available")
    private Boolean isAvailable;

    @Column(name = "is_blocked")
    private Boolean isBlocked;

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
        if (this.isBlocked == null) {
            this.isBlocked = false;
        }
        if (this.publicId == null || this.publicId.isBlank()) {
            this.publicId = generatePublicId();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    private static String generatePublicId() {
        long n = Math.abs(PUBLIC_ID_RANDOM.nextLong());
        String suffix = Long.toString(n, 36).toLowerCase(Locale.ROOT);
        if (suffix.length() > 10) {
            suffix = suffix.substring(0, 10);
        } else if (suffix.length() < 10) {
            suffix = "0".repeat(10 - suffix.length()) + suffix;
        }
        return "rd-" + suffix;
    }
}
