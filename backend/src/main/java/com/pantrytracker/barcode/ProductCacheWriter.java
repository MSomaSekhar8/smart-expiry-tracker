package com.pantrytracker.barcode;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists a freshly fetched product in its OWN REQUIRES_NEW transaction so
 * the INSERT always commits, regardless of whether the surrounding lookup runs
 * with no transaction or with a read-only one. saveAndFlush forces the INSERT
 * to execute inside this method, where failures are contained.
 *
 * A cache write must never take down a successful barcode lookup: failures
 * (e.g. a concurrent request inserting the same barcode, or a database hiccup)
 * are swallowed, logged safely, and reported as false.
 */
@Component
public class ProductCacheWriter {

    private static final Logger log = LoggerFactory.getLogger(ProductCacheWriter.class);

    private final ProductCacheRepository productCacheRepository;

    public ProductCacheWriter(ProductCacheRepository productCacheRepository) {
        this.productCacheRepository = productCacheRepository;
    }

    /**
     * @return true when the cache row was persisted; false when the write
     *         failed (the lookup still returns the external product)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean write(String barcode, JsonNode payload) {
        try {
            productCacheRepository.saveAndFlush(new ProductCache(barcode, payload));
            return true;
        } catch (Exception ex) {
            log.warn("Product cache write failed: {}", ex.getClass().getSimpleName());
            return false;
        }
    }
}