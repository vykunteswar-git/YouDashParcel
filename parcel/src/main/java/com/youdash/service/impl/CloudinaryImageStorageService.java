package com.youdash.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youdash.service.ImageStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class CloudinaryImageStorageService implements ImageStorageService {

    @Value("${cloudinary.cloud-name:}")
    private String cloudName;

    @Value("${cloudinary.api-key:}")
    private String apiKey;

    @Value("${cloudinary.api-secret:}")
    private String apiSecret;

    @Value("${cloudinary.banner-folder:youdash/banners}")
    private String bannerFolder;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String uploadBannerImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("Banner image file is required");
        }
        if (cloudName.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) {
            throw new RuntimeException("Cloudinary configuration is missing");
        }
        try {
            long timestamp = Instant.now().getEpochSecond();
            String signature = sign(timestamp);
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", asResource(file));
            body.add("api_key", apiKey);
            body.add("timestamp", String.valueOf(timestamp));
            body.add("folder", bannerFolder);
            body.add("signature", signature);

            String endpoint = "https://api.cloudinary.com/v1_1/" + cloudName + "/image/upload";
            ResponseEntity<String> response = restTemplate.postForEntity(endpoint, body, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new RuntimeException("Failed to upload image to Cloudinary");
            }
            JsonNode json = objectMapper.readTree(response.getBody());
            String secureUrl = json.path("secure_url").asText();
            if (secureUrl == null || secureUrl.isBlank()) {
                throw new RuntimeException("Cloudinary did not return secure_url");
            }
            return secureUrl;
        } catch (Exception ex) {
            throw new RuntimeException("Cloudinary upload failed: " + ex.getMessage());
        }
    }

    private ByteArrayResource asResource(MultipartFile file) throws Exception {
        return new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                String original = file.getOriginalFilename();
                return (original == null || original.isBlank()) ? "banner-image" : original;
            }

            @Override
            public long contentLength() {
                return file.getSize();
            }
        };
    }

    private String sign(long timestamp) throws Exception {
        String payload = "folder=" + bannerFolder + "&timestamp=" + timestamp + apiSecret;
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] digest = sha1.digest(payload.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
