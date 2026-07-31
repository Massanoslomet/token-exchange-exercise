package no.dnb.exercise.sts_service;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ExchangeController.class)
@Import({
        ServiceRegistry.class,
        ScopeMappingService.class
})
class ExchangeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SubjectTokenValidator subjectTokenValidator;

    @MockitoBean
    private DelegatedTokenService delegatedTokenService;

    @Test
    void shouldReturn401WhenFrontendCredentialIsMissing() throws Exception {
        String body = validExchangeBody("subject-token", "backend:read");

        mockMvc.perform(post("/exchange")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.error").value("invalid_client"))
                .andExpect(jsonPath("$.error_description").value("Missing frontend service credential"));
    }

    @Test
    void shouldReturn400WhenSubjectTokenIsInvalid() throws Exception {
        Mockito.when(subjectTokenValidator.validate("fake-token"))
                .thenThrow(new IllegalArgumentException("Invalid subject_token"));

        String body = validExchangeBody("fake-token", "backend:read");

        mockMvc.perform(post("/exchange")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer frontend-service-token")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.error").value("invalid_grant"))
                .andExpect(jsonPath("$.error_description").value("Invalid, expired, or tampered subject_token"));
    }

    @Test
    void shouldReturn200WhenAgentAlphaRequestsBackendRead() throws Exception {
        Mockito.when(subjectTokenValidator.validate("alpha-token"))
                .thenReturn(new SubjectTokenValidator.SubjectTokenClaims(
                        "agent_alpha",
                        "read"
                ));

        Mockito.when(delegatedTokenService.issueDelegatedAccessToken(
                        "agent_alpha",
                        "backend-service",
                        "backend:read",
                        "frontend-service"
                ))
                .thenReturn("delegated-read-jwt");

        String body = validExchangeBody("alpha-token", "backend:read");

        mockMvc.perform(post("/exchange")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer frontend-service-token")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.access_token").value("delegated-read-jwt"))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(300))
                .andExpect(jsonPath("$.issued_token_type").value("urn:ietf:params:oauth:token-type:access_token"))
                .andExpect(jsonPath("$.scope").value("backend:read"));
    }

    @Test
    void shouldReturn403WhenAgentAlphaRequestsBackendWrite() throws Exception {
        Mockito.when(subjectTokenValidator.validate("alpha-token"))
                .thenReturn(new SubjectTokenValidator.SubjectTokenClaims(
                        "agent_alpha",
                        "read"
                ));

        String body = validExchangeBody("alpha-token", "backend:write");

        mockMvc.perform(post("/exchange")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer frontend-service-token")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.error").value("insufficient_scope"))
                .andExpect(jsonPath("$.error_description").value("Requested scope is not allowed for this subject and caller"));
    }

    @Test
    void shouldReturn200WhenAgentBetaRequestsBackendWrite() throws Exception {
        Mockito.when(subjectTokenValidator.validate("beta-token"))
                .thenReturn(new SubjectTokenValidator.SubjectTokenClaims(
                        "agent_beta",
                        "read write"
                ));

        Mockito.when(delegatedTokenService.issueDelegatedAccessToken(
                        "agent_beta",
                        "backend-service",
                        "backend:write",
                        "frontend-service"
                ))
                .thenReturn("delegated-write-jwt");

        String body = validExchangeBody("beta-token", "backend:write");

        mockMvc.perform(post("/exchange")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer frontend-service-token")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.access_token").value("delegated-write-jwt"))
                .andExpect(jsonPath("$.scope").value("backend:write"));
    }

    private String validExchangeBody(String subjectToken, String scope) {
        return """
                {
                  "grant_type": "urn:ietf:params:oauth:grant-type:token-exchange",
                  "subject_token": "%s",
                  "subject_token_type": "urn:ietf:params:oauth:token-type:id_token",
                  "requested_token_type": "urn:ietf:params:oauth:token-type:access_token",
                  "scope": "%s",
                  "audience": "backend-service"
                }
                """.formatted(subjectToken, scope);
    }
}
