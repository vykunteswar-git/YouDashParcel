package com.youdash.util;

/**
 * Admin/customer weight input may be in kilograms or grams; pricing always uses kg.
 */
public final class WeightUnitConverter {

    public enum Unit {
        KG,
        G;

        public static Unit parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return KG;
            }
            String n = raw.trim().toUpperCase();
            if ("G".equals(n) || "GRAM".equals(n) || "GRAMS".equals(n) || "GM".equals(n)) {
                return G;
            }
            return KG;
        }
    }

    public record ResolvedWeight(double kg, Unit unit) {}

    private WeightUnitConverter() {}

    public static ResolvedWeight resolve(Double rawWeight, String rawUnit) {
        if (rawWeight == null || rawWeight <= 0) {
            throw new RuntimeException("weight must be > 0");
        }
        Unit unit = Unit.parse(rawUnit);
        double kg = unit == Unit.G ? rawWeight / 1000.0 : rawWeight;
        if (kg <= 0) {
            throw new RuntimeException("weight must be > 0");
        }
        return new ResolvedWeight(kg, unit);
    }

    /** Human-readable label for LR / admin UI, e.g. {@code 500 g} or {@code 10 kg}. */
    public static String formatDisplay(double kg, Unit unit) {
        if (unit == Unit.G) {
            return Math.round(kg * 1000.0) + " g";
        }
        if (kg == Math.floor(kg)) {
            return (long) kg + " kg";
        }
        return String.format("%.2f kg", kg);
    }
}
