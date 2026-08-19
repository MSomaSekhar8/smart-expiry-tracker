package com.pantrytracker.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal liveness endpoint. Returns only the status — never credentials,
 * environment values, or configuration details.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public HealthStatus health() {
        return new HealthStatus("UP");
    }

    public record HealthStatus(String status) {}
}