package com.youdash.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youdash.service.DistanceService;
import com.youdash.service.impl.GoogleMapsDistanceService;
import com.youdash.service.impl.HaversineDistanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Exposes {@link DistanceService}: Google Distance Matrix (driving) when {@code youdash.maps.distance.api-key}
 * is set; otherwise great-circle (haversine) fallback.
 */
@Configuration
public class DistanceConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DistanceConfiguration.class);

    @Bean
    public DistanceService distanceService(
            @Value("${youdash.maps.distance.api-key:}") String apiKey,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper) {
        if (apiKey != null && !apiKey.isBlank()) {
            log.info("DistanceService: using Google Distance Matrix API (mode=driving)");
            RestClient client = GoogleMapsDistanceService.buildRestClient(restClientBuilder);
            return new GoogleMapsDistanceService(client, objectMapper, apiKey.trim());
        }
        log.info("DistanceService: using haversine (set youdash.maps.distance.api-key for road distance)");
        return new HaversineDistanceService();
    }
}
