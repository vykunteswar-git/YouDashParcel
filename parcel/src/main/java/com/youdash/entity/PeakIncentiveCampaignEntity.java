package com.youdash.entity;

import com.youdash.model.ServiceMode;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "youdash_peak_incentive_campaigns")
@Data
public class PeakIncentiveCampaignEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "description", length = 512)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_mode", length = 16)
    private ServiceMode serviceMode;

    @Column(name = "bonus_amount", nullable = false)
    private Double bonusAmount;

    @Column(name = "min_completed_orders", nullable = false)
    private Integer minCompletedOrders;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_to", nullable = false)
    private Instant validTo;

    /** Comma-separated DAY_OF_WEEK names, e.g. MONDAY,TUESDAY. */
    @Column(name = "days_of_week_csv", length = 128)
    private String daysOfWeekCsv;

    /** Local time string HH:mm (24h). */
    @Column(name = "start_time_hhmm", nullable = false, length = 5)
    private String startTimeHhmm;

    /** Local time string HH:mm (24h). */
    @Column(name = "end_time_hhmm", nullable = false, length = 5)
    private String endTimeHhmm;

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
        if (isActive == null) {
            isActive = true;
        }
        if (minCompletedOrders == null || minCompletedOrders <= 0) {
            minCompletedOrders = 1;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
