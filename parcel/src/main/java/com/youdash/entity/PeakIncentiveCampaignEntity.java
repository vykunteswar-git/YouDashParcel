package com.youdash.entity;

import com.youdash.model.ServiceMode;
import com.youdash.model.IncentiveType;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "youdash_peak_incentive_campaigns")
@Data
public class PeakIncentiveCampaignEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "incentive_type", nullable = false, length = 32)
    private IncentiveType incentiveType;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "description", length = 512)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_mode", length = 16)
    private ServiceMode serviceMode;

    @Column(name = "incentive_date")
    private LocalDate incentiveDate;

    @Column(name = "target_online_minutes")
    private Integer targetOnlineMinutes;

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

    @Lob
    @Column(name = "slabs_json", columnDefinition = "TEXT")
    private String slabsJson;

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
        if (incentiveType == null) {
            incentiveType = IncentiveType.DAILY_DELIVERIES_SLOT;
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
