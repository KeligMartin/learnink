package com.learnink.query.mongo;

import com.learnink.query.QueryRejectedException;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.json.JsonParseException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Analyse une requête écrite en syntaxe mongosh ({@code db.collection.find(...)}) sans jamais l'exécuter comme du code :
 * les arguments sont lus comme du JSON étendu (clés sans guillemets, ISODate(...), expressions régulières /.../i).
 * Seules les lectures sont acceptées : find, findOne, countDocuments, aggregate et distinct.
 */
@Component
public class MongoQueryParser {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /** Opérateurs qui exécutent du JavaScript sur le serveur ou écrivent dans la base, et étapes d'administration. */
    static final Set<String> FORBIDDEN_OPERATORS = Set.of("$where", "$function", "$accumulator", "$out", "$merge",
            "$currentOp", "$listSessions", "$listLocalSessions", "$planCacheStats", "$changeStream", "$collStats",
            "$indexStats", "$shardedDataDistribution", "$listSearchIndexes");

    private static final Set<String> WRITE_METHODS = Set.of("insertOne", "insertMany", "insert", "updateOne",
            "updateMany", "update", "replaceOne", "deleteOne", "deleteMany", "remove", "drop", "findOneAndUpdate",
            "findOneAndReplace", "findOneAndDelete", "bulkWrite", "createIndex", "createIndexes", "dropIndex",
            "dropIndexes", "renameCollection", "save", "mapReduce");

    private static final String SUPPORTED = "find, findOne, countDocuments, aggregate ou distinct";

    record Call(String name, List<String> arguments) {
    }

    public MongoQuery parse(String text) {
        String query = stripComments(text == null ? "" : text).strip();
        while (query.endsWith(";")) {
            query = query.substring(0, query.length() - 1).strip();
        }
        if (query.isEmpty()) {
            throw new QueryRejectedException("La requête est vide.");
        }
        Cursor cursor = new Cursor(query);
        if (!cursor.consumeWord("db") || !cursor.consume('.')) {
            throw new QueryRejectedException("Une requête MongoDB commence par db.<collection>, par exemple : "
                    + "db.batiment.find({}).");
        }
        String collection = readCollection(cursor);
        List<Call> calls = new ArrayList<>();
        while (!cursor.atEnd()) {
            if (!cursor.consume('.')) {
                throw new QueryRejectedException(cursor.atChar(';')
                        ? "Une seule requête à la fois : retirez le point-virgule au milieu de la requête."
                        : "Syntaxe inattendue près de « " + cursor.rest(20) + " ».");
            }
            String name = cursor.identifier();
            if (name == null) {
                throw new QueryRejectedException("Nom de méthode attendu après le point, près de « " + cursor.rest(20) + " ».");
            }
            if (!cursor.atChar('(')) {
                throw new QueryRejectedException("Parenthèses attendues après " + name + " : " + name + "(...).");
            }
            calls.add(new Call(name, splitArguments(cursor.parenthesized())));
        }
        if (calls.isEmpty()) {
            throw new QueryRejectedException("Indiquez l'opération à réaliser : db." + collection + ".find({}), par exemple.");
        }
        MongoQuery parsed = build(collection, calls);
        checkForbidden(parsed);
        return parsed;
    }

    // ---------------------------------------------------------------------------------------------------------------

    private static String readCollection(Cursor cursor) {
        String name = cursor.identifier();
        if ("getCollection".equals(name) && cursor.atChar('(')) {
            List<String> args = splitArguments(cursor.parenthesized());
            if (args.size() != 1) {
                throw new QueryRejectedException("db.getCollection attend le nom de la collection entre guillemets.");
            }
            name = readString(args.getFirst(), "getCollection");
            if (!cursor.atChar('.')) {
                throw new QueryRejectedException("Indiquez l'opération après db.getCollection(\"" + name + "\").");
            }
            return name;
        }
        if (name == null) {
            throw new QueryRejectedException("Nom de collection attendu après « db. ».");
        }
        if (!cursor.atChar('.')) {
            throw new QueryRejectedException("Indiquez l'opération à réaliser : db." + name + ".find({}), par exemple.");
        }
        return name;
    }

