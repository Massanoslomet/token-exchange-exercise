package no.dnb.exercise.identity_provider;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
public class ClientRegistry {

    private final Map<String, ClientInfo> clients = Map.of(
            "agent_alpha", new ClientInfo("agent_alpha", "alpha-secret", "read"),
            "agent_beta", new ClientInfo("agent_beta", "beta-secret", "read write"),
            "agent_admin", new ClientInfo("agent_admin", "admin-secret", "read write admin")
    );

    public Optional<ClientInfo> authenticate(String clientId, String clientSecret) {
        ClientInfo client = clients.get(clientId);

        if (client == null) {
            return Optional.empty();
        }

        if (!client.clientSecret().equals(clientSecret)) {
            return Optional.empty();
        }

        return Optional.of(client);
    }

    public record ClientInfo(
            String clientId,
            String clientSecret,
            String scope
    ) {
    }
}