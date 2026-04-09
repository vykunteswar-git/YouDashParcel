package com.youdash.config;

import com.youdash.entity.AdminEntity;
import com.youdash.repository.AdminRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AdminInitializer {

    private static final String DEFAULT_ADMIN_EMAIL = "youdashParcel@gmail.com";
    private static final String DEFAULT_ADMIN_PASSWORD = "youdash123";

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @PostConstruct
    public void init() {
        if (adminRepository.findByEmail(DEFAULT_ADMIN_EMAIL).isPresent()) {
            return;
        }

        AdminEntity admin = new AdminEntity();
        admin.setEmail(DEFAULT_ADMIN_EMAIL);
        admin.setPassword(passwordEncoder.encode(DEFAULT_ADMIN_PASSWORD));
        admin.setIsActive(true);
        adminRepository.save(admin);
        log.info("Default admin created");
    }
}