    private static MongoQuery build(String collection, List<Call> calls) {
        Call first = calls.getFirst();
        List<Call> chain = calls.subList(1, calls.size());
        rejectWrite(first.name());
        return switch (first.name()) {
            case "find", "findOne" -> buildFind(collection, first, chain);
            case "countDocuments", "count" -> {
                requireNoChain(first.name(), chain);
                requireAtMost(first, 1);
                yield new MongoQuery.Count(collection, documentArgument(first, 0));
            }
            case "aggregate" -> {
                requireNoChain("aggregate", chain);
                if (first.arguments().isEmpty()) {
                    throw new QueryRejectedException("aggregate attend un tableau d'étapes : db." + collection
                            + ".aggregate([ { $match: {...} }, ... ]).");
                }
                requireAtMost(first, 1);
                yield new MongoQuery.Aggregate(collection, pipeline(first.arguments().getFirst()));
            }
            case "distinct" -> {
                requireNoChain("distinct", chain);
                if (first.arguments().isEmpty()) {
                    throw new QueryRejectedException("distinct attend le nom du champ : db." + collection
                            + ".distinct(\"champ\").");
                }
                requireAtMost(first, 2);
                yield new MongoQuery.Distinct(collection, readString(first.arguments().getFirst(), "distinct"),
                        documentArgument(first, 1));
            }
            default -> throw new QueryRejectedException("Opération « " + first.name() + " » non prise en charge : utilisez "
                    + SUPPORTED + ".");
        };
    }

    private static MongoQuery buildFind(String collection, Call find, List<Call> chain) {
        requireAtMost(find, 2);
        BsonDocument filter = documentArgument(find, 0);
        BsonDocument projection = find.arguments().size() > 1 ? documentArgument(find, 1) : null;
        BsonDocument sort = null;
        Integer skip = null;
        Integer limit = "findOne".equals(find.name()) ? 1 : null;
        for (Call call : chain) {
            rejectWrite(call.name());
            if ("findOne".equals(find.name())) {
                throw new QueryRejectedException("findOne renvoie un seul document : utilisez find(...) pour trier ou limiter.");
            }
            switch (call.name()) {
                case "sort" -> sort = requiredDocument(call);
                case "limit" -> limit = integerArgument(call);
                case "skip" -> skip = integerArgument(call);
                case "projection" -> projection = requiredDocument(call);
                case "count" -> {
                    if (call != chain.getLast()) {
                        throw new QueryRejectedException("count() termine la requête : placez-le en dernier.");
                    }
                    return new MongoQuery.Count(collection, filter);
                }
                case "pretty", "toArray" -> requireAtMost(call, 0);
                default -> throw new QueryRejectedException("Méthode « " + call.name() + " » non prise en charge après find : "
                        + "utilisez sort, skip, limit ou count.");
            }
        }
        return new MongoQuery.Find(collection, filter, projection, sort, skip, limit);
    }

    private static void rejectWrite(String method) {
        if (WRITE_METHODS.contains(method)) {
            throw new QueryRejectedException("Seules les lectures sont autorisées (" + SUPPORTED + ") : « " + method
                    + " » modifierait la base.");
        }
    }

    private static void requireNoChain(String method, List<Call> chain) {
        for (Call call : chain) {
            rejectWrite(call.name());
            if (!"toArray".equals(call.name()) && !"pretty".equals(call.name())) {
                throw new QueryRejectedException("« " + call.name() + " » ne peut pas suivre " + method
                        + (method.equals("aggregate") ? " : utilisez les étapes $sort, $skip et $limit du pipeline." : "."));
            }
        }
    }

    private static void requireAtMost(Call call, int count) {
        if (call.arguments().size() > count) {
            throw new QueryRejectedException(call.name() + " accepte au plus " + count + " argument(s), "
                    + call.arguments().size() + " fourni(s).");
        }
    }

    private static BsonDocument documentArgument(Call call, int index) {
        if (call.arguments().size() <= index) {
            return new BsonDocument();
        }
        return parseDocument(call.arguments().get(index), call.name(), index + 1);
    }

    private static BsonDocument requiredDocument(Call call) {
        if (call.arguments().size() != 1) {
            throw new QueryRejectedException(call.name() + " attend un document, par exemple " + call.name() + "({ nom: 1 }).");
        }
        return parseDocument(call.arguments().getFirst(), call.name(), 1);
    }

    private static Integer integerArgument(Call call) {
        if (call.arguments().size() != 1) {
            throw new QueryRejectedException(call.name() + " attend un nombre entier, par exemple " + call.name() + "(5).");
        }
        try {
            int value = Integer.parseInt(call.arguments().getFirst().strip());
            if (value < 0) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException e) {
            throw new QueryRejectedException(call.name() + " attend un nombre entier positif.");
        }
    }

    static BsonDocument parseDocument(String text, String method, int position) {
        String trimmed = text.strip();
        if (!trimmed.startsWith("{")) {
            throw new QueryRejectedException("L'argument " + position + " de " + method + " doit être un document entre "
                    + "accolades { ... }.");
        }
        try {
            return BsonDocument.parse(trimmed);
        } catch (JsonParseException | IllegalArgumentException e) {
            throw new QueryRejectedException("Syntaxe invalide dans l'argument " + position + " de " + method + " : "
                    + explain(e));
        }
    }

