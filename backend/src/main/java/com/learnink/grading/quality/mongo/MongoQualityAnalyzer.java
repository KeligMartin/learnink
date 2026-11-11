package com.learnink.grading.quality.mongo;

import com.learnink.grading.quality.QualityIssue;
import com.learnink.grading.quality.QueryQualityAnalyzer;
import com.learnink.query.QueryEngine;
import com.learnink.query.QueryRejectedException;
import com.learnink.query.mongo.MongoQuery;
import com.learnink.query.mongo.MongoQueryParser;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Bonnes pratiques MongoDB vérifiées sur une requête correcte :
 * <ul>
 *     <li>projection : ne renvoyer que les champs utiles (l'équivalent du SELECT * en SQL) ;</li>
 *     <li>filtrer tôt : un $match placé après un $lookup ou un $unwind alors qu'il ne porte que sur les champs
 *     d'origine oblige MongoDB à traiter tous les documents avant de les écarter.</li>
 * </ul>
 */
@Component
public class MongoQualityAnalyzer implements QueryQualityAnalyzer {

    static final double PENALTY = 1;
    private static final Set<String> RESHAPING_STAGES = Set.of("$project", "$group", "$count", "$replaceRoot",
            "$replaceWith", "$unset", "$bucket", "$bucketAuto", "$sortByCount", "$facet");

    private final MongoQueryParser parser;

    public MongoQualityAnalyzer(MongoQueryParser parser) {
        this.parser = parser;
    }

    @Override
    public QueryEngine engine() {
        return QueryEngine.MONGODB;
    }

    @Override
    public List<QualityIssue> analyze(String studentQuery, String referenceQuery) {
        MongoQuery query;
        try {
            query = parser.parse(studentQuery);
        } catch (QueryRejectedException e) {
            return List.of();
        }
        List<QualityIssue> issues = new ArrayList<>();
        switch (query) {
            case MongoQuery.Find find -> {
                if (find.projection() == null || find.projection().isEmpty()) {
                    issues.add(new QualityIssue("Ajoutez une projection pour ne renvoyer que les champs utiles "
                            + "(2e argument de find, ex. { nom: 1, _id: 0 }) : c'est l'équivalent d'éviter SELECT *.", PENALTY));
                }
            }
            case MongoQuery.Aggregate aggregate -> {
                List<BsonDocument> pipeline = aggregate.pipeline();
                if (pipeline.stream().noneMatch(stage -> RESHAPING_STAGES.contains(stage.getFirstKey()))) {
                    issues.add(new QualityIssue("Terminez le pipeline par un $project pour ne renvoyer que les champs "
                            + "utiles.", PENALTY));
                }
                lateMatch(pipeline).ifPresent(issues::add);
            }
            default -> {
            }
        }
        return issues;
    }

    /** Un $match après $lookup / $unwind qui ne porte sur aucun des champs ajoutés par ces étapes. */
    private static Optional<QualityIssue> lateMatch(List<BsonDocument> pipeline) {
        List<String> added = new ArrayList<>();
        boolean expensiveBefore = false;
        for (BsonDocument stage : pipeline) {
            String name = stage.getFirstKey();
            BsonValue body = stage.get(name);
            switch (name) {
                case "$lookup" -> {
                    expensiveBefore = true;
                    if (body.isDocument() && body.asDocument().containsKey("as")) {
                        added.add(body.asDocument().getString("as").getValue());
                    }
                }
                case "$unwind" -> {
                    expensiveBefore = true;
                    String path = body.isString() ? body.asString().getValue()
                            : body.isDocument() && body.asDocument().containsKey("path")
                            ? body.asDocument().getString("path").getValue() : "";
                    added.add(path.startsWith("$") ? path.substring(1) : path);
                }
                case "$match" -> {
                    if (expensiveBefore && body.isDocument() && !referencesAny(body, added)) {
                        return Optional.of(new QualityIssue("Placez ce $match avant le $lookup / $unwind : "
                                + "filtrer d'abord évite de traiter des documents qui seront écartés.", PENALTY));
                    }
                }
                default -> {
                    if (RESHAPING_STAGES.contains(name) || "$addFields".equals(name) || "$set".equals(name)) {
                        return Optional.empty();
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** Vrai si le filtre mentionne un des champs ajoutés (en clé ou en chemin "$champ"). */
    private static boolean referencesAny(BsonValue value, List<String> fields) {
        if (value.isDocument()) {
            for (var entry : value.asDocument().entrySet()) {
                if (mentions(entry.getKey(), fields) || referencesAny(entry.getValue(), fields)) {
                    return true;
                }
            }
        } else if (value.isArray()) {
            for (BsonValue element : value.asArray()) {
                if (referencesAny(element, fields)) {
                    return true;
                }
            }
        } else if (value.isString()) {
            String text = value.asString().getValue();
            return text.startsWith("$") && mentions(text.substring(1), fields);
        }
        return false;
    }

    private static boolean mentions(String path, List<String> fields) {
        return fields.stream().anyMatch(field -> !field.isEmpty() && (path.equals(field) || path.startsWith(field + ".")));
    }
}
