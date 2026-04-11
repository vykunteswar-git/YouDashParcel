package com.youdash.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youdash.service.DistanceService;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;

/**
 * Driving distance via Google Distance Matrix API ({@code mode=driving}, metric).
 */
public class GoogleMapsDistanceService implements DistanceService {

    private static final String MATRIX_URL = "https://maps.googleapis.com/maps/api/distancematrix/json";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public GoogleMapsDistanceService(RestClient restClient, ObjectMapper objectMapper, String apiKey) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    /**
     * Builds a {@link RestClient} with connect/read timeouts suitable for the Distance Matrix API.
     */
    public static RestClient buildRestClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(20));
        return builder.requestFactory(factory).build();
    }

    @Override
    public double calculateDistanceKm(double pickupLat, double pickupLng, double deliveryLat, double deliveryLng) {
        String origins = pickupLat + "," + pickupLng;
        String destinations = deliveryLat + "," + deliveryLng;
        URI uri = UriComponentsBuilder.fromUriString(MATRIX_URL)
                .queryParam("origins", origins)
                .queryParam("destinations", destinations)
                .queryParam("mode", "driving")
                .queryParam("units", "metric")
                .queryParam("key", apiKey)
                .build()
                .toUri();

        String body = restClient.get()
                .uri(uri)
                .retrieve()
                .body(String.class);

        if (body == null || body.isBlank()) {
            throw new RuntimeException("Google Distance Matrix returned an empty response");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse Google Distance Matrix JSON", e);
        }

        String status = root.path("status").asText();
        if (!"OK".equals(status)) {
            String err = root.path("error_message").asText("");
            throw new RuntimeException("Google Distance Matrix API error: " + status
                    + (err.isEmpty() ? "" : (" — " + err)));
        }

        JsonNode element = root.path("rows").path(0).path("elements").path(0);
        String elStatus = element.path("status").asText();
        if (!"OK".equals(elStatus)) {
            throw new RuntimeException(
                    "No driving route between pickup and drop (element status: " + elStatus + ")");
        }

        long meters = element.path("distance").path("value").asLong();
        return meters / 1000.0;
    }
}
