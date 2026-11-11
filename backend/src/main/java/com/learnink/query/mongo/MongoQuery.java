package com.learnink.query.mongo;

import org.bson.BsonDocument;

import java.util.List;

/**
 * Requête MongoDB en lecture seule, issue de l'analyse du texte saisi par l'étudiant (syntaxe mongosh).
 */
public sealed interface MongoQuery {

    String collection();

    /** {@code db.c.find(filtre, projection).sort(...).skip(n).limit(n)} */
    record Find(String collection, BsonDocument filter, BsonDocument projection, BsonDocument sort, Integer skip,
                Integer limit) implements MongoQuery {
    }

    /** {@code db.c.countDocuments(filtre)} ou {@code db.c.find(filtre).count()} */
    record Count(String collection, BsonDocument filter) implements MongoQuery {
    }

    /** {@code db.c.aggregate([étapes])} */
    record Aggregate(String collection, List<BsonDocument> pipeline) implements MongoQuery {
    }

    /** {@code db.c.distinct("champ", filtre)} */
    record Distinct(String collection, String field, BsonDocument filter) implements MongoQuery {
    }
}
