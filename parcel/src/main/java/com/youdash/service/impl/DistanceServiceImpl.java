package com.youdash.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youdash.service.DistanceService;
import com.youdash.util.GeoUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
public class DistanceServiceImpl implements DistanceService {

    @Value("${youdash.maps.distance.api-key:}")
    private String apiKey;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public double distanceKm(double originLat, double originLng, double destLat, double destLng) {
        if (apiKey == null || apiKey.isBlank()) {
            return GeoUtils.haversineKm(originLat, originLng, destLat, destLng);
        }
        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl("https://maps.googleapis.com/maps/api/distancematrix/json")
                    .queryParam("origins", originLat + "," + originLng)
                    .queryParam("destinations", destLat + "," + destLng)
                    .queryParam("mode", "driving")
                    .queryParam("key", apiKey)
                    .build()
                    .toUriString();
            String body = restTemplate.getForObject(url, String.class);
            if (body == null) {
                return GeoUtils.haversineKm(originLat, originLng, destLat, destLng);
            }
            JsonNode root = objectMapper.readTree(body);
            String status = root.path("status").asText();
            if (!"OK".equals(status)) {
                log.warn("Distance Matrix status={} — falling back to haversine", status);
                return GeoUtils.haversineKm(originLat, originLng, destLat, destLng);
            }
            JsonNode el = root.path("rows").path(0).path("elements").path(0);
            if (!"OK".equals(el.path("status").asText())) {
                log.warn("Distance Matrix element status={} — falling back to haversine", el.path("status").asText());
                return GeoUtils.haversineKm(originLat, originLng, destLat, destLng);
            }
            int meters = el.path("distance").path("value").asInt();
            return meters / 1000.0;
        } catch (Exception e) {
            log.warn("Distance Matrix failed: {} — using haversine", e.getMessage());
            return GeoUtils.haversineKm(originLat, originLng, destLat, destLng);
        }
    }
}
