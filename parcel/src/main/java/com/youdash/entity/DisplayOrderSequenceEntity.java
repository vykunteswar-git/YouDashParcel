package com.youdash.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * Single-row counter for short public order references ({@code YP-1000}, {@code YP-1001}, …).
 */
@Entity
@Table(name = "youdash_display_order_sequence")
@Data
public class DisplayOrderSequenceEntity {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    /** Next value to assign (e.g. 1000 → {@code YP-1000}). */
    @Column(name = "next_value", nullable = false)
    private Long nextValue;
}
