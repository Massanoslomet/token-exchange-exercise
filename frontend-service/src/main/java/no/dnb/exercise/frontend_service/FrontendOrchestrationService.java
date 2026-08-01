package no.dnb.exercise.frontend_service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

@Service
public class FrontendOrchestrationService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private final String stsExchangeUrl;
    private final String frontendServiceToken;
    private final String backendResourcesUrl;

    public FrontendOrchestrationService(
            ObjectMapper objectMapper,
            @Value("${sts.exchange-url}") String stsExchangeUrl,
            @Value("${sts.service-token}") String frontendServiceToken,
            @Value("${backend.resources-url}") String backendResourcesUrl
    ) {
        this.objectMapper = objectMapper;
        this.stsExchangeUrl = stsExchangeUrl;
        this.frontendServiceToken = frontendServiceToken;
        this.backendResourcesUrl = backendResourcesUrl;
    }

    public BackendResponse getResources(String idToken) {
        String accessToken = exchangeToken(idToken, "backend:read");

        BackendResponse firstAttempt = callBackend(accessToken);

        if (firstAttempt.statusCode() == 401) {
            String retryAccessToken = exchangeToken(idToken, "backend:read");
            return callBackend(retryAccessToken);
        }

        return firstAttempt;
    }

    private String exchangeToken(String idToken, String scope) {
        try {
            Map<String, String> requestBody = Map.of(
                    "grant_type", "urn:ietf:params:oauth:grant-type:token-exchange",
                    "subject_token", idToken,
                    "subject_token_type", "urn:ietf:params:oauth:token-type:id_token",
                    "requested_token_type", "urn:ietf:params:oauth:token-type:access_token",
                    "scope", scope,
                    "audience", "backend-service"
            );

            String json = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(stsExchangeUrl))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + frontendServiceToken)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new FrontendException(response.statusCode(), response.body());
            }

            JsonNode jsonNode = objectMapper.readTree(response.body());
            return jsonNode.get("access_token").asText();

        } catch (FrontendException e) {
            throw e;
        } catch (Exception e) {
            throw new FrontendException(500, "{\"error\":\"frontend_error\",\"error_description\":\"Token exchange failed\"}");
        }
    }

    private BackendResponse callBackend(String accessToken) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(backendResourcesUrl))
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            return new BackendResponse(response.statusCode(), response.body());

        } catch (Exception e) {
            throw new FrontendException(500, "{\"error\":\"frontend_error\",\"error_description\":\"Backend call failed\"}");
        }
    }
}