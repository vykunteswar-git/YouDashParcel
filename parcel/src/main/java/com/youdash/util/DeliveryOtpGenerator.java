package com.youdash.util;

import java.security.SecureRandom;

/** 6-digit numeric string (fits {@code VARCHAR(6)} on orders). */
public final class DeliveryOtpGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private DeliveryOtpGenerator() {}

    public static String generate() {
        int n = 100000 + RANDOM.nextInt(900000);
        return String.valueOf(n);
    }
}
