package com.youdash.controller;

import com.youdash.dto.AppLinksResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public app configuration endpoints for mobile clients.
 */
@RestController
@RequestMapping("/api/app-config")
@CrossOrigin(origins = "*")
public class AppConfigController {

    /**
     * Returns publicly hosted legal document links used by the Flutter app.
     */
    @GetMapping("/links")
    public ResponseEntity<AppLinksResponse> links() {
        AppLinksResponse body = new AppLinksResponse(
                "https://www.youdashexpress.com/privacy-policy.html",
                "https://www.youdashexpress.com/terms.html");
        return ResponseEntity.ok(body);
    }
}
