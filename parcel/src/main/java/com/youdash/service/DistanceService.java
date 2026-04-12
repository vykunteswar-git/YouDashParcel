package com.youdash.service;

/**
 * Driving distance in km when Google key is configured; otherwise haversine straight-line km.
 */
public interface DistanceService {

    double distanceKm(double originLat, double originLng, double destLat, double destLng);
}
