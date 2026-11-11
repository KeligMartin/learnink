package com.learnink.query.mongo;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Clients MongoDB réutilisés d'une requête à l'autre (un par chaîne de connexion).
 * Chaque base d'étudiant n'utilise que quelques connexions ; les clients inactifs sont fermés au-delà de
 * {@link MongoProperties#maxClients()} ou après {@link #IDLE_MILLIS} d'inactivité.
 */
@Component
public class MongoClientPool implements DisposableBean {

    static final long IDLE_MILLIS = TimeUnit.MINUTES.toMillis(20);
    static final int PERSONAL_POOL_SIZE = 3;

    private record Entry(MongoClient client, long lastUsed) {
    }

    private final Map<String, Entry> clients = new ConcurrentHashMap<>();
    private final MongoProperties properties;

    public MongoClientPool(MongoProperties properties) {
        this.properties = properties;
    }

    public MongoClient get(String uri, boolean shared) {
        evictIdle();
        Entry entry = clients.compute(uri, (key, existing) -> new Entry(
                existing == null ? create(uri, shared ? properties.sharedPoolSize() : PERSONAL_POOL_SIZE) : existing.client(),
                System.currentTimeMillis()));
        return entry.client();
    }

    /** Client à usage unique (test d'une nouvelle chaîne de connexion), à fermer par l'appelant. */
    public MongoClient createTemporary(String uri) {
        return create(uri, 1);
    }

    public void close(String uri) {
        Entry entry = clients.remove(uri);
        if (entry != null) {
            entry.client().close();
        }
    }

    private MongoClient create(String uri, int poolSize) {
        int timeout = properties.timeoutSeconds();
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(uri))
                .applicationName("learnink")
                .applyToClusterSettings(cluster -> cluster.serverSelectionTimeout(timeout, TimeUnit.SECONDS))
                .applyToSocketSettings(socket -> socket.connectTimeout(timeout, TimeUnit.SECONDS)
                        .readTimeout(timeout * 3, TimeUnit.SECONDS))
                .applyToConnectionPoolSettings(pool -> pool.maxSize(poolSize).minSize(0)
                        .maxConnectionIdleTime(2, TimeUnit.MINUTES))
                .build();
        return MongoClients.create(settings);
    }

    private void evictIdle() {
        long limit = System.currentTimeMillis() - IDLE_MILLIS;
        clients.entrySet().removeIf(e -> {
            if (e.getValue().lastUsed() < limit) {
                e.getValue().client().close();
                return true;
            }
            return false;
        });
        while (clients.size() >= properties.maxClients()) {
            clients.entrySet().stream()
                    .min(Comparator.comparingLong(e -> e.getValue().lastUsed()))
                    .ifPresent(oldest -> close(oldest.getKey()));
        }
    }

    @Override
    public void destroy() {
        clients.values().forEach(entry -> entry.client().close());
        clients.clear();
    }
}
