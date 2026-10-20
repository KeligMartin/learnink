package com.learnink.query.sql;

import com.learnink.query.QueryEngine;
import com.learnink.query.QueryExecutionException;
import com.learnink.query.QueryExecutionStrategy;
import com.learnink.query.QueryResult;
import com.learnink.query.QueryTarget;
import com.learnink.query.SchemaDescription;
import org.springframework.stereotype.Component;

import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Exécution des requêtes SQL sur la base bac à sable de l'utilisateur (MySQL en production, H2 en développement).
 */
@Component
public class SqlQueryExecutionStrategy implements QueryExecutionStrategy {

    private final SandboxDataSources dataSources;
    private final SelectOnlyQueryValidator validator;
    private final SandboxProperties properties;
    private final Map<Integer, SchemaDescription> schemaCache = new ConcurrentHashMap<>();

    public SqlQueryExecutionStrategy(SandboxDataSources dataSources, SelectOnlyQueryValidator validator,
                                     SandboxProperties properties) {
        this.dataSources = dataSources;
        this.validator = validator;
        this.properties = properties;
    }

    @Override
    public QueryEngine engine() {
        return QueryEngine.SQL;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public QueryResult execute(String query, QueryTarget target) {
        String sql = validator.validate(query);
        try (Connection connection = dataSources.get(target.datasource()).getConnection();
             Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(properties.queryTimeoutSeconds());
            statement.setMaxRows(properties.maxComparedRows() + 1);
            long start = System.nanoTime();
            try (ResultSet resultSet = statement.executeQuery(sql)) {
                return read(resultSet, start);
            }
        } catch (SQLTimeoutException e) {
            throw new QueryExecutionException(
                    "La requête a dépassé le temps maximal autorisé (" + properties.queryTimeoutSeconds() + " s).", e);
        } catch (SQLException e) {
            throw new QueryExecutionException(cleanMessage(e), e);
        }
    }

    private QueryResult read(ResultSet resultSet, long start) throws SQLException {
        ResultSetMetaData metaData = resultSet.getMetaData();
        int columnCount = metaData.getColumnCount();
        List<String> columns = new ArrayList<>(columnCount);
        for (int i = 1; i <= columnCount; i++) {
            columns.add(metaData.getColumnLabel(i));
        }
        List<List<Object>> rows = new ArrayList<>();
        boolean truncated = false;
        while (resultSet.next()) {
            if (rows.size() == properties.maxComparedRows()) {
                truncated = true;
                break;
            }
            List<Object> row = new ArrayList<>(columnCount);
            for (int i = 1; i <= columnCount; i++) {
                row.add(toJsonValue(resultSet.getObject(i)));
            }
            rows.add(row);
        }
        return new QueryResult(columns, rows, truncated, System.nanoTime() - start);
    }

    private static Object toJsonValue(Object value) throws SQLException {
        if (value == null || value instanceof Number || value instanceof Boolean || value instanceof String) {
            return value;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime().toString().replace('T', ' ');
        }
        if (value instanceof TemporalAccessor || value instanceof java.util.Date) {
            return value.toString().replace('T', ' ');
        }
        if (value instanceof Clob clob) {
            return clob.getSubString(1, (int) Math.min(clob.length(), 10_000));
        }
        if (value instanceof byte[]) {
            return "<binaire>";
        }
        return value.toString();
    }

    /** Ne conserve que la première ligne du message (sans le rappel de la requête ajouté par certains pilotes). */
    private static String cleanMessage(SQLException e) {
        String message = e.getMessage() == null ? "Erreur SQL" : e.getMessage();
        String firstLine = message.lines().findFirst().orElse(message);
        int statementIndex = firstLine.indexOf("; SQL statement:");
        return statementIndex > 0 ? firstLine.substring(0, statementIndex) : firstLine;
    }

    @Override
    public SchemaDescription describeSchema(QueryTarget target) {
        SandboxDataSource dataSource = dataSources.get(target.datasource());
        return schemaCache.computeIfAbsent(target.datasource(), number -> loadSchema(dataSource));
    }

    private SchemaDescription loadSchema(SandboxDataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            String schemaName = connection.getSchema();
            List<SchemaDescription.Table> tables = new ArrayList<>();
            try (ResultSet tableRows = metaData.getTables(catalog, schemaName, "%", null)) {
                while (tableRows.next()) {
                    String type = tableRows.getString("TABLE_TYPE");
                    if (type != null && type.toUpperCase().contains("SYSTEM")) {
                        continue;
                    }
                    String tableName = tableRows.getString("TABLE_NAME");
                    tables.add(new SchemaDescription.Table(tableName,
                            loadColumns(metaData, catalog, schemaName, tableName)));
                }
            }
            tables.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
            return new SchemaDescription(List.copyOf(tables));
        } catch (SQLException e) {
            throw new QueryExecutionException("Impossible de lire le schéma de la base : " + e.getMessage(), e);
        }
    }

    private static List<SchemaDescription.Column> loadColumns(DatabaseMetaData metaData, String catalog,
                                                              String schemaName, String table) throws SQLException {
        Set<String> primaryKey = new HashSet<>();
        try (ResultSet keyRows = metaData.getPrimaryKeys(catalog, schemaName, table)) {
            while (keyRows.next()) {
                primaryKey.add(keyRows.getString("COLUMN_NAME"));
            }
        }
        Map<String, String> references = new HashMap<>();
        try (ResultSet foreignKeyRows = metaData.getImportedKeys(catalog, schemaName, table)) {
            while (foreignKeyRows.next()) {
                references.put(foreignKeyRows.getString("FKCOLUMN_NAME"),
                        foreignKeyRows.getString("PKTABLE_NAME") + "." + foreignKeyRows.getString("PKCOLUMN_NAME"));
            }
        }
        List<SchemaDescription.Column> columns = new ArrayList<>();
        try (ResultSet columnRows = metaData.getColumns(catalog, schemaName, table, "%")) {
            while (columnRows.next()) {
                String name = columnRows.getString("COLUMN_NAME");
                columns.add(new SchemaDescription.Column(name, columnRows.getString("TYPE_NAME"),
                        primaryKey.contains(name), references.get(name)));
            }
        }
        return columns;
    }
}
