package com.learnink.query;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Sélectionne la stratégie d'exécution adaptée au moteur demandé.
 * Ajouter un moteur revient à déclarer un nouveau bean {@link QueryExecutionStrategy}.
 */
@Component
public class QueryExecutionStrategyRegistry {

    private final Map<QueryEngine, QueryExecutionStrategy> strategies = new EnumMap<>(QueryEngine.class);

    public QueryExecutionStrategyRegistry(List<QueryExecutionStrategy> strategies) {
        strategies.forEach(strategy -> {
            if (this.strategies.putIfAbsent(strategy.engine(), strategy) != null) {
                throw new IllegalStateException("Plusieurs stratégies déclarées pour le moteur " + strategy.engine());
            }
        });
    }

    public QueryExecutionStrategy get(QueryEngine engine) {
        QueryExecutionStrategy strategy = strategies.get(engine);
        if (strategy == null || !strategy.isAvailable()) {
            throw new EngineUnavailableException("Le moteur " + engine.label() + " n'est pas encore disponible.");
        }
        return strategy;
    }

    public Collection<QueryExecutionStrategy> all() {
        return strategies.values();
    }
}
