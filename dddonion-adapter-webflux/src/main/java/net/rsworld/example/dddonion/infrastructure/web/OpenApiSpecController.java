package net.rsworld.example.dddonion.infrastructure.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liefert den handgeschriebenen OpenAPI-Vertrag zur Laufzeit aus, damit Werkzeuge (und Swagger UI) ihn laden können. Es
 * ist dieselbe {@code openapi/api.yaml}, aus der der Code generiert wird, unverändert ausgeliefert; so bleibt es bei
 * einer Single Source of Truth.
 */
@RestController
public class OpenApiSpecController {

    @GetMapping(value = "/openapi.yaml", produces = "application/yaml")
    public Resource spec() {
        return new ClassPathResource("openapi/api.yaml");
    }
}
