package com.youdash.config;

import com.youdash.entity.AppConfigEntity;
import com.youdash.entity.PackageCategoryEntity;
import com.youdash.repository.AppConfigRepository;
import com.youdash.repository.PackageCategoryRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AppDataInitializer {

    @Autowired
    private AppConfigRepository appConfigRepository;

    @Autowired
    private PackageCategoryRepository packageCategoryRepository;

    @PostConstruct
    public void seed() {
        if (appConfigRepository.findById(1L).isEmpty()) {
            AppConfigEntity c = new AppConfigEntity();
            c.setId(1L);
            c.setGstPercent(18.0);
            c.setPlatformFee(15.0);
            c.setPickupRatePerKm(12.0);
            c.setDropRatePerKm(12.0);
            c.setPerKgRate(8.0);
            c.setDefaultRouteRatePerKm(10.0);
            appConfigRepository.save(c);
        }
        if (packageCategoryRepository.count() == 0) {
            seedCategory("Documents", "📄", 1);
            seedCategory("Electronics", "💻", 2);
            seedCategory("Clothing", "👕", 3);
            seedCategory("Food", "🍱", 4);
            seedCategory("Other", "📦", 5);
        }
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
