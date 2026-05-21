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

    /** MSG91 Template ID from OTP → Templates (NOT the numeric DLT Template ID). */
    @Value("${msg91.otp.template.id:}")
    private String templateId;

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
        if (templateId == null || templateId.isBlank()) {
            log.error("MSG91 template id is not configured (msg91.otp.template.id / MSG91_OTP_TEMPLATE_ID)");
            throw new SmsDeliveryException(
                    SmsDeliveryException.Reason.SERVICE_UNAVAILABLE,
                    "SMS service unavailable, try again");
        }
        if (looksLikeDltTemplateId(templateId)) {
            log.error(
                    "MSG91_OTP_TEMPLATE_ID looks like a DLT id (numeric). Use MSG91 Template ID from OTP → Templates, "
                            + "e.g. 6a0c2283686518230d080bf2");
            throw new SmsDeliveryException(
                    SmsDeliveryException.Reason.DLT_REJECTED,
                    "SMS service unavailable, try again");
        }

        String national10 = PhoneNumberUtil.normalizeNational(
                mobile91.startsWith("91") ? mobile91.substring(2) : mobile91);
        String cc = countryCode != null ? countryCode.trim() : "91";
        String mobileInternational = cc + national10;

        SmsDeliveryException lastFailure = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                dispatch(mobileInternational, otp);
                log.info("MSG91 OTP accepted for {}", maskMobile(mobileInternational));
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

    private void dispatch(String mobileInternational, String otp) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("template_id", templateId.trim());
        body.put("mobile", mobileInternational);
        body.put("otp", otp);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("authkey", authkey);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    OTP_URL,
                    new HttpEntity<>(body, headers),
                    String.class);
            String responseBody = response.getBody();
            log.info("MSG91 response HTTP {} body={}", response.getStatusCode().value(), responseBody);
            parseResponse(response.getStatusCode(), responseBody);
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
        if (!status.is2xxSuccessful()) {
            throw mapHttpFailure(status, body, null);
        }
        if (!isSuccessBody(body)) {
            throw mapHttpFailure(status, body, null);
        }
    }

    private boolean isSuccessBody(String body) {
        if (body == null || body.isBlank()) {
            log.warn("MSG91 returned empty body on HTTP 2xx — treating as failure");
            return false;
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
            if (root.has("request_id") && !root.get("request_id").isNull()) {
                return true;
            }
            String message = text(root, "message");
            if (message != null) {
                String m = message.toLowerCase(Locale.ROOT);
                return m.contains("otp") && (m.contains("sent") || m.contains("success"));
            }
            return false;
        } catch (Exception e) {
            log.warn("MSG91 response not JSON, treating as failure: {}", body);
            return false;
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
        if (combined.contains("invalid template") || combined.contains("template id missing")) {
            log.error("MSG91 invalid template_id — {}", body);
            return new SmsDeliveryException(
                    SmsDeliveryException.Reason.DLT_REJECTED,
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

    private static boolean looksLikeDltTemplateId(String id) {
        if (id == null) {
            return false;
        }
        String t = id.trim();
        return t.length() >= 15 && t.matches("\\d+");
    }

    private static String maskMobile(String mobileInternational) {
        if (mobileInternational == null || mobileInternational.length() < 6) {
            return "****";
        }
        return mobileInternational.substring(0, 4) + "******" + mobileInternational.substring(mobileInternational.length() - 2);
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
