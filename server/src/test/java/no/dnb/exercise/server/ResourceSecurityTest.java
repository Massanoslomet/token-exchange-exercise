package no.dnb.exercise.server;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ResourceController.class)
class ResourceSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtValidationService jwtValidationService;

    @MockitoBean
    private AuditLogService auditLogService;

    @Test
    void shouldReturn401WhenAuthorizationHeaderIsMissing() throws Exception {
        mockMvc.perform(get("/api/resources")
                        .header(HttpHeaders.ACCEPT, "application/json"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.error").value("unauthorized"))
                .andExpect(jsonPath("$.error_description").value("Missing Authorization Bearer token"));
    }

    @Test
    void shouldReturn401WhenTokenIsInvalid() throws Exception {
        Mockito.when(jwtValidationService.validate(anyString()))
                .thenThrow(new IllegalArgumentException("Invalid token"));

        mockMvc.perform(get("/api/resources")
                        .header(HttpHeaders.ACCEPT, "application/json")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer fake-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.error").value("unauthorized"))
                .andExpect(jsonPath("$.error_description").value("Invalid or expired token"));
    }

    @Test
    void shouldReturn200WhenReadTokenIsUsedForGet() throws Exception {
        Mockito.when(jwtValidationService.validate("read-token"))
                .thenReturn(new JwtValidationService.TokenClaims(
                        "agent_alpha",
                        "backend:read",
                        "frontend-service"
                ));

        mockMvc.perform(get("/api/resources")
                        .header(HttpHeaders.ACCEPT, "application/json")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer read-token"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("First resource"))
                .andExpect(jsonPath("$[0].owner").value("agent_alpha"));
    }

    @Test
    void shouldReturn403WhenReadTokenIsUsedForPost() throws Exception {
        Mockito.when(jwtValidationService.validate("read-token"))
                .thenReturn(new JwtValidationService.TokenClaims(
                        "agent_alpha",
                        "backend:read",
                        "frontend-service"
                ));

        String body = """
                {
                  "name": "Should fail",
                  "owner": "agent_alpha"
                }
                """;

        mockMvc.perform(post("/api/resources")
                        .header(HttpHeaders.ACCEPT, "application/json")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer read-token")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.error").value("insufficient_scope"))
                .andExpect(jsonPath("$.error_description").value("Token does not have required scope: backend:write"));
    }

    @Test
    void shouldReturn201WhenWriteTokenIsUsedForPost() throws Exception {
        Mockito.when(jwtValidationService.validate("write-token"))
                .thenReturn(new JwtValidationService.TokenClaims(
                        "agent_beta",
                        "backend:write",
                        "frontend-service"
                ));

        String body = """
                {
                  "name": "Created with delegated write token",
                  "owner": "agent_beta"
                }
                """;

        mockMvc.perform(post("/api/resources")
                        .header(HttpHeaders.ACCEPT, "application/json")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer write-token")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.name").value("Created with delegated write token"))
                .andExpect(jsonPath("$.owner").value("agent_beta"));
    }
}