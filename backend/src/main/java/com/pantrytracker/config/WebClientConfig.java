package com.pantrytracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Server-side client for the Open Food Facts REST API (barcode lookup).
 * The browser never talks to this third-party API directly.
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient openFoodFactsWebClient() {
        return WebClient.builder()
                .baseUrl("https://world.openfoodfacts.org")
                .defaultHeader(HttpHeaders.USER_AGENT,
                        "SmartExpiryTracker/0.1 (pantry-waste-tracker)")
                .build();
    }
}