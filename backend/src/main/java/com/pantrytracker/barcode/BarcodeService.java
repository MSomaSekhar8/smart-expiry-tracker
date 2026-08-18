package com.pantrytracker.barcode;

import com.fasterxml.jackson.databind.JsonNode;
import com.pantrytracker.common.BadRequestException;
import java.time.Duration;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Server-side Open Food Facts lookup with a Postgres-backed cache. The
 * browser never calls the public API directly.
 */
@Service
public class BarcodeService {

    private static final Pattern BARCODE = Pattern.compile("^\\d{8,14}$");

    private final WebClient openFoodFactsWebClient;
    private final ProductCacheRepository productCacheRepository;

    public BarcodeService(WebClient openFoodFactsWebClient,
                          ProductCacheRepository productCacheRepository) {
        this.openFoodFactsWebClient = openFoodFactsWebClient;
        this.productCacheRepository = productCacheRepository;
    }

    @Transactional(readOnly = true)
    public BarcodeDtos.LookupResult lookup(String barcode) {
        String code = barcode == null ? "" : barcode.trim();
        if (!BARCODE.matcher(code).matches()) {
            throw new BadRequestException("Invalid barcode format");
        }
        return productCacheRepository.findById(code)
                .map(cache -> toResult(code, cache.getPayload(), true))
                .orElseGet(() -> fetchAndCache(code));
    }

    private BarcodeDtos.LookupResult fetchAndCache(String code) {
        JsonNode payload;
        try {
            payload = openFoodFactsWebClient.get()
                    .uri("/api/v2/product/{code}.json", code)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
        } catch (Exception ex) {
            throw new BadRequestException("Barcode service unavailable right now");
        }
        if (payload == null || payload.path("status").asInt(0) != 1) {
            throw new BadRequestException("Product not found for this barcode");
        }
        productCacheRepository.save(new ProductCache(code, payload));
        return toResult(code, payload, false);
    }

    private BarcodeDtos.LookupResult toResult(String code, JsonNode payload, boolean cached) {
        JsonNode product = payload.path("product");
        return new BarcodeDtos.LookupResult(
                code,
                product.path("product_name").asText(null),
                product.path("brands").asText(null),
                firstNonNull(product.path("categories_tags"), product.path("categories")),
                cached);
    }

    private String firstNonNull(JsonNode node, JsonNode fallback) {
        if (node != null && node.isArray() && node.size() > 0) {
            return node.get(0).asText().replace("en:", "").replace("-", " ");
        }
        if (fallback != null && fallback.isTextual()) {
            return fallback.asText();
        }
        return null;
    }
}