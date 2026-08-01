package no.dnb.exercise.frontend_service;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.ArgumentMatchers.eq;

@WebMvcTest(FrontendController.class)
class FrontendControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FrontendOrchestrationService orchestrationService;

    @Test
    void shouldReturn401WhenAuthorizationHeaderIsMissing() throws Exception {
        mockMvc.perform(get("/frontend/resources")
                        .header(HttpHeaders.ACCEPT, "application/json"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.error").value("unauthorized"))
                .andExpect(jsonPath("$.error_description").value("Missing Authorization Bearer token"));
    }

    @Test
    void shouldReturnBackendResponseWhenBearerTokenIsProvided() throws Exception {
        String idToken = "id-token-from-identity-provider";

        String backendBody = """
                [
                  {
                    "id": 1,
                    "name": "First resource",
                    "owner": "agent_alpha"
                  }
                ]
                """;

        Mockito.when(orchestrationService.getResources(eq(idToken)))
                .thenReturn(new BackendResponse(200, backendBody));

        mockMvc.perform(get("/frontend/resources")
                        .header(HttpHeaders.ACCEPT, "application/json")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + idToken))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("First resource"))
                .andExpect(jsonPath("$[0].owner").value("agent_alpha"));

        Mockito.verify(orchestrationService).getResources(idToken);
    }

    @Test
    void shouldForwardErrorFromOrchestrationService() throws Exception {
        String idToken = "bad-token";

        Mockito.when(orchestrationService.getResources(eq(idToken)))
                .thenThrow(new FrontendException(
                        400,
                        "{\"error\":\"invalid_grant\",\"error_description\":\"Invalid subject token\"}"
                ));

        mockMvc.perform(get("/frontend/resources")
                        .header(HttpHeaders.ACCEPT, "application/json")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + idToken))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.error").value("invalid_grant"))
                .andExpect(jsonPath("$.error_description").value("Invalid subject token"));
    }
}