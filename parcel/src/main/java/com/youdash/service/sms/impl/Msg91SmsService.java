package com.youdash.service.sms.impl;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youdash.service.sms.PhoneNumberUtil;
import com.youdash.service.sms.SmsDeliveryException;
import com.youdash.service.sms.SmsService;

@Service
public class Msg91SmsService implements SmsService {

    private static final Logger log = LoggerFactory.getLogger(Msg91SmsService.class);
    private static final String OTP_URL = "https://control.msg91.com/api/v5/otp";
    private static final long RETRY_DELAY_MS = 2000L;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${msg91.authkey:}")
    private String authkey;

    @Value("${msg91.country:91}")
    private String countryCode;

    public Msg91SmsService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void sendOtp(String mobile91, String otp) {
        if (authkey == null || authkey.isBlank()) {
            log.error("MSG91 authkey is not configured");
            throw new SmsDeliveryException(
                    SmsDeliveryException.Reason.SERVICE_UNAVAILABLE,
                    "SMS service unavailable, try again");
        }

        String national10 = PhoneNumberUtil.normalizeNational(
                mobile91.startsWith("91") ? mobile91.substring(2) : mobile91);

        SmsDeliveryException lastFailure = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                dispatch(national10, otp);
                log.info("MSG91 OTP sent successfully to {}{}", countryCode, maskNational(national10));
                return;
            } catch (SmsDeliveryException e) {
                lastFailure = e;
                if (!isRetryable(e) || attempt == 2) {
                    throw e;
                }
                log.warn("MSG91 OTP attempt {} failed ({}), retrying in {}ms",
                        attempt, e.getMessage(), RETRY_DELAY_MS);
                sleepQuietly(RETRY_DELAY_MS);
            }
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
    }

    /** MSG91 v5 OTP: country + 10-digit mobile + otp only (no template_id or sender in request). */
    private void dispatch(String national10, String otp) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("country", countryCode != null ? countryCode.trim() : "91");
        body.put("mobile", national10);
        body.put("otp", otp);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("authkey", authkey);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    OTP_URL,
                    new HttpEntity<>(body, headers),
                    String.class);
            parseResponse(response.getStatusCode(), response.getBody());
        } catch (ResourceAccessException e) {
            log.error("MSG91 unreachable: {}", e.getMessage());
            throw new SmsDeliveryException(
                    SmsDeliveryException.Reason.SERVICE_UNAVAILABLE,
                    "SMS service unavailable, try again",
                    e);
        } catch (RestClientResponseException e) {
            log.error("MSG91 HTTP {} body={}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw mapHttpFailure(e.getStatusCode(), e.getResponseBodyAsString(), e);
        } catch (SmsDeliveryException e) {
            throw e;
        } catch (Exception e) {
            log.error("MSG91 unexpected error", e);
            throw new SmsDeliveryException(
                    SmsDeliveryException.Reason.SERVICE_UNAVAILABLE,
                    "SMS service unavailable, try again",
                    e);
        }
    }

    private void parseResponse(HttpStatusCode status, String body) {
        if (status.is2xxSuccessful() && isSuccessBody(body)) {
            return;
        }
        throw mapHttpFailure(status, body, null);
    }

    private boolean isSuccessBody(String body) {
        if (body == null || body.isBlank()) {
            return true;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            String type = text(root, "type");
            if ("success".equalsIgnoreCase(type)) {
                return true;
            }
            if ("error".equalsIgnoreCase(type)) {
                return false;
            }
            String message = text(root, "message");
            if (message != null && message.toLowerCase(Locale.ROOT).contains("success")) {
                return true;
            }
            return !root.has("errors");
        } catch (Exception e) {
            log.debug("Could not parse MSG91 body as JSON, treating HTTP 2xx as success: {}", body);
            return true;
        }
    }

    private SmsDeliveryException mapHttpFailure(HttpStatusCode status, String body, Throwable cause) {
        String combined = ((body != null ? body : "") + " " + status.value()).toLowerCase(Locale.ROOT);

        if (combined.contains("balance") || combined.contains("insufficient") || combined.contains("recharge")) {
            log.error("MSG91 ALERT: insufficient balance — {}", body);
            return new SmsDeliveryException(
                    SmsDeliveryException.Reason.INSUFFICIENT_BALANCE,
                    "SMS service unavailable, try again",
                    cause);
        }
        if (combined.contains("dlt") || combined.contains("template")
                || combined.contains("header") || combined.contains("peid")) {
            log.error("MSG91 DLT/template rejection — {}", body);
            return new SmsDeliveryException(
                    SmsDeliveryException.Reason.DLT_REJECTED,
                    "SMS service unavailable, try again",
                    cause);
        }
        if (combined.contains("invalid") && (combined.contains("mobile") || combined.contains("number"))) {
            return new SmsDeliveryException(
                    SmsDeliveryException.Reason.INVALID_MOBILE,
                    "Invalid mobile number",
                    cause);
        }
        int code = status.value();
        if (code == 400 || code == 422) {
            return new SmsDeliveryException(
                    SmsDeliveryException.Reason.INVALID_MOBILE,
                    "Invalid mobile number",
                    cause);
        }
        if (status.is5xxServerError() || code == 408 || code == 504) {
            return new SmsDeliveryException(
                    SmsDeliveryException.Reason.SERVICE_UNAVAILABLE,
                    "SMS service unavailable, try again",
                    cause);
        }

        log.error("MSG91 provider error HTTP {} — {}", status.value(), body);
        return new SmsDeliveryException(
                SmsDeliveryException.Reason.PROVIDER_ERROR,
                "SMS service unavailable, try again",
                cause);
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText();
    }

    private static boolean isRetryable(SmsDeliveryException e) {
        return e.getReason() == SmsDeliveryException.Reason.SERVICE_UNAVAILABLE
                || e.getReason() == SmsDeliveryException.Reason.PROVIDER_ERROR;
    }

    private static String maskNational(String national10) {
        if (national10 == null || national10.length() < 4) {
            return "****";
        }
        return "******" + national10.substring(national10.length() - 4);
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new SmsDeliveryException(
                    SmsDeliveryException.Reason.SERVICE_UNAVAILABLE,
                    "SMS service unavailable, try again",
                    ie);
        }
    }
}
