package com.learnink.query.mongo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Convertit des documents MongoDB en résultat tabulaire (pour la comparaison avec la solution et les graphiques) :
 * les sous-documents sont aplatis en colonnes « parent.champ », les tableaux restent en JSON.
 * Les dates sont écrites comme en SQL : 2026-03-14 (minuit) ou 2026-03-14 12:30.
 */
final class DocumentConverter {

    private static final ObjectMapper JSON = new ObjectMapper().disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MINUTES = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private DocumentConverter() {
    }

    record Table(List<String> columns, List<List<Object>> rows, List<String> documents) {
    }

    static Table toTable(List<Document> documents) {
        List<Map<String, Object>> flattened = new ArrayList<>(documents.size());
        Set<String> columns = new LinkedHashSet<>();
        List<String> json = new ArrayList<>(documents.size());
        for (Document document : documents) {
            Map<String, Object> flat = new LinkedHashMap<>();
            flatten("", document, flat);
            columns.addAll(flat.keySet());
            flattened.add(flat);
            json.add(toJson(plain(document)));
        }
        List<List<Object>> rows = new ArrayList<>(flattened.size());
        for (Map<String, Object> flat : flattened) {
            List<Object> row = new ArrayList<>(columns.size());
            columns.forEach(column -> row.add(flat.get(column)));
            rows.add(row);
        }
        return new Table(List.copyOf(columns), rows, json);
    }

    private static void flatten(String prefix, Map<?, ?> document, Map<String, Object> out) {
        if (document.isEmpty() && !prefix.isEmpty()) {
            out.put(prefix.substring(0, prefix.length() - 1), "{}");
            return;
        }
        for (Map.Entry<?, ?> entry : document.entrySet()) {
            String key = prefix + entry.getKey();
            if (entry.getValue() instanceof Map<?, ?> nested) {
                flatten(key + ".", nested, out);
            } else {
                out.put(key, scalar(entry.getValue()));
            }
        }
    }

    /** Valeur d'une cellule : nombre, texte, booléen, date formatée ; un tableau devient son texte JSON. */
    static Object scalar(Object value) {
        Object plain = plain(value);
        if (plain instanceof List<?> || plain instanceof Map<?, ?>) {
            return toJson(plain);
        }
        return plain;
    }

    /** Convertit récursivement une valeur BSON en types JSON simples. */
    static Object plain(Object value) {
        return switch (value) {
            case null -> null;
            case Document document -> {
                Map<String, Object> map = new LinkedHashMap<>();
                document.forEach((k, v) -> map.put(k, plain(v)));
                yield map;
            }
            case Map<?, ?> map -> {
                Map<String, Object> copy = new LinkedHashMap<>();
                map.forEach((k, v) -> copy.put(String.valueOf(k), plain(v)));
                yield copy;
            }
            case Collection<?> list -> list.stream().map(DocumentConverter::plain).toList();
            case Date date -> formatDate(date.toInstant());
            case ObjectId id -> id.toHexString();
            case Decimal128 decimal -> decimal.bigDecimalValue();
            case Number number -> number;
            case Boolean bool -> bool;
            case String text -> text;
            case BsonValue bson -> bson.toString();
            default -> value.toString();
        };
    }

    static String formatDate(Instant instant) {
        LocalDateTime time = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        if (time.toLocalTime().toSecondOfDay() == 0 && time.getNano() == 0) {
            return DAY.format(time);
        }
        return (time.getSecond() == 0 ? MINUTES : SECONDS).format(time);
    }

    static String toJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }
}
