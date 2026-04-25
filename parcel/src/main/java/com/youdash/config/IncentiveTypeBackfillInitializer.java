package com.youdash.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class IncentiveTypeBackfillInitializer {

    private final JdbcTemplate jdbcTemplate;

    public IncentiveTypeBackfillInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void backfillLegacyIncentiveTypes() {
        int updatedLegacy = jdbcTemplate.update(
                "UPDATE youdash_peak_incentive_campaigns " +
                        "SET incentive_type = ? " +
                        "WHERE incentive_type IN (?, ?)",
                "DAILY_DELIVERIES_SLOT",
                "BONUS",
                "DAILY"
        );

        int updatedHours = jdbcTemplate.update(
                "UPDATE youdash_peak_incentive_campaigns " +
                        "SET incentive_type = ? " +
                        "WHERE COALESCE(target_online_minutes, 0) > 0 " +
                        "AND incentive_type <> ?",
                "ONLINE_HOURS_DAILY",
                "ONLINE_HOURS_DAILY"
        );

        if (updatedLegacy > 0) {
            log.info("Backfilled {} legacy incentive_type rows to DAILY_DELIVERIES_SLOT", updatedLegacy);
        }
        if (updatedHours > 0) {
            log.info("Backfilled {} campaigns to ONLINE_HOURS_DAILY using target_online_minutes", updatedHours);
        }
    }
}
