package com.pantrytracker.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.pantrytracker.notification.ExpiryDigestTemplate;
import com.pantrytracker.notification.NotificationType;
import com.pantrytracker.user.User;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ResendClientTest {

    private final ResendClient client = new ResendClient("", "Pantry Tracker <onboarding@resend.dev>");

    private static final User VALID_USER = new User("a@example.com", "hash", "Test");
    private static final List<ExpiryDigestTemplate.DigestLine> ONE_LINE = List.of(
            new ExpiryDigestTemplate.DigestLine("Milk", LocalDate.now(), NotificationType.EXPIRING_SOON, 2));

    private static HttpServer startServer(ServerHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            return thread;
        }));
        server.createContext("/emails", exchange -> {
            try {
                handler.handle(exchange);
            } catch (IOException ex) {
                exchange.close();
            }
        });
        server.start();
        return server;
    }

    @FunctionalInterface
    private interface ServerHandler {
        void handle(HttpExchange exchange) throws IOException;
    }

    private static ResendClient clientFor(String baseUrl, int connectTimeoutMillis, int readTimeoutMillis) {
        return new ResendClient("test-key", "Pantry Tracker <test@example.com>",
                baseUrl, connectTimeoutMillis, readTimeoutMillis);
    }

    @Test
    void invalidRecipientIsSkippedWithoutSending() {
        User user = new User("not-an-email", "hash", "Test");

        assertThatCode(() -> client.sendDigest(user, ONE_LINE, List.of())).doesNotThrowAnyException();
        assertThat(client.sendDigest(user, ONE_LINE, List.of())).isFalse();
    }

    @Test
    void blankRecipientIsSkippedWithoutSending() {
        User user = new User("", "hash", "Test");

        assertThat(client.sendDigest(user, ONE_LINE, List.of())).isFalse();
    }

    @Test
    void nullUserIsSkippedWithoutSending() {
        assertThat(client.sendDigest(null, ONE_LINE, List.of())).isFalse();
    }

    @Test
    void emptyDigestIsSkipped() {
        assertThat(client.sendDigest(VALID_USER, List.of(), List.of())).isFalse();
    }

    @Test
    void dryRunWithValidRecipientDoesNotThrow() {
        assertThatCode(() -> client.sendDigest(VALID_USER,
                ONE_LINE,
                List.of(new ExpiryDigestTemplate.DigestLine("Bread", LocalDate.now().minusDays(1),
                        NotificationType.EXPIRED, -1))))
                .doesNotThrowAnyException();
    }

    @Test
    void dryRunReturnsTrueSoDevDigestsAreStillRecorded() {
        assertThat(client.sendDigest(VALID_USER, ONE_LINE, List.of())).isTrue();
    }

    @Test
    void successfulSubmissionReturnsTrueAndUsesTheBearerToken() throws Exception {
        AtomicReference<String> authHeader = new AtomicReference<>();
        HttpServer server = startServer(exchange -> {
            authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        try {
            ResendClient realClient = clientFor(
                    "http://127.0.0.1:" + server.getAddress().getPort(), 1_000, 1_000);

            assertThat(realClient.sendDigest(VALID_USER, ONE_LINE, List.of())).isTrue();
            assertThat(authHeader.get()).isEqualTo("Bearer test-key");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void serverErrorReturnsFalse() throws Exception {
        HttpServer server = startServer(exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        try {
            ResendClient realClient = clientFor(
                    "http://127.0.0.1:" + server.getAddress().getPort(), 1_000, 1_000);

            assertThat(realClient.sendDigest(VALID_USER, ONE_LINE, List.of())).isFalse();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void slowServerHitsTheReadTimeoutAndReturnsFalseWithoutHanging() throws Exception {
        HttpServer server = startServer(exchange -> {
            try {
                Thread.sleep(3_000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        try {
            ResendClient realClient = clientFor(
                    "http://127.0.0.1:" + server.getAddress().getPort(), 500, 200);

            long start = System.nanoTime();
            boolean sent = realClient.sendDigest(VALID_USER, ONE_LINE, List.of());
            long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

            assertThat(sent).isFalse();
            assertThat(elapsedMillis).isLessThan(2_500);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void connectionFailureReturnsFalse() {
        ResendClient realClient = clientFor("http://127.0.0.1:1", 1_000, 1_000);

        assertThat(realClient.sendDigest(VALID_USER, ONE_LINE, List.of())).isFalse();
    }

    @Test
    void productionDefaultsUseFiveSecondConnectAndTenSecondReadTimeouts() {
        ResendClient prodClient = new ResendClient("test-key", "Pantry Tracker <test@example.com>");

        assertThat(prodClient.connectTimeoutMillis).isEqualTo(5_000);
        assertThat(prodClient.readTimeoutMillis).isEqualTo(10_000);
    }
}