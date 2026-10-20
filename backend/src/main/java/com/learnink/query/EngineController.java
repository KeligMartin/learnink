package com.learnink.query;

import com.learnink.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/engines")
public class EngineController {

    public record EngineDto(QueryEngine type, String label, boolean available) {
    }

    private final QueryExecutionStrategyRegistry registry;

    public EngineController(QueryExecutionStrategyRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public List<EngineDto> engines() {
        return registry.all().stream()
                .map(strategy -> new EngineDto(strategy.engine(), strategy.engine().label(), strategy.isAvailable()))
                .sorted(Comparator.comparing(EngineDto::type))
                .toList();
    }

    /** Schéma de la base de la source de données de l'utilisateur connecté. */
    @GetMapping("/{engine}/schema")
    public SchemaDescription schema(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable QueryEngine engine) {
        return registry.get(engine).describeSchema(QueryTarget.of(user));
    }
}
