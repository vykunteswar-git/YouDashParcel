package com.youdash.model.wallet;

import java.util.Locale;

public enum CodCollectionMode {
    CASH,
    QR;

    /** Accepts CASH, QR, UPI, UPI_QR from rider app (UPI → QR). */
    public static CodCollectionMode parseClientValue(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("mode is required (CASH or QR)");
        }
        String v = raw.trim().toUpperCase(Locale.ROOT);
        if ("UPI".equals(v) || "UPI_QR".equals(v) || "UPI/QR".equals(v)) {
            return QR;
        }
        return CodCollectionMode.valueOf(v);
    }
}
