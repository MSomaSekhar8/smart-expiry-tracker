package com.pantrytracker.barcode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class ProductCacheWriterTest {

    @Mock
    private ProductCacheRepository productCacheRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode productPayload() throws Exception {
        return objectMapper.readTree(
                "{\"status\":1,\"product\":{\"product_name\":\"Whole Milk\"}}");
    }

    /**
     * Transaction regression test. The original bug wrote via save() inside a
     * read-only transaction: the INSERT never executed. This verifies the fix:
     * the write runs in a REQUIRES_NEW transaction and forces the INSERT via
     * saveAndFlush (flush executes the statement inside the transaction, which
     * commits on method return). Both assertions fail against the old code.
     */
    @Test
    void cacheWriteRunsInRequiresNewTransactionAndFlushesTheInsert() throws Exception {
        ProductCacheWriter writer = new ProductCacheWriter(productCacheRepository);
        JsonNode payload = productPayload();

        Transactional tx = AnnotationUtils.findAnnotation(
                ProductCacheWriter.class.getMethod("write", String.class, JsonNode.class),
                Transactional.class);
        assertThat(tx).isNotNull();
        assertThat(tx.propagation()).isEqualTo(Propagation.REQUIRES_NEW);

        when(productCacheRepository.saveAndFlush(any(ProductCache.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(writer.write("1234567890123", payload)).isTrue();

        ArgumentCaptor<ProductCache> captor = ArgumentCaptor.forClass(ProductCache.class);
        verify(productCacheRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getBarcode()).isEqualTo("1234567890123");
        assertThat(captor.getValue().getPayload()).isSameAs(payload);
        assertThat(captor.getValue().getFetchedAt()).isNotNull();
    }

    @Test
    void concurrentDuplicateInsertReturnsFalseWithoutThrowing() throws Exception {
        ProductCacheWriter writer = new ProductCacheWriter(productCacheRepository);
        when(productCacheRepository.saveAndFlush(any(ProductCache.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        assertThatCode(() -> writer.write("1234567890123", productPayload()))
                .doesNotThrowAnyException();
        assertThat(writer.write("1234567890123", productPayload())).isFalse();
    }

    @Test
    void genericDatabaseFailureReturnsFalseWithoutExposingDetails() throws Exception {
        ProductCacheWriter writer = new ProductCacheWriter(productCacheRepository);
        when(productCacheRepository.saveAndFlush(any(ProductCache.class)))
                .thenThrow(new RuntimeException("org.postgresql.util.PSQLException: ERROR: could not serialize"));

        assertThatCode(() -> writer.write("1234567890123", productPayload()))
                .doesNotThrowAnyException();
        assertThat(writer.write("1234567890123", productPayload())).isFalse();
    }
}