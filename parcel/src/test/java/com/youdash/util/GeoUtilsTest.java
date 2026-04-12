package com.youdash.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GeoUtilsTest {

    @Test
    void haversineKm_samePoint_isZero() {
        assertEquals(0.0, GeoUtils.haversineKm(17.7, 83.3, 17.7, 83.3), 1e-9);
    }

    @Test
    void isInsideCircle_centerInsideRadius() {
        assertTrue(GeoUtils.isInsideCircle(17.7, 83.3, 17.7, 83.3, 1.0));
        assertTrue(GeoUtils.isInsideCircle(17.701, 83.3, 17.7, 83.3, 5.0));
    }

    @Test
    void isInsidePolygon_square_containsCenter() {
        List<double[]> square = List.of(
                new double[]{17.0, 83.0},
                new double[]{17.0, 84.0},
                new double[]{18.0, 84.0},
                new double[]{18.0, 83.0}
        );
        assertTrue(GeoUtils.isInsidePolygon(17.5, 83.5, square));
        assertFalse(GeoUtils.isInsidePolygon(16.5, 83.5, square));
    }
}
