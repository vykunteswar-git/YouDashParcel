package com.youdash.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeightUnitConverterTest {

    @Test
    void gramsConvertToKgForPricing() {
        WeightUnitConverter.ResolvedWeight r = WeightUnitConverter.resolve(500.0, "Grams (g)");
        assertEquals(0.5, r.kg(), 0.0001);
        assertEquals(WeightUnitConverter.Unit.G, r.unit());
    }

    @Test
    void kilogramsStayAsKg() {
        WeightUnitConverter.ResolvedWeight r = WeightUnitConverter.resolve(500.0, "Kilograms (kg)");
        assertEquals(500.0, r.kg(), 0.0001);
        assertEquals(WeightUnitConverter.Unit.KG, r.unit());
    }

    @Test
    void shortCodesWork() {
        assertEquals(0.5, WeightUnitConverter.resolve(500.0, "G").kg(), 0.0001);
        assertEquals(10.0, WeightUnitConverter.resolve(10.0, "KG").kg(), 0.0001);
    }
}
