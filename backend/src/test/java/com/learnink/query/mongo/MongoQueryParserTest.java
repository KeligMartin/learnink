package com.learnink.query.mongo;

import com.learnink.query.QueryRejectedException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MongoQueryParserTest {

    private final MongoQueryParser parser = new MongoQueryParser();

    @Test
    void readsFindWithProjectionSortAndLimit() {
        MongoQuery query = parser.parse("""
                // chambres partagées
                db.chambre.find({ capacite: { $gte: 2 } }, { _id: 0, numero: 1 })
                  .sort({ numero: 1 }).limit(5);
                """);
        assertThat(query).isInstanceOfSatisfying(MongoQuery.Find.class, find -> {
            assertThat(find.collection()).isEqualTo("chambre");
            assertThat(find.filter().getDocument("capacite").getInt32("$gte").getValue()).isEqualTo(2);
            assertThat(find.projection().getInt32("_id").getValue()).isZero();
            assertThat(find.sort().getInt32("numero").getValue()).isEqualTo(1);
            assertThat(find.limit()).isEqualTo(5);
        });
    }

    @Test
    void readsRegularExpressionsDatesAndCommasInsideStrings() {
        MongoQuery query = parser.parse("db.evenement.find({ nom: /pizza, (soirée)/i, date_evenement: { $gte: ISODate(\"2026-01-01\") }, lieu: 'a, b' })");
        assertThat(query).isInstanceOfSatisfying(MongoQuery.Find.class, find -> {
            assertThat(find.filter().get("nom").isRegularExpression()).isTrue();
            assertThat(find.filter().getDocument("date_evenement").get("$gte").isDateTime()).isTrue();
            assertThat(find.filter().getString("lieu").getValue()).isEqualTo("a, b");
        });
    }

    @Test
    void readsAggregateCountDistinctAndGetCollection() {
        assertThat(parser.parse("db.plat.aggregate([{ $group: { _id: \"$type\", n: { $sum: 1 } } }])"))
                .isInstanceOfSatisfying(MongoQuery.Aggregate.class, a -> assertThat(a.pipeline()).hasSize(1));
        assertThat(parser.parse("db.reservation.countDocuments({ annulee: true })")).isInstanceOf(MongoQuery.Count.class);
        assertThat(parser.parse("db.reservation.find({ annulee: true }).count()")).isInstanceOf(MongoQuery.Count.class);
        assertThat(parser.parse("db.getCollection('plat').distinct('type')"))
                .isInstanceOfSatisfying(MongoQuery.Distinct.class, d -> assertThat(d.collection()).isEqualTo("plat"));
    }

    @Test
    void rejectsWritesAndServerSideCode() {
        assertThatThrownBy(() -> parser.parse("db.plat.deleteMany({})")).isInstanceOf(QueryRejectedException.class)
                .hasMessageContaining("lectures");
        assertThatThrownBy(() -> parser.parse("db.plat.aggregate([{ $out: \"copie\" }])"))
                .hasMessageContaining("$out");
        assertThatThrownBy(() -> parser.parse("db.plat.find({ $where: \"this.prix > 1\" })"))
                .hasMessageContaining("$where");
        assertThatThrownBy(() -> parser.parse("db.plat.find({}); db.plat.drop()"))
                .isInstanceOf(QueryRejectedException.class);
    }

    @Test
    void explainsSyntaxErrors() {
        assertThatThrownBy(() -> parser.parse("SELECT * FROM plat")).hasMessageContaining("db.");
        assertThatThrownBy(() -> parser.parse("db.plat.find({ nom: \"x\" ")).isInstanceOf(QueryRejectedException.class);
        assertThatThrownBy(() -> parser.parse("db.plat.find({ nom \"x\" })")).hasMessageContaining("argument 1 de find");
        assertThatThrownBy(() -> parser.parse("db.plat")).hasMessageContaining("find");
    }
}
