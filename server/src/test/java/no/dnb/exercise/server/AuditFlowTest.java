package no.dnb.exercise.server;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuditFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtValidationService jwtValidationService;

    @Test
    void shouldRecordDeniedMissingTokenAuditEntry() throws Exception {
        mockMvc.perform(get("/api/resources")
                        .header(HttpHeaders.ACCEPT, "application/json"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/audit"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$[0].event").value("ACCESS"))
                .andExpect(jsonPath("$[0].user").value("unknown"))
                .andExpect(jsonPath("$[0].actor").value("unknown"))
                .andExpect(jsonPath("$[0].action").value("GET /api/resources"))
                .andExpect(jsonPath("$[0].scope").value("none"))
                .andExpect(jsonPath("$[0].result").value("DENIED_MISSING_TOKEN"));
    }

    @Test
    void shouldRecordAllowedAuditEntry() throws Exception {
        Mockito.when(jwtValidationService.validate("read-token"))
                .thenReturn(new JwtValidationService.TokenClaims(
                        "agent_alpha",
                        "backend:read",
                        "frontend-service"
                ));

        mockMvc.perform(get("/api/resources")
                        .header(HttpHeaders.ACCEPT, "application/json")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer read-token"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/audit"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$[0].event").value("ACCESS"))
                .andExpect(jsonPath("$[0].user").value("agent_alpha"))
                .andExpect(jsonPath("$[0].actor").value("frontend-service"))
                .andExpect(jsonPath("$[0].action").value("GET /api/resources"))
                .andExpect(jsonPath("$[0].scope").value("backend:read"))
                .andExpect(jsonPath("$[0].result").value("ALLOWED"));
    }

    @Test
    void shouldRecordDeniedInsufficientScopeAuditEntry() throws Exception {
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
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/audit"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$[0].event").value("ACCESS"))
                .andExpect(jsonPath("$[0].user").value("agent_alpha"))
                .andExpect(jsonPath("$[0].actor").value("frontend-service"))
                .andExpect(jsonPath("$[0].action").value("POST /api/resources"))
                .andExpect(jsonPath("$[0].scope").value("backend:read"))
                .andExpect(jsonPath("$[0].result").value("DENIED_INSUFFICIENT_SCOPE"));
    }
}
