package com.pantrytracker.barcode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pantrytracker.common.BadRequestException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

@ExtendWith(MockitoExtension.class)
class BarcodeServiceTest {

    private static final String BARCODE = "1234567890123";
    private static final String PRODUCT_JSON = """
            {
              "status": 1,
              "product": {
                "product_name": "Whole Milk",
                "brands": "Acme Dairy",
                "categories_tags": ["en:dairy", "en:milk"]
              }
            }
            """;
    private static final String NOT_FOUND_JSON = """
            {
              "status": 0,
              "status_verbose": "product not found"
            }
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ProductCacheRepository productCacheRepository;
    @Mock
    private ProductCacheWriter productCacheWriter;

    private HttpServer offServer;
    private AtomicInteger offRequests;
    private volatile String offPayload = PRODUCT_JSON;
    private volatile boolean offRespondWithError;
    private BarcodeService service;

    @BeforeEach
    void setUp() throws Exception {
        offRequests = new AtomicInteger();
        offServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        offServer.setExecutor(Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            return thread;
        }));
        offServer.createContext("/", this::handleOffRequest);
        offServer.start();

        WebClient webClient = WebClient.builder()
                .baseUrl("http://127.0.0.1:" + offServer.getAddress().getPort())
                .build();
        service = new BarcodeService(webClient, productCacheRepository, productCacheWriter);
    }

    @AfterEach
    void tearDown() {
        offServer.stop(0);
    }

    private void handleOffRequest(HttpExchange exchange) throws IOException {
        offRequests.incrementAndGet();
        if (offRespondWithError) {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
            return;
        }
        byte[] body = offPayload.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    @Test
    void firstLookupFetchesFromTheApiAndWritesTheCache() throws Exception {
        when(productCacheRepository.findById(BARCODE)).thenReturn(Optional.empty());
        when(productCacheWriter.write(eq(BARCODE), any(JsonNode.class))).thenReturn(true);

        BarcodeDtos.LookupResult result = service.lookup(BARCODE);

        assertThat(result.barcode()).isEqualTo(BARCODE);
        assertThat(result.name()).isEqualTo("Whole Milk");
        assertThat(result.brand()).isEqualTo("Acme Dairy");
        assertThat(result.category()).isEqualTo("dairy");
        assertThat(result.cached()).isFalse();
        assertThat(offRequests.get()).isEqualTo(1);

        ArgumentCaptor<JsonNode> payload = ArgumentCaptor.forClass(JsonNode.class);
        verify(productCacheWriter).write(eq(BARCODE), payload.capture());
        assertThat(payload.getValue().path("product").path("product_name").asText())
                .isEqualTo("Whole Milk");
    }

    @Test
    void secondLookupServesFromCacheWithoutHittingTheApiAgain() throws Exception {
        JsonNode cachedPayload = objectMapper.readTree(PRODUCT_JSON);
        ProductCache cache = new ProductCache(BARCODE, cachedPayload);
        when(productCacheRepository.findById(BARCODE))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(cache));
        when(productCacheWriter.write(eq(BARCODE), any(JsonNode.class))).thenReturn(true);

        BarcodeDtos.LookupResult first = service.lookup(BARCODE);
        BarcodeDtos.LookupResult second = service.lookup(BARCODE);

        assertThat(first.cached()).isFalse();
        assertThat(second.cached()).isTrue();
        assertThat(second.name()).isEqualTo("Whole Milk");
        assertThat(second.brand()).isEqualTo("Acme Dairy");
        assertThat(second.category()).isEqualTo("dairy");
        assertThat(offRequests.get()).isEqualTo(1);
        verify(productCacheWriter).write(eq(BARCODE), any(JsonNode.class));
    }

    @Test
    void externalApiFailureDoesNotWriteAnyCacheRow() {
        when(productCacheRepository.findById(BARCODE)).thenReturn(Optional.empty());
        offRespondWithError = true;

        assertThatThrownBy(() -> service.lookup(BARCODE))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Barcode service unavailable right now");
        verify(productCacheWriter, never()).write(any(), any());
        verify(productCacheRepository, never()).saveAndFlush(any());
    }

    @Test
    void productNotFoundDoesNotWriteAnyCacheRow() {
        when(productCacheRepository.findById(BARCODE)).thenReturn(Optional.empty());
        offPayload = NOT_FOUND_JSON;

        assertThatThrownBy(() -> service.lookup(BARCODE))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Product not found for this barcode");
        verify(productCacheWriter, never()).write(any(), any());
    }

    @Test
    void cacheWriteFailureStillReturnsTheExternalProduct() {
        when(productCacheRepository.findById(BARCODE)).thenReturn(Optional.empty());
        when(productCacheWriter.write(eq(BARCODE), any(JsonNode.class))).thenReturn(false);

        BarcodeDtos.LookupResult result = service.lookup(BARCODE);

        assertThat(result.name()).isEqualTo("Whole Milk");
        assertThat(result.brand()).isEqualTo("Acme Dairy");
        assertThat(result.cached()).isFalse();
        assertThat(offRequests.get()).isEqualTo(1);
    }

    @Test
    void cachedLookupReturnsTheStoredPayloadWithoutCallingTheApi() throws Exception {
        JsonNode cachedPayload = objectMapper.readTree(PRODUCT_JSON);
        when(productCacheRepository.findById(BARCODE))
                .thenReturn(Optional.of(new ProductCache(BARCODE, cachedPayload)));

        BarcodeDtos.LookupResult result = service.lookup(BARCODE);

        assertThat(result.cached()).isTrue();
        assertThat(result.name()).isEqualTo("Whole Milk");
        assertThat(result.brand()).isEqualTo("Acme Dairy");
        assertThat(result.category()).isEqualTo("dairy");
        assertThat(offRequests.get()).isZero();
        verify(productCacheWriter, never()).write(any(), any());
    }

    @Test
    void invalidBarcodeIsRejectedWithoutCallingTheApi() {
        assertThatThrownBy(() -> service.lookup("abc"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid barcode format");
        assertThat(offRequests.get()).isZero();
    }

    /**
     * Regression guard for the original bug: lookup() must NOT be
     * @Transactional(readOnly = true). If it ever is again, a cache write
     * could silently join a read-only transaction and never persist.
     */
    @Test
    void lookupIsNotTransactionalSoTheCacheWriteCanNeverJoinAReadOnlyTransaction() throws Exception {
        Transactional tx = org.springframework.core.annotation.AnnotationUtils.findAnnotation(
                BarcodeService.class.getMethod("lookup", String.class), Transactional.class);
        assertThat(tx).isNull();
    }
}