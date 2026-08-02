package no.dnb.exercise.frontend_service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class FrontendOrchestrationServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldExchangeTokenAndCallBackendWithDelegatedToken() throws Exception {
        HttpServer stsServer = HttpServer.create(new InetSocketAddress(0), 0);
        HttpServer backendServer = HttpServer.create(new InetSocketAddress(0), 0);

        try {
            stsServer.createContext("/exchange", exchange -> {
                assertEquals("POST", exchange.getRequestMethod());
                assertEquals("Bearer frontend-service-token", exchange.getRequestHeaders().getFirst("Authorization"));

                String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

                assertTrue(requestBody.contains("id-token-from-user"));
                assertTrue(requestBody.contains("backend:read"));
                assertTrue(requestBody.contains("backend-service"));

                String responseBody = """
                        {
                          "access_token": "delegated-access-token",
                          "issued_token_type": "urn:ietf:params:oauth:token-type:access_token",
                          "token_type": "Bearer",
                          "expires_in": 300
                        }
                        """;

                sendJson(exchange, 200, responseBody);
            });

            backendServer.createContext("/api/resources", exchange -> {
                assertEquals("GET", exchange.getRequestMethod());
                assertEquals("Bearer delegated-access-token", exchange.getRequestHeaders().getFirst("Authorization"));

                String responseBody = """
                        [
                          {
                            "id": 1,
                            "name": "First resource",
                            "owner": "agent_alpha"
                          }
                        ]
                        """;

                sendJson(exchange, 200, responseBody);
            });

            stsServer.start();
            backendServer.start();

            String stsUrl = "http://localhost:" + stsServer.getAddress().getPort() + "/exchange";
            String backendUrl = "http://localhost:" + backendServer.getAddress().getPort() + "/api/resources";

            FrontendOrchestrationService service = new FrontendOrchestrationService(
                    objectMapper,
                    stsUrl,
                    "frontend-service-token",
                    backendUrl
            );

            BackendResponse response = service.getResources("id-token-from-user");

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("First resource"));
            assertTrue(response.body().contains("agent_alpha"));

        } finally {
            stsServer.stop(0);
            backendServer.stop(0);
        }
    }

    @Test
    void shouldRetryWithNewDelegatedTokenWhenBackendReturns401() throws Exception {
        HttpServer stsServer = HttpServer.create(new InetSocketAddress(0), 0);
        HttpServer backendServer = HttpServer.create(new InetSocketAddress(0), 0);

        AtomicInteger stsCalls = new AtomicInteger(0);
        AtomicInteger backendCalls = new AtomicInteger(0);

        try {
            stsServer.createContext("/exchange", exchange -> {
                int callNumber = stsCalls.incrementAndGet();

                String responseBody = """
                        {
                          "access_token": "delegated-access-token-%d",
                          "issued_token_type": "urn:ietf:params:oauth:token-type:access_token",
                          "token_type": "Bearer",
                          "expires_in": 300
                        }
                        """.formatted(callNumber);

                sendJson(exchange, 200, responseBody);
            });

            backendServer.createContext("/api/resources", exchange -> {
                int callNumber = backendCalls.incrementAndGet();

                if (callNumber == 1) {
                    assertEquals("Bearer delegated-access-token-1", exchange.getRequestHeaders().getFirst("Authorization"));
                    sendJson(exchange, 401, "{\"error\":\"unauthorized\",\"error_description\":\"Expired token\"}");
                    return;
                }

                assertEquals("Bearer delegated-access-token-2", exchange.getRequestHeaders().getFirst("Authorization"));

                String responseBody = """
                        [
                          {
                            "id": 1,
                            "name": "First resource",
                            "owner": "agent_alpha"
                          }
                        ]
                        """;

                sendJson(exchange, 200, responseBody);
            });

            stsServer.start();
            backendServer.start();

            String stsUrl = "http://localhost:" + stsServer.getAddress().getPort() + "/exchange";
            String backendUrl = "http://localhost:" + backendServer.getAddress().getPort() + "/api/resources";

            FrontendOrchestrationService service = new FrontendOrchestrationService(
                    objectMapper,
                    stsUrl,
                    "frontend-service-token",
                    backendUrl
            );

            BackendResponse response = service.getResources("id-token-from-user");

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("First resource"));
            assertEquals(2, stsCalls.get());
            assertEquals(2, backendCalls.get());

        } finally {
            stsServer.stop(0);
            backendServer.stop(0);
        }
    }

    @Test
    void shouldForwardStsErrorWhenTokenExchangeFails() throws Exception {
        HttpServer stsServer = HttpServer.create(new InetSocketAddress(0), 0);

        try {
            stsServer.createContext("/exchange", exchange -> {
                String responseBody = """
                        {
                          "error": "invalid_grant",
                          "error_description": "Invalid subject token"
                        }
                        """;

                sendJson(exchange, 400, responseBody);
            });

            stsServer.start();

            String stsUrl = "http://localhost:" + stsServer.getAddress().getPort() + "/exchange";

            FrontendOrchestrationService service = new FrontendOrchestrationService(
                    objectMapper,
                    stsUrl,
                    "frontend-service-token",
                    "http://localhost:9999/api/resources"
            );

            FrontendException exception = assertThrows(
                    FrontendException.class,
                    () -> service.getResources("bad-id-token")
            );

            assertEquals(400, exception.statusCode());
            assertTrue(exception.responseBody().contains("invalid_grant"));
            assertTrue(exception.responseBody().contains("Invalid subject token"));

        } finally {
            stsServer.stop(0);
        }
    }

    private static void sendJson(com.sun.net.httpserver.HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}