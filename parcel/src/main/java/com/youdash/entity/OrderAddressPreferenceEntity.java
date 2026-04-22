package com.youdash.entity;

import com.youdash.model.OrderAddressRole;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(
        name = "youdash_order_address_preferences",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_order_addr_pref_user_role_coord",
                        columnNames = {"user_id", "role", "coordinate_key"})
        },
        indexes = {
                @Index(name = "idx_order_addr_pref_user", columnList = "user_id")
        })
@Data
public class OrderAddressPreferenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private OrderAddressRole role;

    @Column(name = "coordinate_key", nullable = false, length = 64)
    private String coordinateKey;

    @Column(name = "lat", nullable = false)
    private Double lat;

    @Column(name = "lng", nullable = false)
    private Double lng;

    @Column(name = "address", length = 512)
    private String address;

    @Column(name = "tag", length = 32)
    private String tag;

    @Column(name = "door_no", length = 64)
    private String doorNo;

    @Column(name = "landmark", length = 255)
    private String landmark;

    @Column(name = "contact_name", length = 128)
    private String contactName;

    @Column(name = "contact_phone", length = 32)
    private String contactPhone;

    @Column(name = "is_hidden", nullable = false)
    private Boolean isHidden;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (isHidden == null) {
            isHidden = false;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
