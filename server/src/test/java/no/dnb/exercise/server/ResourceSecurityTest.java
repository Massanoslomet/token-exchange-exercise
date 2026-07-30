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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ResourceController.class)
class ResourceSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtValidationService jwtValidationService;

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
    void shouldReturn200WhenTokenIsValid() throws Exception {
        Mockito.when(jwtValidationService.validate("valid-token"))
              .thenReturn(new JwtValidationService.TokenClaims("agent_alpha", "backend:read", "frontend-service"));

        mockMvc.perform(get("/api/resources")
                        .header(HttpHeaders.ACCEPT, "application/json")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("First resource"))
                .andExpect(jsonPath("$[0].owner").value("agent_alpha"));
    }
}