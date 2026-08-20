package com.pantrytracker.barcode;

import com.fasterxml.jackson.databind.JsonNode;
import com.pantrytracker.common.BadRequestException;
import java.time.Duration;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Server-side Open Food Facts lookup with a Postgres-backed cache. The
 * browser never calls the public API directly.
 *
 * The cache read is read-only by nature (SimpleJpaRepository runs it in its
 * own short read-only transaction), but lookup() itself holds NO transaction:
 * the external API call must never run inside a DB transaction, and the cache
 * write goes through {@link ProductCacheWriter}, which commits in its own
 * REQUIRES_NEW transaction.
 */
@Service
public class BarcodeService {

    private static final Pattern BARCODE = Pattern.compile("^\\d{8,14}$");

    private final WebClient openFoodFactsWebClient;
    private final ProductCacheRepository productCacheRepository;
    private final ProductCacheWriter productCacheWriter;

    public BarcodeService(WebClient openFoodFactsWebClient,
                          ProductCacheRepository productCacheRepository,
                          ProductCacheWriter productCacheWriter) {
        this.openFoodFactsWebClient = openFoodFactsWebClient;
        this.productCacheRepository = productCacheRepository;
        this.productCacheWriter = productCacheWriter;
    }

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
        productCacheWriter.write(code, payload);
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