package com.youdash.config;

import com.youdash.entity.AppConfigEntity;
import com.youdash.entity.OutstationLegRateTierEntity;
import com.youdash.entity.PackageCategoryEntity;
import com.youdash.model.OutstationLegType;
import com.youdash.model.PaymentType;
import com.youdash.repository.AppConfigRepository;
import com.youdash.repository.OutstationLegRateTierRepository;
import com.youdash.repository.PackageCategoryRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AppDataInitializer {

    private static final double DEFAULT_PLATFORM_FEE = 15.0;
    private static final double DEFAULT_PICKUP_RATE = 12.0;
    private static final double DEFAULT_DROP_RATE = 12.0;

    @Autowired
    private AppConfigRepository appConfigRepository;

    @Autowired
    private OutstationLegRateTierRepository legRateTierRepository;

    @Autowired
    private PackageCategoryRepository packageCategoryRepository;

    @PostConstruct
    public void seed() {
        AppConfigEntity config = appConfigRepository.findById(1L).orElse(null);
        if (config == null) {
            AppConfigEntity c = new AppConfigEntity();
            c.setId(1L);
            c.setGstPercent(18.0);
            c.setPlatformFee(DEFAULT_PLATFORM_FEE);
            c.setIncityPlatformFee(DEFAULT_PLATFORM_FEE);
            c.setOutstationPlatformFee(DEFAULT_PLATFORM_FEE);
            c.setPickupRatePerKm(DEFAULT_PICKUP_RATE);
            c.setDropRatePerKm(DEFAULT_DROP_RATE);
            c.setPerKgRate(8.0);
            c.setDefaultRouteRatePerKm(10.0);
            c.setCodEnabled(true);
            c.setOnlineEnabled(true);
            c.setDefaultPaymentType(PaymentType.ONLINE);
            config = appConfigRepository.save(c);
        } else {
            migrateLegacyPlatformFees(config);
            appConfigRepository.save(config);
        }

        seedLegTiersIfEmpty(config);

        if (packageCategoryRepository.count() == 0) {
            seedCategory("Documents", "📄", 1);
            seedCategory("Electronics", "💻", 2);
            seedCategory("Clothing", "👕", 3);
            seedCategory("Food", "🍱", 4);
            seedCategory("Other", "📦", 5);
        }
    }

    private void migrateLegacyPlatformFees(AppConfigEntity config) {
        Double legacy = config.getPlatformFee();
        if (config.getIncityPlatformFee() == null && legacy != null) {
            config.setIncityPlatformFee(legacy);
        }
        if (config.getOutstationPlatformFee() == null && legacy != null) {
            config.setOutstationPlatformFee(legacy);
        }
        if (config.getIncityPlatformFee() == null) {
            config.setIncityPlatformFee(DEFAULT_PLATFORM_FEE);
        }
        if (config.getOutstationPlatformFee() == null) {
            config.setOutstationPlatformFee(DEFAULT_PLATFORM_FEE);
        }
    }

    private void seedLegTiersIfEmpty(AppConfigEntity config) {
        if (legRateTierRepository.count() > 0) {
            return;
        }
        double pickup = config.getPickupRatePerKm() != null ? config.getPickupRatePerKm() : DEFAULT_PICKUP_RATE;
        double drop = config.getDropRatePerKm() != null ? config.getDropRatePerKm() : DEFAULT_DROP_RATE;
        saveDefaultTier(OutstationLegType.PICKUP, 0.0, 9999.0, pickup, 0);
        saveDefaultTier(OutstationLegType.DROP, 0.0, 9999.0, drop, 0);
    }

    private void saveDefaultTier(
            OutstationLegType legType,
            double minKg,
            double maxKg,
            double ratePerKm,
            int sortOrder) {
        OutstationLegRateTierEntity row = new OutstationLegRateTierEntity();
        row.setLegType(legType);
        row.setMinWeightKg(minKg);
        row.setMaxWeightKg(maxKg);
        row.setRatePerKm(ratePerKm);
        row.setSortOrder(sortOrder);
        row.setIsActive(true);
        legRateTierRepository.save(row);
    }

    private void seedCategory(String name, String emoji, int order) {
        PackageCategoryEntity e = new PackageCategoryEntity();
        e.setName(name);
        e.setEmoji(emoji);
        e.setSortOrder(order);
        e.setIsActive(true);
        packageCategoryRepository.save(e);
    }
}
