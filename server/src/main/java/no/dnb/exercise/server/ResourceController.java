package no.dnb.exercise.server;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@RestController
@Tag(name = "Resources", description = "REST endpoints for resource management")
public class ResourceController {

    private final List<ResourceItem> resources = new ArrayList<>(List.of(
            new ResourceItem(1, "First resource", "agent_alpha"),
            new ResourceItem(2, "Second resource", "agent_beta")
    ));

    @Operation(summary = "Get all resources")
    @ApiResponse(responseCode = "200", description = "Resources returned successfully")
    @ApiResponse(responseCode = "406", description = "Only application/json is supported")
    @GetMapping(value = "/api/resources", produces = "application/json")
    public ResponseEntity<List<ResourceItem>> getResources() {
        return ResponseEntity.ok(resources);
    }

    @Operation(summary = "Get one resource by ID")
    @ApiResponse(responseCode = "200", description = "Resource found")
    @ApiResponse(responseCode = "404", description = "Resource not found")
    @ApiResponse(responseCode = "406", description = "Only application/json is supported")
    @GetMapping(value = "/api/resources/{id}", produces = "application/json")
    public ResponseEntity<?> getResourceById(@PathVariable int id) {
        return resources.stream()
                .filter(resource -> resource.id() == id)
                .findFirst()
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404).body(
                        ApiError.of(
                                "not_found",
                                "Resource with id " + id + " was not found"
                        )
                ));
    }

    @Operation(summary = "Create a new resource")
    @ApiResponse(responseCode = "201", description = "Resource created")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @ApiResponse(responseCode = "406", description = "Only application/json is supported")
    @PostMapping(
            value = "/api/resources",
            consumes = "application/json",
            produces = "application/json"
    )
    public ResponseEntity<ResourceItem> createResource(
            @Valid @RequestBody CreateResourceRequest request
    ) {
        int nextId = resources.size() + 1;

        ResourceItem created = new ResourceItem(
                nextId,
                request.name(),
                request.owner()
        );

        resources.add(created);

        return ResponseEntity
                .created(URI.create("/api/resources/" + created.id()))
                .body(created);
    }

    @Operation(summary = "Replace an existing resource")
    @ApiResponse(responseCode = "200", description = "Resource updated")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @ApiResponse(responseCode = "404", description = "Resource not found")
    @ApiResponse(responseCode = "406", description = "Only application/json is supported")
    @PutMapping(
            value = "/api/resources/{id}",
            consumes = "application/json",
            produces = "application/json"
    )
    public ResponseEntity<?> updateResource(
            @PathVariable int id,
            @Valid @RequestBody UpdateResourceRequest request
    ) {
        for (int i = 0; i < resources.size(); i++) {
            ResourceItem current = resources.get(i);

            if (current.id() == id) {
                ResourceItem updated = new ResourceItem(
                        id,
                        request.name(),
                        request.owner()
                );

                resources.set(i, updated);

                return ResponseEntity.ok(updated);
            }
        }

        return ResponseEntity.status(404).body(
                ApiError.of(
                        "not_found",
                        "Resource with id " + id + " was not found"
                )
        );
    }

    @Operation(summary = "Delete a resource")
    @ApiResponse(responseCode = "204", description = "Resource deleted")
    @ApiResponse(responseCode = "404", description = "Resource not found")
    @ApiResponse(responseCode = "406", description = "Only application/json is supported")
    @DeleteMapping(value = "/api/resources/{id}", produces = "application/json")
    public ResponseEntity<?> deleteResource(@PathVariable int id) {
        boolean removed = resources.removeIf(resource -> resource.id() == id);

        if (removed) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.status(404).body(
                ApiError.of(
                        "not_found",
                        "Resource with id " + id + " was not found"
                )
        );
    }
}