    private static List<BsonDocument> pipeline(String text) {
        String trimmed = text.strip();
        if (!trimmed.startsWith("[")) {
            throw new QueryRejectedException("aggregate attend un tableau d'étapes entre crochets : aggregate([ ... ]).");
        }
        BsonArray stages;
        try {
            stages = BsonArray.parse(trimmed);
        } catch (JsonParseException | IllegalArgumentException e) {
            throw new QueryRejectedException("Syntaxe invalide dans le pipeline : " + explain(e));
        }
        List<BsonDocument> pipeline = new ArrayList<>();
        for (int i = 0; i < stages.size(); i++) {
            BsonValue stage = stages.get(i);
            if (!stage.isDocument() || stage.asDocument().size() != 1) {
                throw new QueryRejectedException("L'étape " + (i + 1) + " du pipeline doit être un document à une seule clé, "
                        + "par exemple { $match: { ... } }.");
            }
            String name = stage.asDocument().getFirstKey();
            if (!name.startsWith("$")) {
                throw new QueryRejectedException("L'étape " + (i + 1) + " du pipeline doit commencer par un opérateur "
                        + "($match, $group, $sort...), et non « " + name + " ».");
            }
            pipeline.add(stage.asDocument());
        }
        return pipeline;
    }

    private static String readString(String text, String method) {
        try {
            BsonValue value = BsonDocument.parse("{v: " + text.strip() + "}").get("v");
            if (value != null && value.isString()) {
                return value.asString().getValue();
            }
        } catch (JsonParseException | IllegalArgumentException e) {
            // message ci-dessous
        }
        throw new QueryRejectedException(method + " attend un texte entre guillemets.");
    }

    private static String explain(RuntimeException e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        if (message.contains("JSON reader was expecting a name")) {
            return "un nom de champ est attendu (vérifiez les virgules et les accolades).";
        }
        if (message.contains("JSON reader was expecting ':'")) {
            return "« : » attendu entre un nom de champ et sa valeur.";
        }
        if (message.contains("Invalid JSON input") || message.contains("Unexpected")) {
            return "caractère inattendu (vérifiez les guillemets, les virgules et les accolades).";
        }
        return message;
    }

    // --------------------------------------------------------------------------------------------- opérateurs interdits

    private static void checkForbidden(MongoQuery query) {
        switch (query) {
            case MongoQuery.Find find -> {
                check(find.filter());
                check(find.projection());
                check(find.sort());
            }
            case MongoQuery.Count count -> check(count.filter());
            case MongoQuery.Distinct distinct -> check(distinct.filter());
            case MongoQuery.Aggregate aggregate -> aggregate.pipeline().forEach(MongoQueryParser::check);
        }
    }

    private static void check(BsonValue value) {
        if (value == null) {
            return;
        }
        if (value.isDocument()) {
            for (Map.Entry<String, BsonValue> entry : value.asDocument().entrySet()) {
                if (FORBIDDEN_OPERATORS.contains(entry.getKey())) {
                    throw new QueryRejectedException("L'opérateur " + entry.getKey() + " n'est pas autorisé : "
                            + (Set.of("$out", "$merge").contains(entry.getKey())
                            ? "il écrirait dans la base." : "il exécute du code ou administre le serveur."));
                }
                check(entry.getValue());
            }
        } else if (value.isArray()) {
            value.asArray().forEach(MongoQueryParser::check);
        }
    }

    // ------------------------------------------------------------------------------------------------- découpage texte

