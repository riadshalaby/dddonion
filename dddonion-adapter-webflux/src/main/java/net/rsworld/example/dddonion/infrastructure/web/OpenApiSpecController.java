package net.rsworld.example.dddonion.infrastructure.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the authored OpenAPI contract at runtime so tooling (and Swagger UI) can load it. The same
 * {@code openapi/orders.yaml} that drives code generation is exposed verbatim, keeping a single source of truth.
 */
@RestController
public class OpenApiSpecController {

    @GetMapping(value = "/openapi.yaml", produces = "application/yaml")
    public Resource spec() {
        return new ClassPathResource("openapi/orders.yaml");
    }
}