    /** Retire les commentaires // et /* *\/ situés hors des chaînes et des expressions régulières. */
    static String stripComments(String text) {
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '"' || c == '\'') {
                int end = endOfString(text, i);
                out.append(text, i, end);
                i = end;
            } else if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '/') {
                while (i < text.length() && text.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '*') {
                int end = text.indexOf("*/", i + 2);
                i = end < 0 ? text.length() : end + 2;
                out.append(' ');
            } else if (c == '/' && regexAllowed(out)) {
                int end = endOfRegex(text, i);
                out.append(text, i, end);
                i = end;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /** Une expression régulière peut commencer après « : [ , ( » ou en début de texte. */
    private static boolean regexAllowed(CharSequence before) {
        for (int j = before.length() - 1; j >= 0; j--) {
            char p = before.charAt(j);
            if (!Character.isWhitespace(p)) {
                return p == ':' || p == '[' || p == ',' || p == '(';
            }
        }
        return true;
    }

    private static int endOfString(String text, int start) {
        char quote = text.charAt(start);
        int i = start + 1;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == quote) {
                return i + 1;
            }
            i++;
        }
        throw new QueryRejectedException("Guillemet " + quote + " non refermé.");
    }

    private static int endOfRegex(String text, int start) {
        int i = start + 1;
        boolean inClass = false;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == '[') {
                inClass = true;
            } else if (c == ']') {
                inClass = false;
            } else if (c == '/' && !inClass) {
                i++;
                while (i < text.length() && Character.isLetter(text.charAt(i))) {
                    i++;
                }
                return i;
            } else if (c == '\n') {
                break;
            }
            i++;
        }
        throw new QueryRejectedException("Expression régulière /.../ non refermée.");
    }

    /** Découpe les arguments d'un appel au niveau des virgules qui ne sont dans aucun document, tableau ou chaîne. */
    static List<String> splitArguments(String inside) {
        List<String> arguments = new ArrayList<>();
        int depth = 0;
        int start = 0;
        StringBuilder seen = new StringBuilder();
        int i = 0;
        while (i < inside.length()) {
            char c = inside.charAt(i);
            if (c == '"' || c == '\'') {
                int end = endOfString(inside, i);
                seen.append(inside, i, end);
                i = end;
                continue;
            }
            if (c == '/' && regexAllowed(seen)) {
                int end = endOfRegex(inside, i);
                seen.append(inside, i, end);
                i = end;
                continue;
            }
            if (c == '{' || c == '[' || c == '(') {
                depth++;
            } else if (c == '}' || c == ']' || c == ')') {
                depth--;
                if (depth < 0) {
                    throw new QueryRejectedException("« " + c + " » sans ouverture correspondante.");
                }
            } else if (c == ',' && depth == 0) {
                arguments.add(inside.substring(start, i));
                start = i + 1;
            }
            seen.append(c);
            i++;
        }
        if (depth != 0) {
            throw new QueryRejectedException("Accolade, crochet ou parenthèse non refermé(e).");
        }
        String last = inside.substring(start);
        if (!last.isBlank() || !arguments.isEmpty()) {
            arguments.add(last);
        }
        List<String> trimmed = new ArrayList<>();
        for (String argument : arguments) {
            if (argument.isBlank()) {
                throw new QueryRejectedException("Argument vide : vérifiez les virgules entre les arguments.");
            }
            trimmed.add(argument.strip());
        }
        return trimmed;
    }

    /** Lecture séquentielle du texte de la requête. */
    private static final class Cursor {
        private final String text;
        private int pos;

        Cursor(String text) {
            this.text = text;
        }

        void skipSpaces() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }

        boolean atEnd() {
            skipSpaces();
            return pos >= text.length();
        }

        boolean atChar(char c) {
            skipSpaces();
            return pos < text.length() && text.charAt(pos) == c;
        }

        boolean consume(char c) {
            if (atChar(c)) {
                pos++;
                return true;
            }
            return false;
        }

        boolean consumeWord(String word) {
            skipSpaces();
            int save = pos;
            String found = identifier();
            if (word.equals(found)) {
                return true;
            }
            pos = save;
            return false;
        }

        String identifier() {
            skipSpaces();
            var matcher = IDENTIFIER.matcher(text).region(pos, text.length());
            if (!matcher.lookingAt()) {
                return null;
            }
            pos = matcher.end();
            return matcher.group();
        }

        /** Lit « ( ... ) » et renvoie le contenu, en respectant chaînes, expressions régulières et imbrications. */
        String parenthesized() {
            skipSpaces();
            int open = pos;
            int depth = 0;
            int i = open;
            StringBuilder seen = new StringBuilder();
            while (i < text.length()) {
                char c = text.charAt(i);
                if (c == '"' || c == '\'') {
                    int end = endOfString(text, i);
                    seen.append(text, i, end);
                    i = end;
                    continue;
                }
                if (c == '/' && i > open && regexAllowed(seen)) {
                    int end = endOfRegex(text, i);
                    seen.append(text, i, end);
                    i = end;
                    continue;
                }
                if (c == '(' || c == '{' || c == '[') {
                    depth++;
                } else if (c == ')' || c == '}' || c == ']') {
                    depth--;
                    if (depth == 0) {
                        if (c != ')') {
                            throw new QueryRejectedException("« " + c + " » inattendu : vérifiez les parenthèses.");
                        }
                        pos = i + 1;
                        return text.substring(open + 1, i);
                    }
                }
                seen.append(c);
                i++;
            }
            throw new QueryRejectedException("Parenthèse non refermée.");
        }

        String rest(int max) {
            skipSpaces();
            String rest = text.substring(pos);
            return rest.length() > max ? rest.substring(0, max) + "…" : rest;
        }
    }
}
